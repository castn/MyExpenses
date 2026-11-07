package org.totschnig.myexpenses.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.createTransaction
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_UUID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.sync.json.TransactionChange

/**
 * End-to-end characterization tests: pin current behaviour so that future moves of the sync
 * code can be verified against this baseline.
 */
@RunWith(AndroidJUnit4::class)
class SyncAdapterEndToEndTest : BaseSyncTest() {

    private fun countTransactions(accountId: Long): Int = contentResolver.query(
        TransactionProvider.TRANSACTIONS_URI,
        arrayOf("count(*)"),
        "$KEY_ACCOUNTID = ?",
        arrayOf(accountId.toString()),
        null
    )!!.use { c ->
        c.moveToFirst()
        c.getInt(0)
    }

    private fun amountForUuid(accountId: Long, uuid: String): Long? = contentResolver.query(
        TransactionProvider.TRANSACTIONS_URI,
        arrayOf(KEY_AMOUNT, KEY_UUID),
        "$KEY_ACCOUNTID = ? AND $KEY_UUID = ?",
        arrayOf(accountId.toString(), uuid),
        null
    )?.use { c ->
        if (c.moveToFirst()) c.getLong(0) else null
    }

    private fun changeFileBody(
        uuid: String,
        amount: Long,
        timestamp: Long = TransactionChange.currentTimStamp
    ): String = Json.encodeToString(
        listOf(
            TransactionChange(
                type = TransactionChange.Type.created,
                uuid = uuid,
                timeStamp = timestamp,
                amount = amount,
                date = 1_000L
            )
        )
    )

    @Test
    fun upload_localTransactionAppearsInRemoteChangeFile() {
        addBackend()
        val accountId = syncedAccount()
        repository.createTransaction(
            Transaction(amount = -100, accountId = accountId, uuid = "tx-upload", date = 1_000L)
        )

        performSync()

        val remoteJson = remoteFile(accountId, "_1.json")
        assertThat(remoteJson.exists()).isTrue()

        val changes: List<TransactionChange> = Json.decodeFromString(remoteJson.readText())
        assertThat(changes).hasSize(1)
        assertThat(changes[0].uuid).isEqualTo("tx-upload")
        assertThat(changes[0].amount).isEqualTo(-100L)
    }

    @Test
    fun upload_sequenceNumbersAdvance() {
        addBackend()
        val accountId = syncedAccount()
        repository.createTransaction(
            Transaction(amount = -200, accountId = accountId, uuid = "tx-seq", date = 1_000L)
        )

        performSync()

        assertThat(lastSyncedLocal(accountId)).isEqualTo("1")
        assertThat(lastSyncedRemote(accountId)).isEqualTo("0_1")
    }

    @Test
    fun download_remoteChangeCreatesLocalTransaction() {
        addBackend()
        val accountId = syncedAccount()

        writeRemote(accountId, "_1.json", changeFileBody("tx-remote", -300L))

        performSync()

        val amount = amountForUuid(accountId, "tx-remote")
        assertThat(amount).isEqualTo(-300L)
    }

    @Test
    fun download_remoteSequenceTracked() {
        addBackend()
        val accountId = syncedAccount()
        writeRemote(accountId, "_1.json", changeFileBody("tx-dl-seq", -50L))

        performSync()

        assertThat(lastSyncedRemote(accountId)).isEqualTo("0_1")
        assertThat(lastSyncedLocal(accountId)).isNull()
    }

    @Test
    fun idempotent_secondSyncDoesNotDuplicate() {
        addBackend()
        val accountId = syncedAccount()
        writeRemote(accountId, "_1.json", changeFileBody("tx-idem", -400L))

        performSync()
        val countAfterFirst = countTransactions(accountId)

        performSync()
        val countAfterSecond = countTransactions(accountId)

        assertThat(countAfterSecond).isEqualTo(countAfterFirst)
    }

    @Test
    fun conflict_laterTimestampWins() {
        addBackend()
        val accountId = syncedAccount()

        repository.createTransaction(
            Transaction(
                amount = -100L,
                accountId = accountId,
                uuid = "tx-conflict",
                date = 1_000L
            )
        )

        val lateTs = TransactionChange.currentTimStamp + 100L
        writeRemote(
            accountId,
            "_1.json",
            Json.encodeToString(
                listOf(
                    TransactionChange(
                        type = TransactionChange.Type.created,
                        uuid = "tx-conflict",
                        timeStamp = lateTs,
                        amount = -999L,
                        date = 1_000L
                    )
                )
            )
        )

        performSync()

        // Local wins: when both sides have a "created" for the same UUID, the local amount is kept.
        // Looks like a bug: the later remote timestamp is ignored, local always wins.
        val stored = amountForUuid(accountId, "tx-conflict")
        assertThat(stored).isEqualTo(-100L)
    }

    @Test
    fun multipleAccounts_eachAccountHasItsOwnRemoteFolder() {
        addBackend()
        val accountA = syncedAccount(label = "AccountA")
        val accountB = syncedAccount(label = "AccountB")

        repository.createTransaction(
            Transaction(amount = -11L, accountId = accountA, uuid = "tx-a", date = 1_000L)
        )
        repository.createTransaction(
            Transaction(amount = -22L, accountId = accountB, uuid = "tx-b", date = 1_000L)
        )

        performSync()

        val fileA = remoteFile(accountA, "_1.json")
        val fileB = remoteFile(accountB, "_1.json")

        assertThat(fileA.exists()).isTrue()
        assertThat(fileB.exists()).isTrue()

        val changesA: List<TransactionChange> = Json.decodeFromString(fileA.readText())
        val changesB: List<TransactionChange> = Json.decodeFromString(fileB.readText())

        assertThat(changesA.map { it.uuid }).containsExactly("tx-a")
        assertThat(changesB.map { it.uuid }).containsExactly("tx-b")
    }

    @Test
    fun noOp_nothingHappensWhenBothSidesAreEmpty() {
        addBackend()
        val accountId = syncedAccount()

        performSync()

        assertThat(countTransactions(accountId)).isEqualTo(0)
        assertThat(lastSyncedLocal(accountId)).isNull()
        assertThat(lastSyncedRemote(accountId)).isNull()
    }
}
