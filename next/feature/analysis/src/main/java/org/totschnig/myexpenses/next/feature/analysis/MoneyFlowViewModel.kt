package org.totschnig.myexpenses.next.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.analysisState
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.tags.TagFacts
import org.totschnig.myexpenses.next.data.analysis.MoneyFlow
import org.totschnig.myexpenses.next.data.analysis.MoneyFlowAnalyzer
import org.totschnig.myexpenses.next.data.analysis.PeriodKind

/**
 * Where the money came from and went to, see [MoneyFlowAnalyzer]: the last twelve months for the
 * analysis tab and twelve months, a month or a year the user picks for the page of the money flow
 */
class MoneyFlowViewModel(
    private val ledgerSource: LedgerSource,
    private val tagFacts: Flow<TagFacts>,
) : ViewModel() {

    private data class Selection(val kind: PeriodKind = PeriodKind.TWELVE_MONTHS, val offset: Int = 0)

    private val selection = MutableStateFlow(Selection())

    private fun flows(selection: Flow<Selection>): StateFlow<AnalysisState<MoneyFlow>> =
        combine(
            ledgerSource.ledger, ledgerSource.catalog, tagFacts, ledgerSource.today, selection
        ) { ledger, catalog, tags, today, selected ->
            ledger?.let {
                MoneyFlowAnalyzer.analyse(
                    it, tags.excludedFromSpending, catalog.categoriesById, selected.kind, selected.offset, today
                )
            }
        }
            .combine(ledgerSource.consent) { flow, consent -> analysisState(consent, flow) }
            .flowOn(Dispatchers.Default)
            .stateWhileShown(viewModelScope, AnalysisState.Loading)

    /** The last twelve months */
    val lastTwelveMonths: StateFlow<AnalysisState<MoneyFlow>> = flows(MutableStateFlow(Selection()))

    /** The period picked by the user */
    val flow: StateFlow<AnalysisState<MoneyFlow>> = flows(selection)

    fun showLastTwelveMonths() {
        selection.value = Selection()
    }

    /** Switches between twelve months, months and years, starting with the current one */
    fun selectKind(kind: PeriodKind) {
        selection.value = Selection(kind)
    }

    fun showEarlier() {
        selection.update { it.copy(offset = it.offset + 1) }
    }

    fun showLater() {
        selection.update { it.copy(offset = (it.offset - 1).coerceAtLeast(0)) }
    }
}
