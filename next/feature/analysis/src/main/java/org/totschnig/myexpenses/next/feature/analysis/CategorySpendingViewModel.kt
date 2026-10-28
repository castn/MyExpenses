package org.totschnig.myexpenses.next.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
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
import org.totschnig.myexpenses.next.data.analysis.CategoryAnalysis
import org.totschnig.myexpenses.next.data.analysis.CategoryAnalyzer
import org.totschnig.myexpenses.next.data.analysis.PeriodKind

/**
 * What was spent per category, see [CategoryAnalyzer]: the current month for the analysis tab
 * and a month or year the user picks for the page with all categories
 */
class CategorySpendingViewModel(
    private val ledgerSource: LedgerSource,
    private val tagFacts: Flow<TagFacts>,
) : ViewModel() {

    private data class Selection(val kind: PeriodKind = PeriodKind.MONTH, val offset: Int = 0)

    private val selection = MutableStateFlow(Selection())

    private fun analyses(selection: Flow<Selection>): StateFlow<AnalysisState<CategoryAnalysis>> =
        combine(ledgerSource.consent, ledgerSource.ledger, ledgerSource.catalog, tagFacts, selection) { consent, ledger, catalog, tags, selection ->
            analysisState(consent, ledger?.let {
                CategoryAnalyzer.analyse(
                    it, tags.excludedFromSpending, catalog.categoriesById, selection.kind, selection.offset, LocalDate.now()
                )
            })
        }
            .flowOn(Dispatchers.Default)
            .stateWhileShown(viewModelScope, AnalysisState.Loading)

    /** The current month, compared with the one before */
    val currentMonth: StateFlow<AnalysisState<CategoryAnalysis>> = analyses(MutableStateFlow(Selection()))

    /** The month or year picked by the user */
    val analysis: StateFlow<AnalysisState<CategoryAnalysis>> = analyses(selection)

    fun showCurrentMonth() {
        selection.value = Selection()
    }

    /** Switches between months and years, starting with the current one */
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
