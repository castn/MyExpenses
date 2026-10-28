package org.totschnig.myexpenses.next.feature.analysis

import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.common.ledger.Memo
import org.totschnig.myexpenses.next.core.common.ledger.mapMemo
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.analysisState
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.tags.TagFacts
import org.totschnig.myexpenses.next.data.analysis.TagAnalysis
import org.totschnig.myexpenses.next.data.analysis.TagAnalyzer

/**
 * What was spent on trips, themes and business expenses, see [TagAnalyzer]
 */
class TagAnalysisViewModel(
    val ledgerSource: LedgerSource,
    private val tagFacts: Flow<TagFacts>,
) : ViewModel() {

    /** Year of the business expenses, null for the latest one */
    private val year = MutableStateFlow<Int?>(null)

    private data class AnalysisInput(
        val ledger: Ledger,
        val tags: List<TagInfo>,
        val meanings: Map<Long, TagMeaning>,
        val categories: Map<Long, CategoryInfo>,
        val today: LocalDate,
        val year: Int?,
    )

    private val memo = Memo<AnalysisInput, TagAnalysis>("analysis: tags") { input ->
        with(input) { TagAnalyzer.analyse(ledger, tags, meanings, categories, today, year) }
    }

    /** Null while loading or without consent */
    private val input: Flow<AnalysisInput?> =
        combine(ledgerSource.ledger, ledgerSource.catalog, tagFacts, ledgerSource.today, year) { ledger, catalog, tags, today, year ->
            ledger?.let { AnalysisInput(it, catalog.tags, tags.meanings, catalog.categoriesById, today, year) }
        }

    val analysis: StateFlow<AnalysisState<TagAnalysis>> =
        combine(ledgerSource.consent, input.mapMemo(memo)) { consent, analysis -> analysisState(consent, analysis) }
            .logTimings("analysis: tags")
            .flowOn(Dispatchers.Default)
            .stateWhileShown(viewModelScope, AnalysisState.Loading)

    fun selectYear(year: Int) {
        this.year.value = year
    }
}
