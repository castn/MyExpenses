package org.totschnig.myexpenses.next.data.cash

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The cash account of the user, see [CashAccount], created and taken back through [writing]
 */
class CashRepository(private val store: CashStore, private val writing: CashWriting) {

    /** Null if there is none */
    val account: Flow<CashAccount?> = store.account

    /** Creates the cash account holding [balance] in minor units of the home currency on [today] */
    suspend fun open(balance: Long, today: LocalDate): CashAccount =
        CashAccount(writing.createAccount(balance), today).also { store.set(it) }

    /**
     * Takes back [open]: the account is no longer the cash account, and it is deleted unless
     * something was booked on it meanwhile
     */
    suspend fun takeBack(account: CashAccount) {
        store.set(null)
        writing.deleteIfEmpty(account.id)
    }
}
