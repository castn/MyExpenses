package org.totschnig.myexpenses.next.debts

import android.database.SQLException
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.totschnig.myexpenses.db2.DebtLink
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.deleteDebt
import org.totschnig.myexpenses.db2.linkToDebt
import org.totschnig.myexpenses.db2.loadDebt
import org.totschnig.myexpenses.db2.loadDebtLinks
import org.totschnig.myexpenses.db2.loadDebts
import org.totschnig.myexpenses.db2.requireParty
import org.totschnig.myexpenses.db2.setDebtSealed
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.debts.Debt
import org.totschnig.myexpenses.next.data.debts.DebtDatabase
import org.totschnig.myexpenses.next.data.debts.DebtTransaction
import org.totschnig.myexpenses.next.data.debts.DebtValues
import org.totschnig.myexpenses.next.data.debts.DebtWriting
import org.totschnig.myexpenses.next.toInfo
import org.totschnig.myexpenses.provider.KEY_CODE
import org.totschnig.myexpenses.provider.KEY_DEBT_ID
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider.CURRENCIES_URI
import org.totschnig.myexpenses.provider.TransactionProvider.DEBTS_URI
import org.totschnig.myexpenses.provider.TransactionProvider.PAYEES_URI
import org.totschnig.myexpenses.provider.TransactionProvider.TRANSACTIONS_URI
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.util.epoch2LocalDate
import org.totschnig.myexpenses.viewmodel.data.Debt as AppDebt
import org.totschnig.myexpenses.viewmodel.data.DisplayDebt

/** The debts through the [Repository] */
class RepositoryDebtDatabase(private val repository: Repository) :
    DebtDatabase,
    DebtWriting {
    private val contentResolver get() = repository.contentResolver
    private val currencyContext get() = repository.currencyContext

    override val homeCurrency: CurrencyInfo get() = currencyContext.homeCurrencyUnit.toInfo()

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

    override fun link(transactionId: Long, debtId: Long?) = refusedAsState {
        repository.linkToDebt(transactionId, debtId)
    }

    override fun allDebts(): List<Debt> = repository.loadDebts(null, null).map { it.toNext() }

    override fun payeeNames(): List<String> = contentResolver
        .query(PAYEES_URI, arrayOf(KEY_PAYEE_NAME), null, null, "$KEY_PAYEE_NAME COLLATE LOCALIZED")
        ?.useAndMapToList { it.getString(0) }
        .orEmpty()

    override fun currencies(): List<CurrencyInfo> {
        val home = homeCurrency
        val codes = contentResolver.query(CURRENCIES_URI, arrayOf(KEY_CODE), null, null, KEY_CODE)
            ?.useAndMapToList { it.getString(0) }
            .orEmpty()
        return listOf(home) + codes.filter { it != home.code }.map { currencyContext[it].toInfo() }
    }

    override fun save(values: DebtValues): Long = refusedAsState {
        val payeeId = checkNotNull(repository.requireParty(values.payee)) { "No payee" }
        repository.saveDebt(
            AppDebt(
                id = values.id,
                label = values.label,
                description = values.description,
                payeeId = payeeId,
                amount = values.amount,
                currency = currencyContext[values.currency],
                date = values.date,
                equivalentAmount = values.equivalentAmount
            )
        )
    }

    override fun setSealed(debtId: Long, isSealed: Boolean) = refusedAsState {
        repository.setDebtSealed(debtId, isSealed)
    }

    override fun delete(debtId: Long): Boolean = refusedAsState { repository.deleteDebt(debtId) }

    /** The database refuses with an exception of SQLite, the debts expect [IllegalStateException] */
    private fun <T> refusedAsState(block: () -> T): T = try {
        block()
    } catch (e: SQLException) {
        throw IllegalStateException("Refused by the database", e)
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
    isSealed = isSealed,
    description = description,
    date = epoch2LocalDate(date)
)

private fun DebtLink.toNext() =
    DebtTransaction(transactionId, amount, currency, homeAmount, payeeId, debtId, isVoid, date)
