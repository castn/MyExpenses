package org.totschnig.myexpenses.next.data.tips

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import org.totschnig.myexpenses.next.data.budgets.BudgetFacts
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.ledger.Memo
import org.totschnig.myexpenses.next.core.model.ledger.analysisState
import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.common.shareWhileShown
import org.totschnig.myexpenses.next.data.tags.TagFacts

/**
 * Tips where to spend less, see [SavingTip], and which of them the user dismissed or has seen,
 * once for the process, for the tab, widgets and notifications. The decisions are kept in [store],
 * which is part of the backup. The flows run in [scope] while they are collected.
 */
class SavingTipsRepository(
    val ledgerSource: LedgerSource,
    val store: TipStore,
    contractFacts: Flow<ContractFacts?>,
    tagFacts: Flow<TagFacts>,
    receiptFacts: Flow<ReceiptFacts>,
    scope: CoroutineScope,
    budgetFacts: Flow<BudgetFacts?> = flowOf(null),
) {
    /** Decisions of the user only sort the tips, they are not part of it */
    private data class TipsInput(
        val contracts: ContractFacts,
        val tags: TagFacts,
        val receipts: ReceiptFacts,
        val today: LocalDate,
        val budget: BudgetFacts?,
    )

    private val memo = Memo<TipsInput, List<SavingTip>>("tips: saving tips") { (contracts, tags, receipts, today, budget) ->
        savingTips(contracts, tags, receipts, today, budget)
    }

    /** Null while loading or without consent */
    private val input: Flow<TipsInput?> =
        combine(
            contractFacts, tagFacts, receiptFacts, ledgerSource.today,
            // The other tips need not wait for the budget
            budgetFacts.onStart { emit(null) }
        ) { contracts, tags, receipts, today, budget ->
            contracts?.let { TipsInput(it, tags, receipts, today, budget) }
        }

    /** Tips where to spend less, found in the confirmed contracts, see [SavingTip] */
    val savingTips: SharedFlow<AnalysisState<SavingTipsOverview>> =
        combine(ledgerSource.consent, input, store.decisions) { consent, input, decisions ->
            analysisState(consent, input?.let {
                val (dismissed, shown) = memo.of(it)
                    .partition(decisions::isDismissed)
                SavingTipsOverview(
                    tips = shown,
                    dismissed = dismissed,
                    newIds = shown.filter(decisions::isNew).mapTo(HashSet()) { tip -> tip.id }
                )
            })
        }
            .logTimings("tips: saving tips")
            .flowOn(Dispatchers.Default)
            .shareWhileShown(scope)
}
