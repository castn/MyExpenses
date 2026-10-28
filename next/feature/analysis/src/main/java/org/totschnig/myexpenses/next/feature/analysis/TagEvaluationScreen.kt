package org.totschnig.myexpenses.next.feature.analysis

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import org.totschnig.myexpenses.charts.ChartColumn
import org.totschnig.myexpenses.charts.ColumnChart
import org.totschnig.myexpenses.designsystem.ChoiceChips
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.NavigationRow
import org.totschnig.myexpenses.designsystem.PageSubtitle
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.ShareRow
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.formatRange
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberAxisAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.data.analysis.TagEvaluation
import org.totschnig.myexpenses.next.data.analysis.TagGroup

/**
 * The evaluation of one tag: what was spent, by month for themes, by category, and the transactions
 *
 * @param years to switch between for business expenses, the latest first
 * @param onSelectYear null if the year cannot be switched
 */
@Composable
fun TagEvaluationScreen(
    evaluation: TagEvaluation,
    currency: CurrencyInfo,
    onBack: () -> Unit,
    onShowTransactions: () -> Unit,
    modifier: Modifier = Modifier,
    years: List<Int> = emptyList(),
    year: Int? = null,
    onSelectYear: ((Int) -> Unit)? = null,
) {
    val amount = rememberAmountFormat(currency)
    val dateFormatter = rememberDateFormatter()
    DetailScaffold(
        onBack = onBack,
        title = evaluation.label,
        subtitle = { PageSubtitle(dateFormatter.formatRange(evaluation.start, evaluation.end)) },
        modifier = modifier
    ) {
        if (onSelectYear != null && years.size > 1) {
            ChoiceChips(
                options = years,
                selected = year,
                label = { it.toString() },
                onSelect = onSelectYear,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        DetailCard {
            InfoRow(stringResource(R.string.next_analysis_total), amount(evaluation.total), isFirst = true)
            when (evaluation.group) {
                TagGroup.TRIP -> InfoRow(
                    pluralStringResource(R.plurals.next_analysis_days, evaluation.days.toInt(), evaluation.days.toInt()),
                    stringResource(R.string.next_analysis_per_day, amount(evaluation.perDay))
                )
                TagGroup.THEME -> InfoRow(
                    stringResource(R.string.next_analysis_by_month),
                    stringResource(R.string.next_analysis_per_month, amount(evaluation.monthlyAverage))
                )
                TagGroup.BUSINESS -> {}
            }
        }
        if (evaluation.group == TagGroup.THEME) {
            SectionTitle(stringResource(R.string.next_analysis_by_month))
            DetailCard {
                val locale = LocalConfiguration.current.locales[0]
                ColumnChart(
                    evaluation.months.map { (month, spent) ->
                        ChartColumn(
                            label = month.month.getDisplayName(TextStyle.NARROW, locale),
                            value = spent.toFloat(),
                            valueLabel = amount(spent),
                            description = "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)} ${month.year}"
                        )
                    },
                    Modifier.padding(Spacing.content),
                    axisLabel = rememberAxisAmountFormat(currency)
                )
            }
        }
        if (evaluation.byCategory.isNotEmpty()) {
            SectionTitle(stringResource(R.string.next_analysis_by_category))
            DetailCard {
                val noCategory = stringResource(R.string.next_analysis_no_category)
                evaluation.byCategory.forEachIndexed { index, category ->
                    ShareRow(
                        label = category.label ?: noCategory,
                        value = amount(category.amount),
                        share = if (evaluation.total > 0) category.amount.toFloat() / evaluation.total else 0f,
                        isFirst = index == 0,
                        isMuted = category.mainCategoryId == null
                    )
                }
            }
        }
        SectionTitle(stringResource(R.string.next_analysis_transactions_title))
        DetailCard {
            NavigationRow(
                title = pluralStringResource(R.plurals.next_analysis_transactions, evaluation.count, evaluation.count),
                supportingText = evaluation.label,
                onClick = onShowTransactions,
                isFirst = true
            )
        }
    }
}

@Preview(name = "Theme")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TagEvaluationScreenPreview() {
    NextPreviewTheme {
        TagEvaluationScreen(previewAnalysis().themes.first(), CurrencyInfo.Preview, onBack = {}, onShowTransactions = {})
    }
}

@Preview(name = "Trip")
@Composable
private fun TripEvaluationPreview() {
    NextPreviewTheme {
        TagEvaluationScreen(previewAnalysis().trips.first(), CurrencyInfo.Preview, onBack = {}, onShowTransactions = {})
    }
}
