package org.totschnig.myexpenses.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.DebtLink
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.db2.linkToDebt
import org.totschnig.myexpenses.db2.loadDebt
import org.totschnig.myexpenses.db2.loadDebtLinks
import org.totschnig.myexpenses.db2.loadDebts
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.storeExchangeRate
import org.totschnig.myexpenses.provider.KEY_DEBT_ID
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_ROWID

/** The hooks the debts of the new UI read and write through, see RepositoryDebtDatabase in :ui_next */
@RunWith(AndroidJUnit4::class)
class DebtLinksTest : BaseDebtTest() {

    private val accountId by lazy { insertAccount("Cash", currency = HOME) }

    private val foreignAccountId by lazy {
        insertAccount("Dollars", currency = FOREIGN).also {
            repository.storeExchangeRate(it, 2.0, FOREIGN, HOME)
        }
    }

    private fun insert(account: Long, amount: Long, debtId: Long? = null) =
        repository.insertTransaction(
            accountId = account,
            amount = amount,
            date = LocalDateTime.of(DEBT_DATE, LocalTime.NOON),
            payeeId = payee,
            debtId = debtId
        ).id

    private fun linkOf(id: Long) = repository.loadTransaction(id).data.debtId

    @Test
    fun linksCarryAmountsPayeeDebtAndVoidState() {
        val debtId = saveDebt(amount = 10000)
        val home = insert(accountId, 3000, debtId)
        val foreign = insert(foreignAccountId, 1000, debtId)
        val voided = insert(accountId, 500, debtId)
        repository.deleteTransaction(voided, markAsVoid = true)

        val links = repository.loadDebtLinks("$KEY_DEBT_ID = ?", arrayOf(debtId.toString()))

        assertThat(links).containsExactly(
            DebtLink(home, 3000, HOME, 3000, payee, debtId, isVoid = false, DEBT_DATE),
            DebtLink(foreign, 1000, FOREIGN, 2000, payee, debtId, isVoid = false, DEBT_DATE),
            DebtLink(voided, 500, HOME, 500, payee, debtId, isVoid = true, DEBT_DATE)
        )
    }

    @Test
    fun aTransactionWithoutDebtIsFoundById() {
        val id = insert(accountId, 3000)
        assertThat(repository.loadDebtLinks("$KEY_ROWID = ?", arrayOf(id.toString())))
            .containsExactly(DebtLink(id, 3000, HOME, 3000, payee, null, isVoid = false, DEBT_DATE))
    }

    @Test
    fun linkMovesAndUnlinks() {
        val first = saveDebt(amount = 100, label = "First")
        val second = saveDebt(amount = 100, label = "Second")
        val id = insert(accountId, 50)

        repository.linkToDebt(id, first)
        assertThat(linkOf(id)).isEqualTo(first)
        repository.linkToDebt(id, second)
        assertThat(linkOf(id)).isEqualTo(second)
        repository.linkToDebt(id, null)
        assertThat(linkOf(id)).isNull()
    }

    @Test
    fun aSealedDebtRefusesLinkAndUnlink() {
        val sealed = saveDebt(amount = 100, label = "Sealed")
        val onSealed = insert(accountId, 50, sealed)
        val free = insert(accountId, 50)
        viewModel.closeDebt(sealed)

        assertThrows(SQLiteConstraintException::class.java) {
            repository.linkToDebt(onSealed, null)
        }
        assertThrows(SQLiteConstraintException::class.java) {
            repository.linkToDebt(free, sealed)
        }
        assertThat(linkOf(onSealed)).isEqualTo(sealed)
        assertThat(linkOf(free)).isNull()
    }

    @Test
    fun debtsOfAPayeeIncludeSealedOnes() {
        val open = saveDebt(amount = 100, label = "Open")
        val sealed = saveDebt(amount = 200, label = "Sealed")
        viewModel.closeDebt(sealed)

        val debts = repository.loadDebts("$KEY_PAYEEID = ?", arrayOf(payee.toString()))

        assertThat(debts.map { it.id to it.isSealed })
            .containsExactly(open to false, sealed to true)
        assertThat(repository.loadDebt(sealed)?.amount).isEqualTo(200)
        assertThat(repository.loadDebt(9999)).isNull()
    }
}
