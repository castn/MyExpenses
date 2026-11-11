package org.totschnig.myexpenses.next.data.debts

import kotlinx.coroutines.flow.Flow

/**
 * The debts and their transactions in the database of the app, implemented by the shell. Apart
 * from the flow, the functions block and are called on `Dispatchers.IO`.
 */
interface DebtDatabase {
    /** The code of the home currency */
    val homeCurrency: String

    /** Emits at once and on each change of debts or transactions */
    fun changes(): Flow<Unit>

    /** Null if the transaction is gone */
    fun transaction(transactionId: Long): DebtTransaction?

    /** Null if the debt is gone */
    fun debt(debtId: Long): Debt?

    /** All debts of the payee, also closed ones */
    fun debtsOf(payeeId: Long): List<Debt>

    /** The transactions linked to the debts, also parts of splits and voided ones */
    fun paymentsOf(debtIds: Collection<Long>): List<DebtTransaction>

    /**
     * Links the transaction to the debt, or unlinks it with null. Throws [IllegalStateException]
     * if the database refuses it, e.g. as the debt it pays or is to pay is closed.
     */
    fun link(transactionId: Long, debtId: Long?)
}
