package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.ledger.Memo
import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.common.shareWhileShown
import org.totschnig.myexpenses.next.data.tags.TagFacts

/**
 * The pots of the user and where they stand in the current period, see [BudgetAnalyzer], once
 * for the process, for the overview, other features, widgets and notifications. The pots are kept
 * in [store]. The flows run in [scope] while they are collected.
 *
 * @param receiptFacts for pots of item groups, null while loading
 */
class BudgetRepository(
    val ledgerSource: LedgerSource,
    contractFacts: Flow<ContractFacts?>,
    tagFacts: Flow<TagFacts>,
    val store: BudgetStore,
    scope: CoroutineScope,
    receiptFacts: Flow<ReceiptFacts?> = flowOf(null),
) {
    val currentMemo = Memo<BudgetInput, BudgetOverview>("budget: current", BudgetAnalyzer::analyse)

    /** Everything but the period, null while loading or without consent */
    val input: SharedFlow<BudgetInput?> = combine(
        combine(ledgerSource.ledger, ledgerSource.catalog, contractFacts, tagFacts, store.pots) { ledger, catalog, contracts, tags, pots ->
            if (ledger == null || contracts == null) null
            else BudgetInput(ledger, pots, catalog.categoriesById, contracts.confirmed, contracts.salaries, tags.meanings, LocalDate.MIN)
        },
        ledgerSource.today,
        // Receipts are only needed for pots of item groups, the others need not wait for them
        receiptFacts.onStart { emit(null) }
    ) { input, today, receipts ->
        input?.copy(
            today = today,
            receipts = if (input.pots.any { it.kind == PotKind.ITEM_GROUP }) receipts?.items.orEmpty() else emptyMap()
        )
    }.shareWhileShown(scope)

    /** The current period, for the overview and for other features, null while loading or without consent */
    val current: SharedFlow<BudgetOverview?> = input
        .map { it?.let(currentMemo::of) }
        .logTimings("budget: current")
        .flowOn(Dispatchers.Default)
        .shareWhileShown(scope)

    /** Whether pots of item groups can be offered: they need scanned receipts */
    val canScanReceipts: Flow<Boolean> = receiptFacts.map { it?.canScan != false }

    /** What other features know about the budget, e.g. saving tips; null while loading or without consent */
    val facts: Flow<BudgetFacts?> = current.map { overview -> overview?.let { BudgetFacts(it.pots) } }
}
