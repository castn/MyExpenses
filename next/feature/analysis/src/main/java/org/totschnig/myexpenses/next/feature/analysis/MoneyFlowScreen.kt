package org.totschnig.myexpenses.next.feature.analysis

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.totschnig.myexpenses.charts.SankeyChart
import org.totschnig.myexpenses.charts.layout.SankeyHit
import org.totschnig.myexpenses.designsystem.ChoiceChips
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.KeyFigure
import org.totschnig.myexpenses.designsystem.KeyFigures
import org.totschnig.myexpenses.designsystem.NavigationRow
import org.totschnig.myexpenses.designsystem.PeriodSwitcher
import org.totschnig.myexpenses.designsystem.SectionHeader
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.data.analysis.FlowActor
import org.totschnig.myexpenses.next.data.analysis.FlowGroup
import org.totschnig.myexpenses.next.data.analysis.MoneyFlow
import org.totschnig.myexpenses.next.data.analysis.PeriodKind
import org.totschnig.myexpenses.next.data.analysis.SpendingPeriod

/**
 * The money flow of the last twelve months in the analysis tab: what came in, what went out, what
 * is left, and the way to the diagram
 */
@Composable
fun MoneyFlowOverview(flow: MoneyFlow, currency: CurrencyInfo, onOpen: () -> Unit) {
    val amount = rememberAmountFormat(currency)
    Column {
        SectionHeader(title = stringResource(R.string.next_flow_title))
        DetailCard {
            FlowFigures(flow, amount)
            NavigationRow(
                title = stringResource(R.string.next_flow_open),
                supportingText = stringResource(R.string.next_flow_last_twelve_months),
                onClick = onOpen
            )
        }
    }
}

/**
 * Where the money of twelve months, a month or a year came from and went to, as Sankey diagram.
 * Tapping a node or band tells what it stands for and leads to its transactions.
 *
 * @param onShowTransactions with a title and the ids of the transactions
 */
@Composable
fun MoneyFlowScreen(
    flow: MoneyFlow,
    currency: CurrencyInfo,
    onBack: () -> Unit,
    onSelectKind: (PeriodKind) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onShowTransactions: (String, List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount = rememberAmountFormat(currency)
    val periodLabel = rememberPeriodLabel()
    val labels = rememberFlowLabels()
    val chart = remember(flow, labels, amount) { MoneyFlowChart.of(flow, labels, amount) }
    var selected by remember(flow) { mutableStateOf<SankeyHit?>(null) }
    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.next_flow_title),
        modifier = modifier,
        header = {
            ChoiceChips(
                options = listOf(PeriodKind.TWELVE_MONTHS, PeriodKind.MONTH, PeriodKind.YEAR),
                selected = flow.period.kind,
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
                label = periodLabel(flow.period),
                onPrevious = onEarlier.takeIf { flow.hasEarlier },
                onNext = onLater.takeIf { !flow.isCurrent },
                previousDescription = stringResource(R.string.next_analysis_earlier),
                nextDescription = stringResource(R.string.next_analysis_later)
            )
        }
    ) {
        DetailCard {
            FlowFigures(flow, amount)
        }
        if (flow.isEmpty) {
            DetailCard(Modifier.padding(top = 12.dp)) {
                HintRow(stringResource(R.string.next_analysis_empty), isFirst = true)
            }
            return@DetailScaffold
        }
        DetailCard(Modifier.padding(top = 12.dp)) {
            val item = selected?.let(chart::itemOf)
            when {
                item == null -> HintRow(stringResource(R.string.next_flow_hint), isFirst = true)
                item.count == null || item.transactionIds.isEmpty() -> InfoRow(item.title, amount(item.amount), isFirst = true)
                else -> NavigationRow(
                    title = item.title,
                    supportingText = pluralStringResource(R.plurals.next_analysis_transactions, item.count, item.count),
                    value = amount(item.amount),
                    onClick = { onShowTransactions(item.title, item.transactionIds) },
                    isFirst = true
                )
            }
        }
        DetailCard(Modifier.padding(top = 12.dp)) {
            val actors = chart.nodes.count { it.column == 3 }
            SankeyChart(
                chart.nodes,
                chart.flows,
                Modifier
                    .padding(Spacing.content)
                    .fillMaxWidth()
                    // Room for the actors, which are the most nodes of a column
                    .height((actors * 24).coerceIn(360, 720).dp),
                selected = selected,
                onSelect = { selected = it }
            )
        }
    }
}

/** Earned, spent and what is left, negative if more was spent */
@Composable
private fun FlowFigures(flow: MoneyFlow, amount: (Long) -> String) {
    KeyFigures(
        listOf(
            KeyFigure(stringResource(R.string.next_flow_earned), amount(flow.earned)),
            KeyFigure(stringResource(R.string.next_flow_spent), amount(flow.spent)),
            KeyFigure(stringResource(R.string.next_flow_left), amount(flow.left)),
        )
    )
}

@Composable
private fun rememberFlowLabels(): FlowLabels {
    val accounts = stringResource(R.string.next_flow_accounts)
    val noCategory = stringResource(R.string.next_analysis_no_category)
    val noPayee = stringResource(R.string.next_flow_no_payee)
    val others = stringResource(R.string.next_analysis_others)
    val left = stringResource(R.string.next_flow_left)
    val savings = stringResource(R.string.next_flow_savings)
    val resources = LocalResources.current
    return remember(accounts, noCategory, noPayee, others, left, savings, resources) {
        FlowLabels(
            accounts = accounts,
            noCategory = noCategory,
            noPayee = noPayee,
            others = others,
            otherActors = { resources.getQuantityString(R.plurals.next_flow_other_actors, it, it) },
            left = left,
            savings = savings
        )
    }
}

internal fun previewFlow(kind: PeriodKind = PeriodKind.TWELVE_MONTHS): MoneyFlow {
    fun actor(name: String?, amount: Long, count: Int = 4, others: Int = 0) =
        FlowActor(name, amount, count, (1L..count).toList(), others)
    fun group(key: Long, label: String?, amount: Long, actors: List<FlowActor> = emptyList()) =
        FlowGroup(key, label, amount, 12, (1L..12L).toList(), actors)
    return MoneyFlow(
        period = SpendingPeriod.of(kind, 0, LocalDate.of(2026, 10, 13)),
        offset = 0,
        hasEarlier = true,
        incomes = listOf(group(1, "Gehalt", 3_000_000), group(2, "Erstattungen", 240_000)),
        expenses = listOf(
            group(10, "Wohnen", 1_140_000, listOf(actor("Vermieter", 1_080_000, 12), actor("Stadtwerke", 60_000))),
            group(11, "Lebensmittel", 504_000, listOf(actor("REWE", 260_000), actor("Aldi", 150_000), actor(null, 94_000, 9, 5))),
            group(12, "Essen gehen", 186_000, listOf(actor("McDonald's", 18_600, 20), actor("Pizzeria", 120_000), actor(null, 47_400, 6, 3))),
            group(13, "Mobilität", 210_000, listOf(actor("Shell", 140_000), actor("Deutsche Bahn", 70_000))),
            group(MoneyFlow.REST, null, 90_000, listOf(actor(null, 90_000, 8, 6))),
        ),
        expenseOrder = listOf(10, 11, 13, 12)
    )
}

@Preview(name = "Overview")
@Composable
private fun MoneyFlowOverviewPreview() {
    NextPreviewTheme {
        MoneyFlowOverview(previewFlow(), CurrencyInfo.Preview, onOpen = {})
    }
}

@Preview(name = "Money flow", heightDp = 1200)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 1200)
@Composable
private fun MoneyFlowScreenPreview() {
    NextPreviewTheme {
        MoneyFlowScreen(
            previewFlow(), CurrencyInfo.Preview,
            onBack = {}, onSelectKind = {}, onEarlier = {}, onLater = {}, onShowTransactions = { _, _ -> }
        )
    }
}
