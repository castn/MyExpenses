package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.next.core.common.ledger.Memo
import org.totschnig.myexpenses.next.core.common.ledger.mapMemo
import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.common.shareWhileShown
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.data.categories.CategoryRepository
import org.totschnig.myexpenses.next.data.categories.CategoryRule

/**
 * The guided setup, once for the process: the payees to decide on, how much of the money has a
 * category, what will be categorized without asking, and how far the user is, see [SetupProgress].
 * Categories are written through [categories]. The flows run in [scope] while they are collected.
 *
 * @param contracts what the contracts step needs, [SetupContracts.None] without contracts
 */
class SetupRepository(
    ledgerSource: LedgerSource,
    private val categories: CategoryRepository,
    val store: SetupStore,
    scope: CoroutineScope,
    val contracts: Flow<SetupContracts> = flowOf(SetupContracts.None)
) {
    private val decisions = categories.store.decisions

    private data class QueueInput(val ledger: Ledger, val unclear: Set<String>)

    private val queueMemo = Memo<QueueInput, CategoryQueue>("setup: queue") { (ledger, unclear) ->
        categoryQueue(ledger, unclear)
    }

    /** Null while loading */
    val categoryQueue: SharedFlow<CategoryQueue?> =
        combine(
            ledgerSource.ledger,
            decisions.map { it.unclear }.distinctUntilChanged()
        ) { ledger, unclear ->
            ledger?.let { QueueInput(it, unclear) }
        }
            .mapMemo(queueMemo)
            .logTimings("setup: queue")
            .flowOn(Dispatchers.Default)
            .shareWhileShown(scope)

    private data class ShareInput(
        val ledger: Ledger,
        val rules: List<CategoryRule>,
        val categories: List<CategoryInfo>,
        val today: LocalDate
    )

    private val shareMemo = Memo<ShareInput, AutomaticShare>("setup: automatic share") { input ->
        with(input) { automaticShare(ledger, rules, categories, today) }
    }

    /** Null while loading */
    val automaticShare: SharedFlow<AutomaticShare?> =
        combine(
            ledgerSource.ledger,
            decisions.map { it.rules }.distinctUntilChanged(),
            ledgerSource.catalog.map { it.categories }.distinctUntilChanged(),
            ledgerSource.today
        ) { ledger, rules, categories, today ->
            ledger?.let { ShareInput(it, rules, categories, today) }
        }
            .mapMemo(shareMemo)
            .logTimings("setup: automatic share")
            .flowOn(Dispatchers.Default)
            .shareWhileShown(scope)

    val progress: Flow<SetupProgress> = store.progress

    /** The steps there are, the contracts step only with [contracts] */
    val steps: Flow<List<SetupStep>> = contracts.map { contracts ->
        SetupStep.entries.filter {
            it != SetupStep.CONTRACTS || contracts is SetupContracts.Available
        }
    }.distinctUntilChanged()

    /** The debits of [payee] without category get [categoryId], with [always] also later ones */
    suspend fun categorize(payee: SetupPayee, categoryId: Long, always: Boolean) {
        categories.categorize(payee.counterpart, payee.transactionIds, categoryId, always)
    }

    /** The user knows no category for [payee] ("Weiß ich nicht") */
    suspend fun markUnclear(payee: SetupPayee) {
        categories.markUnclear(payee.counterpart)
    }
}
