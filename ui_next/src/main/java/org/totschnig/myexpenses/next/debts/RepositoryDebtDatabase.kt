package org.totschnig.myexpenses.next.debts

import android.database.SQLException
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.totschnig.myexpenses.db2.DebtLink
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.linkToDebt
import org.totschnig.myexpenses.db2.loadDebt
import org.totschnig.myexpenses.db2.loadDebtLinks
import org.totschnig.myexpenses.db2.loadDebts
import org.totschnig.myexpenses.next.data.debts.Debt
import org.totschnig.myexpenses.next.data.debts.DebtDatabase
import org.totschnig.myexpenses.next.data.debts.DebtTransaction
import org.totschnig.myexpenses.next.toInfo
import org.totschnig.myexpenses.provider.KEY_DEBT_ID
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider.DEBTS_URI
import org.totschnig.myexpenses.provider.TransactionProvider.TRANSACTIONS_URI
import org.totschnig.myexpenses.viewmodel.data.DisplayDebt

/** The debts through the [Repository] */
class RepositoryDebtDatabase(private val repository: Repository) : DebtDatabase {
    private val contentResolver get() = repository.contentResolver

    override val homeCurrency: String get() = repository.currencyContext.homeCurrencyString

    override fun changes(): Flow<Unit> = combine(
        contentResolver.observeQuery(DEBTS_URI, notifyForDescendants = true),
        contentResolver.observeQuery(TRANSACTIONS_URI, notifyForDescendants = true)
    ) { _, _ -> }

    override fun transaction(transactionId: Long): DebtTransaction? = repository
        .loadDebtLinks("$KEY_ROWID = ?", arrayOf(transactionId.toString()))
        .firstOrNull()
        ?.toNext()

    override fun debt(debtId: Long): Debt? = repository.loadDebt(debtId)?.toNext()

    override fun debtsOf(payeeId: Long): List<Debt> =
        repository.loadDebts("$KEY_PAYEEID = ?", arrayOf(payeeId.toString())).map { it.toNext() }

    override fun paymentsOf(debtIds: Collection<Long>): List<DebtTransaction> = repository
        .loadDebtLinks("$KEY_DEBT_ID IN (${debtIds.joinToString()})", null)
        .map { it.toNext() }

    override fun link(transactionId: Long, debtId: Long?) {
        try {
            repository.linkToDebt(transactionId, debtId)
        } catch (e: SQLException) {
            throw IllegalStateException("Debt of transaction $transactionId refused", e)
        }
    }
}

private fun DisplayDebt.toNext() = Debt(
    id = id,
    label = label,
    payeeId = payeeId,
    payeeName = payeeName,
    currency = currency.toInfo(),
    amount = amount,
    equivalentAmount = equivalentAmount,
    isSealed = isSealed
)

private fun DebtLink.toNext() =
    DebtTransaction(transactionId, amount, currency, homeAmount, payeeId, debtId, isVoid)
