package org.totschnig.myexpenses.next.feature.budgets

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.totschnig.myexpenses.charts.ChartColumn
import org.totschnig.myexpenses.charts.ColumnChart
import org.totschnig.myexpenses.designsystem.ActionCard
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.MeterRow
import org.totschnig.myexpenses.designsystem.NavigationRow
import org.totschnig.myexpenses.designsystem.PageSubtitle
import org.totschnig.myexpenses.designsystem.PeriodSwitcher
import org.totschnig.myexpenses.designsystem.PromptCard
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.TopBarAction
import org.totschnig.myexpenses.designsystem.theme.LocalSemanticColors
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.balance.BalancePeriod
import org.totschnig.myexpenses.next.core.ui.periodLabel
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberAxisAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.core.ui.icons.CatalogIcon
import org.totschnig.myexpenses.designsystem.IconTile
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.totschnig.myexpenses.next.data.budgets.BudgetFrame
import org.totschnig.myexpenses.next.data.budgets.BudgetOverview
import org.totschnig.myexpenses.next.data.budgets.Pot
import org.totschnig.myexpenses.next.data.budgets.PotKind
import org.totschnig.myexpenses.next.data.budgets.PotStatus

/**
 * The budget in a period: the frame from the monthly balance and the pots
 *
 * @param onOpenPot with [Pot.id]
 */
@Composable
fun BudgetScreen(
    state: AnalysisState<BudgetOverview>,
    currency: CurrencyInfo,
    onBack: () -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onOpenPot: (String) -> Unit,
    modifier: Modifier = Modifier,
    onConsent: () -> Unit = {},
    onAddPot: () -> Unit = {},
    legacyCount: Int = 0,
    onImportLegacy: () -> Unit = {},
    onDismissLegacy: () -> Unit = {},
) {
    val overview = (state as? AnalysisState.Ready)?.value
    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.next_budget_title),
        modifier = modifier,
        header = overview?.let {
            {
                PeriodSwitcher(
                    label = periodLabel(it.period.start, it.period.end),
                    onPrevious = onEarlier.takeIf { _ -> it.hasEarlier },
                    onNext = onLater.takeIf { _ -> !it.isCurrent },
                    previousDescription = stringResource(R.string.next_analysis_earlier),
                    nextDescription = stringResource(R.string.next_analysis_later)
                )
            }
        }
    ) {
        when (state) {
            AnalysisState.Loading -> Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            AnalysisState.AskConsent, AnalysisState.Declined -> PromptCard(
                icon = Icons.Outlined.Savings,
                title = stringResource(R.string.next_budget_title),
                text = stringResource(R.string.next_budget_consent),
                confirmLabel = stringResource(R.string.next_contracts_consent_accept),
                onConfirm = onConsent
            )

            is AnalysisState.Ready -> {
                if (legacyCount > 0) {
                    ActionCard(
                        icon = Icons.Outlined.Savings,
                        title = pluralStringResource(R.plurals.next_budget_legacy_title, legacyCount, legacyCount),
                        text = stringResource(R.string.next_budget_legacy_text),
                        confirmLabel = stringResource(R.string.next_budget_legacy_import),
                        onConfirm = onImportLegacy,
                        dismissLabel = stringResource(R.string.next_budget_legacy_dismiss),
                        onDismiss = onDismissLegacy,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                BudgetContent(state.value, currency, onOpenPot, onAddPot)
            }
        }
    }
}

@Composable
private fun BudgetContent(overview: BudgetOverview, currency: CurrencyInfo, onOpenPot: (String) -> Unit, onAddPot: () -> Unit) {
    val amount = rememberAmountFormat(currency)
    SectionTitle(stringResource(R.string.next_budget_frame))
    DetailCard {
        FrameRows(overview.frame, amount)
    }
    SectionTitle(stringResource(R.string.next_budget_pots))
    DetailCard {
        if (overview.pots.isEmpty()) {
            HintRow(stringResource(R.string.next_budget_empty), isFirst = true)
        }
        overview.pots.forEachIndexed { index, status ->
            PotRow(status, amount, isFirst = index == 0, onClick = { onOpenPot(status.pot.id) })
        }
        LinkRow(stringResource(R.string.next_budget_add_pot), onClick = onAddPot)
    }
}

@Composable
private fun FrameRows(frame: BudgetFrame, amount: (Long) -> String) {
    val colors = LocalSemanticColors.current
    InfoRow(stringResource(R.string.next_balance_income), amount(frame.income), isFirst = true)
    InfoRow(stringResource(R.string.next_budget_fixed), "−" + amount(frame.fixed))
    InfoRow(stringResource(R.string.next_budget_planned), "−" + amount(frame.planned))
    InfoRow(
        stringResource(R.string.next_budget_unplanned), amount(frame.unplanned),
        valueColor = colors.amountColor(frame.unplanned.compareTo(0))
    )
    if (frame.spentOutsidePots > 0) {
        InfoRow(stringResource(R.string.next_budget_outside), amount(frame.spentOutsidePots))
    }
    InfoRow(
        stringResource(R.string.next_budget_free), amount(frame.free),
        valueColor = colors.amountColor(frame.free.compareTo(0))
    )
}

/** A pot in the list of the budget, with a meter of what is used */
@Composable
internal fun PotRow(status: PotStatus, amount: (Long) -> String, isFirst: Boolean, onClick: (() -> Unit)?) {
    val pot = status.pot
    val total = when (pot.kind) {
        PotKind.GOAL -> pot.amount
        else -> status.planned + status.carried
    }
    if (pot.kind == PotKind.ITEM_GROUP) {
        ItemGroupRow(status, isFirst, onClick)
        return
    }
    val value = when {
        pot.kind == PotKind.GOAL -> stringResource(R.string.next_budget_to_save, amount(status.left.coerceAtLeast(0)))
        status.isOverspent -> stringResource(R.string.next_budget_over, amount(-status.left))
        else -> stringResource(R.string.next_budget_left, amount(status.left))
    }
    val supporting = buildList {
        add(
            if (pot.kind == PotKind.GOAL) stringResource(R.string.next_budget_saved_of, amount(status.saved), amount(pot.amount))
            else stringResource(R.string.next_budget_of, amount(total))
        )
        if (status.upcoming > 0) add(stringResource(R.string.next_budget_due, amount(status.upcoming)))
        status.projected?.takeIf { it > total }?.let { add(stringResource(R.string.next_budget_projected, amount(it))) }
    }.joinToString(" · ")
    fun share(value: Long) = if (total > 0) value.toFloat() / total else if (value > 0) 1f else 0f
    MeterRow(
        label = pot.name,
        value = value,
        used = share(if (pot.kind == PotKind.GOAL) status.saved else status.spent),
        committed = share(status.upcoming),
        projected = status.projected?.let(::share),
        supportingText = supporting,
        leadingIcon = { PotIcon(pot) },
        isFirst = isFirst,
        onClick = onClick
    )
}

/** A pot of item groups: their share of the scanned purchases against the largest share allowed */
@Composable
private fun ItemGroupRow(status: PotStatus, isFirst: Boolean, onClick: (() -> Unit)?) {
    val percent = remember { NumberFormat.getPercentInstance() }
    val max = (status.pot.maxSharePercent ?: 0) / 100f
    val share = status.share
    MeterRow(
        label = status.pot.name,
        value = if (share != null) stringResource(R.string.next_budget_share, percent.format(share), percent.format(max))
        else stringResource(R.string.next_budget_max_share, percent.format(max)),
        used = if (share != null && max > 0f) share / max else 0f,
        supportingText = if (status.scannedPurchases > 0) {
            pluralStringResource(R.plurals.next_budget_scanned, status.scannedPurchases, status.scannedPurchases)
        } else stringResource(R.string.next_budget_no_receipts),
        leadingIcon = { PotIcon(status.pot) },
        isFirst = isFirst,
        onClick = onClick
    )
}

/** The icon of [pot]: the one chosen, else one that fits its name, else the one of its kind */
@Composable
internal fun PotIcon(pot: Pot, isLarge: Boolean = false) {
    val name = pot.iconName
    if (name != null) CatalogIcon(name, isLarge = isLarge)
    else Icon(
        pot.kind.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(if (isLarge) 28.dp else 24.dp)
    )
}

/** [PotIcon] on an [IconTile] */
@Composable
internal fun PotIconTile(pot: Pot, isLarge: Boolean = false) {
    IconTile(isLarge = isLarge) { PotIcon(pot, isLarge) }
}

val PotKind.icon: ImageVector
    get() = when (this) {
        PotKind.RUNNING -> Icons.Outlined.Autorenew
        PotKind.YEARLY -> Icons.Outlined.CalendarMonth
        PotKind.GOAL -> Icons.Outlined.Flag
        PotKind.PROJECT -> Icons.Outlined.Workspaces
        PotKind.ITEM_GROUP -> Icons.Outlined.ShoppingBasket
    }

/**
 * One pot in the period of [overview]: where it stands, its earlier periods and its transactions
 */
@Composable
fun PotDetailScreen(
    overview: BudgetOverview,
    status: PotStatus,
    currency: CurrencyInfo,
    onBack: () -> Unit,
    onShowTransactions: () -> Unit,
    onShowOneOffs: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
) {
    val amount = rememberAmountFormat(currency)
    val pot = status.pot
    val dates = rememberDateFormatter()
    DetailScaffold(
        onBack = onBack,
        title = pot.name,
        subtitle = {
            PageSubtitle(
                when (pot.kind) {
                    PotKind.RUNNING -> periodLabel(overview.period.start, overview.period.end)
                    else -> listOfNotNull(
                        stringResource(pot.kind.labelRes),
                        pot.end?.let { stringResource(R.string.next_budget_until, dates.format(it)) }
                    ).joinToString(" · ")
                }
            )
        },
        actions = { TopBarAction(Icons.Outlined.Edit, stringResource(R.string.next_budget_edit), onEdit) },
        modifier = modifier
    ) {
        DetailCard {
            PotRow(status, amount, isFirst = true, onClick = null)
        }
        DetailCard(Modifier.padding(top = 12.dp)) {
            PotFigures(status, amount)
        }
        if (pot.kind == PotKind.RUNNING && status.history.any { it > 0 }) {
            SectionTitle(stringResource(R.string.next_budget_history))
            DetailCard {
                HistoryChart(status, overview.period, currency)
            }
        }
        SectionTitle(stringResource(R.string.next_analysis_transactions_title))
        DetailCard {
            val count = status.transactionIds.size
            NavigationRow(
                title = pluralStringResource(R.plurals.next_analysis_transactions, count, count),
                supportingText = pot.name,
                onClick = onShowTransactions,
                isFirst = true,
                value = amount(status.spent)
            )
            if (status.oneOffIds.isNotEmpty()) {
                val oneOffs = status.oneOffIds.size
                NavigationRow(
                    title = pluralStringResource(R.plurals.next_budget_one_offs, oneOffs, oneOffs),
                    supportingText = stringResource(R.string.next_budget_one_offs_hint),
                    onClick = onShowOneOffs,
                    value = amount(status.oneOff)
                )
            }
        }
    }
}

@Composable
private fun PotFigures(status: PotStatus, amount: (Long) -> String) {
    val pot = status.pot
    when (pot.kind) {
        PotKind.ITEM_GROUP -> {
            InfoRow(stringResource(R.string.next_budget_in_groups), amount(status.spent), isFirst = true)
            InfoRow(stringResource(R.string.next_budget_allowed), amount(status.planned))
        }

        PotKind.GOAL -> {
            InfoRow(stringResource(R.string.next_budget_target), amount(pot.amount), isFirst = true)
            InfoRow(stringResource(R.string.next_budget_saved), amount(status.saved))
            if (status.spent != 0L) InfoRow(stringResource(R.string.next_budget_spent), amount(status.spent))
        }

        else -> {
            InfoRow(
                stringResource(if (pot.kind == PotKind.YEARLY) R.string.next_budget_added else R.string.next_budget_planned_amount),
                amount(status.planned),
                isFirst = true
            )
            if (status.carried != 0L) InfoRow(stringResource(R.string.next_budget_carried), amount(status.carried))
            InfoRow(stringResource(R.string.next_budget_spent), amount(status.spent))
            if (status.fixedSpent != 0L) InfoRow(stringResource(R.string.next_budget_contracts), amount(status.fixedSpent))
            if (status.upcoming != 0L) InfoRow(stringResource(R.string.next_budget_upcoming), amount(status.upcoming))
            if (pot.kind != PotKind.RUNNING && status.spentInPeriod != status.spent) {
                InfoRow(stringResource(R.string.next_budget_spent_in_period), amount(status.spentInPeriod))
            }
            status.projected?.let { InfoRow(stringResource(R.string.next_budget_projection), amount(it)) }
        }
    }
}

/** What was spent in the earlier periods and in the shown one, the last column */
@Composable
private fun HistoryChart(status: PotStatus, period: BalancePeriod, currency: CurrencyInfo) {
    val months = remember { DateTimeFormatter.ofPattern("MMM") }
    val amount = rememberAmountFormat(currency)
    val periods = status.history.size
    val columns = (status.history + status.spent).mapIndexed { index, spent ->
        // Periods are about a month long, the label is the month they start in
        val start = period.start.minusMonths((periods - index).toLong())
        ChartColumn(months.format(start), spent.toFloat(), amount(spent))
    }.takeLast(6)
    ColumnChart(
        columns = columns,
        modifier = Modifier.padding(Spacing.content),
        axisLabel = rememberAxisAmountFormat(currency)
    )
}

internal val PotKind.labelRes: Int
    get() = when (this) {
        PotKind.RUNNING -> R.string.next_budget_kind_running
        PotKind.YEARLY -> R.string.next_budget_kind_yearly
        PotKind.GOAL -> R.string.next_budget_kind_goal
        PotKind.PROJECT -> R.string.next_budget_kind_project
        PotKind.ITEM_GROUP -> R.string.next_budget_kind_item_group
    }

private val previewOverview = BudgetOverview(
    period = BalancePeriod(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 28), true),
    offset = 0,
    hasEarlier = true,
    frame = BudgetFrame(income = 320000, fixed = 145000, planned = 90000, spentOutsidePots = 12000, free = 41000),
    pots = listOf(
        PotStatus(
            Pot(name = "Täglicher Bedarf", kind = PotKind.RUNNING, amount = 45000),
            planned = 45000, spent = 31000, projected = 47000, history = listOf(42000, 44000, 39000)
        ),
        PotStatus(
            Pot(name = "Fitness", kind = PotKind.RUNNING, amount = 5000),
            planned = 5000, upcoming = 3500
        ),
        PotStatus(Pot(name = "Ausgehen", kind = PotKind.RUNNING, amount = 15000), planned = 15000, spent = 17500),
        PotStatus(Pot(name = "Geschenke", kind = PotKind.YEARLY, amount = 60000), planned = 50000, spent = 21000),
        PotStatus(
            Pot(name = "Neues Spiel", kind = PotKind.GOAL, amount = 7000, end = LocalDate.of(2026, 12, 24)),
            planned = 7000, saved = 4000
        ),
    )
)

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetScreenPreview() {
    NextPreviewTheme {
        BudgetScreen(AnalysisState.Ready(previewOverview), CurrencyInfo.Preview, {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun PotDetailScreenPreview() {
    NextPreviewTheme {
        PotDetailScreen(previewOverview, previewOverview.pots.first(), CurrencyInfo.Preview, {}, {}, {})
    }
}

