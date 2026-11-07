package org.totschnig.myexpenses.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.SyncResult
import android.content.pm.ProviderInfo
import android.os.Bundle
import java.io.File
import org.junit.After
import org.junit.Before
import org.robolectric.Robolectric
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.db2.loadAccount
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_SYNC_SEQUENCE_LOCAL
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.appendBooleanQueryParameter
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.sync.json.TransactionChange

/**
 * Runs the real sync code on a local folder: the sync adapter with the storage access framework
 * backend, whose account names start with "SAF".
 */
abstract class BaseSyncTest : BaseTestWithRepository() {
    protected lateinit var documents: LocalDocumentsProvider
    protected lateinit var cloud: File
    protected val accountManager: AccountManager by lazy { AccountManager.get(application) }
    protected val backend: Account = GenericAccountService.getAccount(BACKEND_NAME)

    @Before
    fun setUpCloud() {
        cloud = File(application.filesDir, "cloud").apply { mkdirs() }
        documents = Robolectric.buildContentProvider(LocalDocumentsProvider::class.java)
            .create(ProviderInfo().apply { authority = LocalDocumentsProvider.AUTHORITY })
            .get()
        documents.root = cloud
    }

    @After
    fun tearDownCloud() {
        cloud.deleteRecursively()
    }

    protected fun addBackend(encryptionPassword: String? = null) {
        accountManager.addAccountExplicitly(
            backend,
            null,
            Bundle().apply {
                putString(
                    GenericAccountService.KEY_SYNC_PROVIDER_URL,
                    documents.treeUri.toString()
                )
            }
        )
        encryptionPassword?.let { GenericAccountService.storePassword(application, backend, it) }
    }

    protected fun syncedAccount(
        label: String = "Synced",
        openingBalance: Long = 0,
        currency: String = currencyContext.homeCurrencyString
    ) = insertAccount(
        label,
        openingBalance = openingBalance,
        currency = currency,
        syncAccountName = BACKEND_NAME
    )

    protected fun accountUuid(accountId: Long) = repository.loadAccount(accountId)!!.uuid!!

    protected fun performSync(extras: Bundle = Bundle()): SyncResult {
        val adapter = SyncAdapter(application, true).also { application.injector.inject(it) }
        val syncResult = SyncResult()
        val client = contentResolver
            .acquireContentProviderClient(TransactionProvider.AUTHORITY)!!
        try {
            adapter.onPerformSync(
                backend,
                extras,
                TransactionProvider.AUTHORITY,
                client,
                syncResult
            )
        } finally {
            client.close()
        }
        return syncResult
    }

    protected fun remoteFile(accountId: Long, name: String) =
        File(File(cloud, accountUuid(accountId)), name)

    protected fun writeRemote(accountId: Long, name: String, contents: String) {
        remoteFile(accountId, name).apply {
            parentFile!!.mkdirs()
            writeText(contents)
        }
    }

    protected fun lastSyncedLocal(accountId: Long): String? =
        accountManager.getUserData(backend, SyncAdapter.keyLastSyncedLocal(accountId))

    protected fun lastSyncedRemote(accountId: Long): String? =
        accountManager.getUserData(backend, SyncAdapter.keyLastSyncedRemote(accountId))

    companion object {
        const val BACKEND_NAME = "SAF - test"
    }
}

/** Starts recording changes as the sync adapter does on the first sync of an account. */
fun ContentResolver.initChangeLog(accountId: Long) {
    update(
        TransactionProvider.CHANGES_URI.buildUpon()
            .appendQueryParameter(KEY_ACCOUNTID, accountId.toString())
            .appendBooleanQueryParameter(TransactionProvider.QUERY_PARAMETER_INIT)
            .build(),
        ContentValues(0),
        null,
        null
    )
}

/** The recorded changes of an account in the form the sync adapter reads them. */
fun ContentResolver.loadChanges(accountId: Long, sequence: Long = 1): List<TransactionChange> =
    query(
        TransactionProvider.CHANGES_URI.buildUpon()
            .appendQueryParameter(KEY_ACCOUNTID, accountId.toString())
            .appendQueryParameter(KEY_SYNC_SEQUENCE_LOCAL, sequence.toString())
            .build(),
        null,
        null,
        null,
        null
    )!!.useAndMapToList { TransactionChange.fromCursor(it) }
