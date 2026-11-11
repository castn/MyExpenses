package org.totschnig.myexpenses.next.feature.debts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.debts.Debt
import org.totschnig.myexpenses.next.data.debts.DebtDatabase
import org.totschnig.myexpenses.next.data.debts.DebtTransaction
import org.totschnig.myexpenses.next.data.debts.DebtValues
import org.totschnig.myexpenses.next.data.debts.DebtWriting

/** Keeps debts and transactions in memory; a closed debt refuses changes like the database */
class FakeDebtDatabase :
    DebtDatabase,
    DebtWriting {
    override val homeCurrency = CurrencyInfo.Preview
    val debts = mutableListOf<Debt>()
    val transactions = mutableListOf<DebtTransaction>()
    val payees = mutableListOf("Alice", "Bob")
    private val changes = MutableStateFlow(0)

    override fun changes(): Flow<Unit> = changes.map { }

    override fun transaction(transactionId: Long) = transactions.find { it.id == transactionId }

    override fun debt(debtId: Long) = debts.find { it.id == debtId }

    override fun debtsOf(payeeId: Long) = debts.filter { it.payeeId == payeeId }

    override fun paymentsOf(debtIds: Collection<Long>) =
        transactions.filter { it.debtId in debtIds }

    override fun link(transactionId: Long, debtId: Long?) {
        val transaction = transaction(transactionId)!!
        val touched = listOfNotNull(transaction.debtId, debtId).mapNotNull(::debt)
        check(touched.none { it.isSealed }) { "sealed" }
        transactions[transactions.indexOf(transaction)] = transaction.copy(debtId = debtId)
        changed()
    }

    override fun allDebts() = debts.toList()

    override fun payeeNames() = payees.toList()

    override fun currencies() = listOf(homeCurrency, CurrencyInfo("USD", "$", 2))

    override fun save(values: DebtValues): Long {
        val existing = debt(values.id)
        check(existing?.isSealed != true) { "sealed" }
        if (values.payee !in payees) payees += values.payee
        val id = if (values.id == 0L) (debts.maxOfOrNull { it.id } ?: 0L) + 1 else values.id
        val saved = Debt(
            id = id,
            label = values.label,
            payeeId = payees.indexOf(values.payee).toLong(),
            payeeName = values.payee,
            currency = currencies().first { it.code == values.currency },
            amount = values.amount,
            equivalentAmount = values.equivalentAmount,
            description = values.description,
            date = values.date
        )
        if (existing == null) debts += saved else debts[debts.indexOf(existing)] = saved
        changed()
        return id
    }

    override fun setSealed(debtId: Long, isSealed: Boolean) {
        val debt = debt(debtId)!!
        debts[debts.indexOf(debt)] = debt.copy(isSealed = isSealed)
        changed()
    }

    override fun delete(debtId: Long): Boolean {
        val removed = debts.removeAll { it.id == debtId }
        transactions.replaceAll { if (it.debtId == debtId) it.copy(debtId = null) else it }
        changed()
        return removed
    }

    private fun changed() {
        changes.value++
    }
}
