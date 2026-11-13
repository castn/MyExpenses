package org.totschnig.myexpenses.next.data.cash

import java.io.Serializable
import java.time.LocalDate

/**
 * The account the user keeps cash in, created with what it held on [since]. From then on cash
 * withdrawals are movements to it rather than spending. [Serializable] for the saved state.
 */
data class CashAccount(val id: Long, val since: LocalDate) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** What the cash area needs of the app */
interface CashWriting {
    /** Creates a cash account in the home currency holding [balance] in minor units, returns its id */
    suspend fun createAccount(balance: Long): Long

    /** Deletes the account [accountId] unless something was booked on it; false if it is kept */
    suspend fun deleteIfEmpty(accountId: Long): Boolean

    /**
     * The debits [transactionIds] become transfers to the account [accountId]; those in another
     * currency than it stay as they are
     */
    suspend fun moveToAccount(transactionIds: List<Long>, accountId: Long)
}
