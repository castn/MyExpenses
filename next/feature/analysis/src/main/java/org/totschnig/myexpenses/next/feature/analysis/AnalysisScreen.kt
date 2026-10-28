package org.totschnig.myexpenses.next.feature.analysis

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.EmptyState
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.NavigationRow
import org.totschnig.myexpenses.designsystem.PageTopBar
import org.totschnig.myexpenses.designsystem.TopBarAction
import org.totschnig.myexpenses.designsystem.PromptCard
import org.totschnig.myexpenses.designsystem.SectionHeader
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.formatRange
import org.totschnig.myexpenses.next.data.guidance.DataGap
import org.totschnig.myexpenses.next.feature.analysis.ReadinessBox
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.data.analysis.CategoryAmount
import org.totschnig.myexpenses.next.data.analysis.CategoryAnalysis
import org.totschnig.myexpenses.next.data.analysis.CategorySpending
import org.totschnig.myexpenses.next.data.analysis.MoneyFlow
import org.totschnig.myexpenses.next.data.analysis.MonthAmount
import org.totschnig.myexpenses.next.data.analysis.TagAnalysis
import org.totschnig.myexpenses.next.data.analysis.TagEvaluation
import org.totschnig.myexpenses.next.data.analysis.TagGroup

/**
 * Content of the tab "Analyse": spending by category of the current month, and what was spent on
 * trips, themes and business expenses, each a row that opens its evaluation
 *
 * @param spending of the current month, null while loading
 * @param onOpenCategory with [CategorySpending.key]
 * @param onOpenSpending opens all categories
 * @param gaps what the analysis still lacks, shown at the top
 * @param onShowGap shows the transactions of a gap
 */
@Composable
fun AnalysisScreen(
    state: AnalysisState<TagAnalysis>,
    spending: CategoryAnalysis?,
    currency: CurrencyInfo,
    modifier: Modifier = Modifier,
    moneyFlow: MoneyFlow? = null,
    onConsent: () -> Unit = {},
    /** Opens the settings of the analysis, none if null */
    onSettings: (() -> Unit)? = null,
    onOpen: (TagEvaluation) -> Unit = {},
    onOpenCategory: (Long) -> Unit = {},
    onOpenSpending: () -> Unit = {},
    onOpenMoneyFlow: () -> Unit = {},
    gaps: List<DataGap> = emptyList(),
    onShowGap: (DataGap) -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(onBack = null, title = stringResource(R.string.next_tab_analysis), actions = {
            onSettings?.let {
                TopBarAction(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.next_settings_open),
                    onClick = it
                )
            }
        })
        val contentModifier = Modifier.weight(1f)
        when (state) {
            AnalysisState.Loading -> Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            AnalysisState.AskConsent, AnalysisState.Declined -> PromptCard(
                icon = Icons.Default.Insights,
                title = stringResource(R.string.next_analysis_consent_title),
                text = stringResource(R.string.next_analysis_consent_text),
                confirmLabel = stringResource(R.string.next_contracts_consent_accept),
                onConfirm = onConsent,
                modifier = contentModifier
            )

            is AnalysisState.Ready -> AnalysisList(
                state.value, spending, moneyFlow, currency, onOpen, onOpenCategory, onOpenSpending, onOpenMoneyFlow,
                gaps, onShowGap, contentModifier
            )
        }
    }
}

@Composable
private fun AnalysisList(
    analysis: TagAnalysis,
    spending: CategoryAnalysis?,
    moneyFlow: MoneyFlow?,
    currency: CurrencyInfo,
    onOpen: (TagEvaluation) -> Unit,
    onOpenCategory: (Long) -> Unit,
    onOpenSpending: () -> Unit,
    onOpenMoneyFlow: () -> Unit,
    gaps: List<DataGap>,
    onShowGap: (DataGap) -> Unit,
    modifier: Modifier,
) {
    val hasSpending = spending != null && spending.categories.isNotEmpty()
    val hasMoneyFlow = moneyFlow != null && !moneyFlow.isEmpty
    if (analysis.isEmpty && !hasSpending && !hasMoneyFlow && gaps.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.next_analysis_empty),
            hint = stringResource(R.string.next_analysis_empty_hint),
            modifier = modifier
        )
        return
    }
    val amount = rememberAmountFormat(currency)
    val dateFormatter = rememberDateFormatter()
    val tripsTitle = stringResource(R.string.next_analysis_trips)
    val themesTitle = stringResource(R.string.next_analysis_themes)
    val businessTitle = stringResource(R.string.next_analysis_business, analysis.year)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (gaps.isNotEmpty()) {
            item(key = "readiness") {
                ReadinessBox(gaps, onShowGap, Modifier.padding(bottom = 12.dp))
            }
        }
        if (spending != null && hasSpending) {
            item(key = "spending") {
                SpendingOverview(spending, currency, onOpenCategory, onOpenAll = onOpenSpending)
            }
        }
        if (moneyFlow != null && hasMoneyFlow) {
            item(key = "moneyFlow") {
                MoneyFlowOverview(moneyFlow, currency, onOpen = onOpenMoneyFlow)
            }
        }
        // The info box already tells about tags
        if (analysis.isEmpty && DataGap.NoTags !in gaps) {
            item(key = "tags") {
                SectionHeader(title = stringResource(R.string.next_analysis_tags))
                DetailCard {
                    HintRow(stringResource(R.string.next_analysis_empty_hint), isFirst = true)
                }
            }
        }
        section(tripsTitle, analysis.trips, onOpen, amount) {
            dateFormatter.formatRange(it.start, it.end)
        }
        section(themesTitle, analysis.themes, onOpen, amount) {
            stringResource(R.string.next_analysis_per_month, amount(it.monthlyAverage))
        }
        section(businessTitle, analysis.business, onOpen, amount) {
            pluralStringResource(R.plurals.next_analysis_transactions, it.count, it.count)
        }
    }
}

private fun LazyListScope.section(
    title: String,
    evaluations: List<TagEvaluation>,
    onOpen: (TagEvaluation) -> Unit,
    amount: (Long) -> String,
    supportingText: @Composable (TagEvaluation) -> String,
) {
    if (evaluations.isEmpty()) return
    item(key = title) {
        SectionHeader(title = title, trailing = amount(evaluations.sumOf { it.total }))
        DetailCard {
            evaluations.forEachIndexed { index, evaluation ->
                NavigationRow(
                    title = evaluation.label,
                    supportingText = supportingText(evaluation),
                    onClick = { onOpen(evaluation) },
                    isFirst = index == 0,
                    value = amount(evaluation.total)
                )
            }
        }
    }
}

internal fun previewAnalysis(): TagAnalysis {
    fun evaluation(id: Long, label: String, group: TagGroup, start: LocalDate, end: LocalDate, total: Long) =
        TagEvaluation(
            tagId = id, label = label, color = null, group = group, start = start, end = end, total = total,
            months = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(YearMonth.from(end)) }
                .mapIndexed { index, month -> MonthAmount(month, total / 12 + index * 500) }
                .toList(),
            byCategory = listOf(
                CategoryAmount(1, "Essen", total * 3 / 5), CategoryAmount(2, "Reisen", total / 5),
                CategoryAmount(null, null, total / 5)
            ),
            transactionIds = (1L..12L).toList()
        )
    return TagAnalysis(
        trips = listOf(
            evaluation(1, "Urlaub Italien", TagGroup.TRIP, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14), 184000),
            evaluation(2, "Hamburg", TagGroup.TRIP, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 8), 42050)
        ),
        themes = listOf(
            evaluation(3, "Hund", TagGroup.THEME, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 10, 5), 96000)
        ),
        business = listOf(
            evaluation(4, "Beruflich", TagGroup.BUSINESS, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 5), 31200)
        ),
        businessYears = listOf(2026, 2025),
        year = 2026
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnalysisScreenPreview() {
    NextPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            AnalysisScreen(
                AnalysisState.Ready(previewAnalysis()), previewSpending(), CurrencyInfo.Preview,
                gaps = listOf(DataGap.Uncategorized(List(12) { it.toLong() }, 96))
            )
        }
    }
}

@Preview(name = "Empty")
@Composable
private fun AnalysisEmptyPreview() {
    NextPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            AnalysisScreen(
                AnalysisState.Ready(TagAnalysis(emptyList(), emptyList(), emptyList(), emptyList(), 2026)),
                null,
                CurrencyInfo.Preview
            )
        }
    }
}
