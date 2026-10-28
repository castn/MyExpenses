package org.totschnig.myexpenses.next.feature.analysis

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.totschnig.myexpenses.charts.BarChart
import org.totschnig.myexpenses.charts.ChartBar
import org.totschnig.myexpenses.charts.scale.largestWithRest
import org.totschnig.myexpenses.designsystem.ChoiceChips
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.NavigationRow
import org.totschnig.myexpenses.designsystem.PageSubtitle
import org.totschnig.myexpenses.designsystem.PeriodSwitcher
import org.totschnig.myexpenses.designsystem.SectionHeader
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.ShareRow
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.formatRange
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.data.analysis.CategoryAnalysis
import org.totschnig.myexpenses.next.data.analysis.CategorySpending
import org.totschnig.myexpenses.next.data.analysis.PeriodKind
import org.totschnig.myexpenses.next.data.analysis.SpendingPeriod
import org.totschnig.myexpenses.next.data.analysis.SubcategorySpending

/**
 * The card of the analysis tab: the largest categories of [analysis] as bars, compared with the
 * month before, and a row to all categories
 *
 * @param onOpenCategory with [CategorySpending.key]
 * @param onOpenAll also for the bar that sums up the smaller categories
 */
@Composable
fun SpendingOverview(
    analysis: CategoryAnalysis,
    currency: CurrencyInfo,
    onOpenCategory: (Long) -> Unit,
    onOpenAll: () -> Unit,
) {
    val amount = rememberAmountFormat(currency)
    val periodLabel = rememberPeriodLabel()
    Column {
        SectionHeader(title = stringResource(R.string.next_analysis_spending), trailing = amount(analysis.total))
        DetailCard {
            SpendingChart(analysis, amount, onOpenCategory, onOpenAll)
            NavigationRow(
                title = stringResource(R.string.next_analysis_all_categories),
                supportingText = periodLabel(analysis.period),
                onClick = onOpenAll
            )
        }
    }
}

/**
 * All categories in a month or year that can be switched
 *
 * @param onOpenCategory with [CategorySpending.key]
 */
@Composable
fun CategorySpendingScreen(
    analysis: CategoryAnalysis,
    currency: CurrencyInfo,
    onBack: () -> Unit,
    onSelectKind: (PeriodKind) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount = rememberAmountFormat(currency)
    val periodLabel = rememberPeriodLabel()
    val noCategory = stringResource(R.string.next_analysis_no_category)
    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.next_analysis_spending),
        modifier = modifier,
        header = {
            ChoiceChips(
                options = listOf(PeriodKind.MONTH, PeriodKind.YEAR),
                selected = analysis.period.kind,
                label = {
                    stringResource(
                        when (it) {
                            PeriodKind.MONTH -> R.string.next_analysis_month
                            PeriodKind.YEAR -> R.string.next_analysis_year
                            PeriodKind.TWELVE_MONTHS -> R.string.next_analysis_twelve_months
                        }
                    )
                },
                onSelect = onSelectKind,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            PeriodSwitcher(
                label = periodLabel(analysis.period),
                onPrevious = onEarlier.takeIf { analysis.hasEarlier },
                onNext = onLater.takeIf { !analysis.isCurrent },
                previousDescription = stringResource(R.string.next_analysis_earlier),
                nextDescription = stringResource(R.string.next_analysis_later)
            )
        }
    ) {
        DetailCard {
            Totals(analysis.total, analysis.previousTotal, analysis, amount, periodLabel)
        }
        if (analysis.categories.isEmpty()) {
            DetailCard(Modifier.padding(top = 12.dp)) {
                HintRow(stringResource(R.string.next_analysis_empty), isFirst = true)
            }
            return@DetailScaffold
        }
        SectionTitle(stringResource(R.string.next_analysis_by_category))
        DetailCard {
            SpendingChart(analysis, amount, onOpenCategory, onOpenAll = null)
        }
        SectionTitle(stringResource(R.string.next_analysis_all_categories))
        DetailCard {
            analysis.categories.forEachIndexed { index, category ->
                ShareRow(
                    label = category.label ?: noCategory,
                    value = amount(category.amount),
                    share = if (analysis.total > 0) category.amount.toFloat() / analysis.total else 0f,
                    isFirst = index == 0,
                    isMuted = category.mainCategoryId == null,
                    onClick = { onOpenCategory(category.key) }
                )
            }
        }
    }
}

/**
 * One main category in the period of [analysis]: what was spent, on which subcategories, and its
 * transactions
 */
@Composable
fun CategoryDetailScreen(
    analysis: CategoryAnalysis,
    category: CategorySpending,
    currency: CurrencyInfo,
    onBack: () -> Unit,
    onShowTransactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount = rememberAmountFormat(currency)
    val periodLabel = rememberPeriodLabel()
    val label = category.label ?: stringResource(R.string.next_analysis_no_category)
    DetailScaffold(
        onBack = onBack,
        title = label,
        subtitle = { PageSubtitle(periodLabel(analysis.period)) },
        modifier = modifier
    ) {
        DetailCard {
            Totals(category.amount, category.previous, analysis, amount, periodLabel)
        }
        // Worth a list only if not all was booked on the main category itself
        if (category.subcategories.any { it.categoryId != category.mainCategoryId }) {
            SectionTitle(stringResource(R.string.next_analysis_subcategories))
            DetailCard {
                category.subcategories.forEachIndexed { index, subcategory ->
                    ShareRow(
                        label = subcategory.label,
                        value = amount(subcategory.amount),
                        share = if (category.amount > 0) subcategory.amount.toFloat() / category.amount else 0f,
                        isFirst = index == 0
                    )
                }
            }
        }
        SectionTitle(stringResource(R.string.next_analysis_transactions_title))
        DetailCard {
            val count = category.transactionIds.size
            NavigationRow(
                title = pluralStringResource(R.plurals.next_analysis_transactions, count, count),
                supportingText = label,
                onClick = onShowTransactions,
                isFirst = true
            )
        }
    }
}

/**
 * What was spent and, if known, in the period before. The change is only shown for periods that
 * are over, since the current one is not comparable with a whole one yet.
 */
@Composable
private fun Totals(
    total: Long,
    previous: Long?,
    analysis: CategoryAnalysis,
    amount: (Long) -> String,
    periodLabel: (SpendingPeriod) -> String,
) {
    InfoRow(stringResource(R.string.next_analysis_total), amount(total), isFirst = true)
    if (previous != null) {
        InfoRow(periodLabel(analysis.period.previous()), amount(previous))
        if (!analysis.isCurrent) {
            InfoRow(stringResource(R.string.next_analysis_change), changeText(total, previous, amount))
        }
    }
}

/** E.g. "+120,00 € (+8 %)", without percentage if nothing was spent before */
private fun changeText(total: Long, previous: Long, amount: (Long) -> String): String {
    val difference = total - previous
    val sign = if (difference > 0) "+" else ""
    val percent = if (previous > 0) {
        val format = NumberFormat.getPercentInstance()
        " ($sign${format.format(difference.toDouble() / previous)})"
    } else ""
    return "$sign${amount(difference)}$percent"
}

/** A bar of the chart, null [key] for the one that sums up the smaller categories */
private data class SpendingBar(val key: Long?, val label: String, val amount: Long, val previous: Long?)

/**
 * The largest categories as bars, the others summed up, compared with the period before
 *
 * @param onOpenAll for the bar of the others, not tappable if null
 */
@Composable
private fun SpendingChart(
    analysis: CategoryAnalysis,
    amount: (Long) -> String,
    onOpenCategory: (Long) -> Unit,
    onOpenAll: (() -> Unit)?,
) {
    val noCategory = stringResource(R.string.next_analysis_no_category)
    val others = stringResource(R.string.next_analysis_others)
    val nameOf = rememberPeriodName()
    val bars = remember(analysis, noCategory, others) {
        largestWithRest(
            analysis.categories.map { SpendingBar(it.key, it.label ?: noCategory, it.amount, it.previous) },
            value = { it.amount.toDouble() }
        ) { rest ->
            SpendingBar(
                null, others, rest.sumOf { it.amount },
                rest.mapNotNull { it.previous }.takeIf { it.isNotEmpty() }?.sum()
            )
        }
    }
    BarChart(
        bars = bars.map { bar ->
            ChartBar(
                label = bar.label,
                value = bar.amount.toFloat(),
                valueLabel = amount(bar.amount),
                comparison = bar.previous?.toFloat(),
                comparisonLabel = bar.previous?.let(amount)
            )
        },
        modifier = Modifier.padding(Spacing.content),
        valueName = nameOf(analysis.period),
        comparisonName = nameOf(analysis.period.previous()),
        onClick = { index ->
            val key = bars[index].key
            if (key != null) onOpenCategory(key) else onOpenAll?.invoke()
        }
    )
}

/** "Oktober 2026" or "2026" */
@Composable
internal fun rememberPeriodLabel(): (SpendingPeriod) -> String {
    val months = rememberMonthFormatter()
    return remember(months) {
        { period ->
            when (period.kind) {
                PeriodKind.MONTH -> months.format(period.start)
                PeriodKind.YEAR -> period.start.year.toString()
                PeriodKind.TWELVE_MONTHS -> months.formatRange(period.start, period.end)
            }
        }
    }
}

/** Short, for legends: "Oktober" or "2026" */
@Composable
private fun rememberPeriodName(): (SpendingPeriod) -> String = remember {
    val months = DateTimeFormatter.ofPattern("LLLL")
    return@remember { period: SpendingPeriod ->
        when (period.kind) {
            PeriodKind.MONTH -> months.format(period.start)
            PeriodKind.YEAR -> period.start.year.toString()
            PeriodKind.TWELVE_MONTHS -> months.formatRange(period.start, period.end)
        }
    }
}

internal fun previewSpending(kind: PeriodKind = PeriodKind.MONTH, offset: Int = 0): CategoryAnalysis {
    fun category(id: Long?, label: String?, amount: Long, previous: Long?) = CategorySpending(
        id, label, amount, previous,
        subcategories = if (id == null) emptyList() else listOf(
            SubcategorySpending(id, label!!, amount * 2 / 3), SubcategorySpending(id + 100, "Sub", amount / 3)
        ),
        transactionIds = (1L..8L).toList()
    )
    val categories = listOf(
        category(1, "Wohnen", 95000, 95000),
        category(2, "Lebensmittel", 42000, 51000),
        category(3, "Mobilität", 18000, 9000),
        category(4, "Freizeit", 12000, 15000),
        category(null, null, 7000, 2000),
        category(5, "Gesundheit", 4000, 0),
        category(6, "Kleidung", 2500, 8000),
    )
    return CategoryAnalysis(
        period = SpendingPeriod.of(kind, offset, LocalDate.of(2026, 10, 12)),
        offset = offset,
        total = categories.sumOf { it.amount },
        previousTotal = categories.sumOf { it.previous ?: 0 },
        categories = categories,
        hasEarlier = true
    )
}

@Preview(name = "Overview")
@Composable
private fun SpendingOverviewPreview() {
    NextPreviewTheme {
        SpendingOverview(previewSpending(), CurrencyInfo.Preview, onOpenCategory = {}, onOpenAll = {})
    }
}

@Preview(name = "All categories")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategorySpendingScreenPreview() {
    NextPreviewTheme {
        CategorySpendingScreen(
            previewSpending(offset = 1), CurrencyInfo.Preview,
            onBack = {}, onSelectKind = {}, onEarlier = {}, onLater = {}, onOpenCategory = {}
        )
    }
}

@Preview(name = "Category")
@Composable
private fun CategoryDetailScreenPreview() {
    NextPreviewTheme {
        val analysis = previewSpending()
        CategoryDetailScreen(analysis, analysis.categories[1], CurrencyInfo.Preview, onBack = {}, onShowTransactions = {})
    }
}
