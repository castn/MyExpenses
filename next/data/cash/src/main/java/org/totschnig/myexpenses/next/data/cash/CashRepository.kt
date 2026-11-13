package org.totschnig.myexpenses.next.data.cash

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.next.core.common.shareWhileShown
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.isCashWithdrawal

/**
 * The cash account of the user, see [CashAccount], created and taken back through [writing]. Cash
 * withdrawals since it was created are moved to it by [movedToAccount]. The flows run in [scope]
 * while they are collected.
 */
class CashRepository(
    private val store: CashStore,
    private val writing: CashWriting,
    ledgerSource: LedgerSource,
    scope: CoroutineScope
) {

    /** Null if there is none */
    val account: Flow<CashAccount?> = store.account

    /** Withdrawals handed to [CashWriting.moveToAccount] once, not again if they stay, e.g. in another currency */
    private val tried: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /**
     * Moves new cash withdrawals to the cash account while it is collected, see [withdrawalsToMove]:
     * while the app is shown and right after an import. Emits the ids it moved.
     */
    val movedToAccount: SharedFlow<List<Long>> =
        combine(ledgerSource.ledger.filterNotNull(), store.account, ::Pair)
            .map { (ledger, account) ->
                account?.let { cash ->
                    withdrawalsToMove(ledger, cash).filter { tried.add(it) }.also {
                        if (it.isNotEmpty()) writing.moveToAccount(it, cash.id)
                    }
                }.orEmpty()
            }
            .shareWhileShown(scope)

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

/**
 * Ids of the cash withdrawals from the day [account] was created on, of other accounts and without
 * category: they become transfers to it. Those from before keep the category they got; one the user
 * gave a category stays spending.
 */
fun withdrawalsToMove(ledger: Ledger, account: CashAccount): List<Long> = ledger.transactions
    .filter {
        it.isCashWithdrawal && !it.date.isBefore(account.since) && it.accountId != account.id &&
            it.categoryId == null && !it.isSplit && !it.isSealed
    }
    .map { it.id }
