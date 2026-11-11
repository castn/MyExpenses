package org.totschnig.myexpenses.next.data.debts

import kotlinx.coroutines.flow.Flow
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/**
 * The debts and their transactions in the database of the app, implemented by the shell. Apart
 * from the flow, the functions block and are called on `Dispatchers.IO`.
 */
interface DebtDatabase {
    val homeCurrency: CurrencyInfo

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

    /** All debts, also closed ones */
    fun allDebts(): List<Debt>
}

/**
 * Entering and changing debts in the database of the app, implemented by the shell; blocks, called
 * on `Dispatchers.IO`
 */
interface DebtWriting {
    /** The names of the payees, to pick one for a new debt */
    fun payeeNames(): List<String>

    /** The currencies a debt can be in */
    fun currencies(): List<CurrencyInfo>

    /**
     * Saves a new debt or changes one, with the payee of that name, which is created if there is
     * none. Returns the id. Throws [IllegalStateException] if the database refuses it, e.g. as the
     * debt is closed.
     */
    fun save(values: DebtValues): Long

    /** Closes or reopens the debt */
    fun setSealed(debtId: Long, isSealed: Boolean)

    /** Deletes the debt, its transactions stay without it; false if it was not deleted */
    fun delete(debtId: Long): Boolean
}
