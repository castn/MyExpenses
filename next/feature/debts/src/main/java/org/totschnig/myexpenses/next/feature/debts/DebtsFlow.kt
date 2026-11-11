package org.totschnig.myexpenses.next.feature.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.EmptyState
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LoadingBlock
import org.totschnig.myexpenses.designsystem.MenuButton
import org.totschnig.myexpenses.designsystem.MenuEntry
import org.totschnig.myexpenses.designsystem.NavigationRow
import org.totschnig.myexpenses.designsystem.OverviewCard
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.theme.LocalSemanticColors
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.next.data.debts.DebtOverview
import org.totschnig.myexpenses.next.data.debts.DebtSort
import org.totschnig.myexpenses.next.data.debts.DebtSummary

/**
 * The pages of the debts on a back stack of their own ([DebtKey]): the overview, a debt with its
 * history and the form of a debt
 *
 * @param openDebt a debt shown from the start
 */
@Composable
fun DebtsFlow(viewModel: DebtBookViewModel, onClose: () -> Unit, openDebt: Long? = null) {
    val start = listOfNotNull<NavKey>(DebtKey.Overview, openDebt?.let { DebtKey.Detail(it) })
    val backStack = rememberNavBackStack(*start.toTypedArray())
    val snackbar = remember { SnackbarHostState() }
    fun back() {
        if (backStack.lastOrNull() is DebtKey.Form) viewModel.cancelForm()
        if (backStack.size > 1) backStack.removeLastOrNull() else onClose()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .safeDrawingPadding()
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = ::back,
            entryProvider = entryProvider {
                entry<DebtKey.Overview> { DebtOverviewPage(viewModel, backStack, ::back) }
                entry<DebtKey.Detail> { key ->
                    DebtDetailPage(viewModel, key.debtId, ::back) { debt ->
                        viewModel.startForm(debt)
                        backStack.add(DebtKey.Form(debt.id))
                    }
                }
                entry<DebtKey.Form> { DebtFormPage(viewModel, ::back) }
            }
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    DebtMessages(viewModel, snackbar)
    // After saving, the form gives way to the debt saved
    val savedId = viewModel.form.collectAsStateWithLifecycle().value.savedId
    LaunchedEffect(savedId) {
        if (savedId == null) return@LaunchedEffect
        if (backStack.lastOrNull() is DebtKey.Form) backStack.removeLastOrNull()
        val saved = DebtKey.Detail(savedId)
        if (backStack.lastOrNull() != saved) backStack.add(saved)
        viewModel.savedShown()
    }
}

@Composable
private fun DebtMessages(viewModel: DebtBookViewModel, snackbar: SnackbarHostState) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val refused = stringResource(R.string.next_debt_refused)
    val deleted = stringResource(R.string.next_debt_deleted)
    LaunchedEffect(message) {
        val text = when (message) {
            DebtMessage.Refused -> refused
            DebtMessage.Deleted -> deleted
            null -> return@LaunchedEffect
        }
        viewModel.messageShown()
        snackbar.showSnackbar(text)
    }
}

@Composable
private fun DebtOverviewPage(
    viewModel: DebtBookViewModel,
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        DetailScaffold(
            onBack = onBack,
            title = stringResource(R.string.next_debts_title),
            actions = {
                MenuButton(
                    entries = overviewMenu(settings.sort, settings.showAll, viewModel),
                    contentDescription = stringResource(R.string.next_debt_actions)
                )
            }
        ) {
            when (val shown = overview) {
                null -> LoadingBlock()

                else -> OverviewContent(shown, settings.sort, viewModel) {
                    backStack.add(DebtKey.Detail(it.debt.id))
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                viewModel.startForm()
                backStack.add(DebtKey.Form())
            },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.next_debts_add)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.screen)
        )
    }
}

@Composable
private fun overviewMenu(sort: DebtSort, showAll: Boolean, actions: DebtBookActions) = listOf(
    MenuEntry(
        stringResource(
            if (showAll) R.string.next_debts_hide_closed else R.string.next_debts_show_all
        ),
        if (showAll) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
    ) { actions.setShowAll(!showAll) }
) + DebtSort.entries.filter { it != sort }.map { option ->
    MenuEntry(stringResource(option.label), Icons.AutoMirrored.Outlined.Sort) {
        actions.setSort(option)
    }
}

private val DebtSort.label
    get() = when (this) {
        DebtSort.Payee -> R.string.next_debts_sort_payee
        DebtSort.Label -> R.string.next_debts_sort_label
        DebtSort.Balance -> R.string.next_debts_sort_balance
    }

@Composable
private fun OverviewContent(
    overview: DebtOverview,
    sort: DebtSort,
    viewModel: DebtBookViewModel,
    onOpen: (DebtSummary) -> Unit
) {
    if (overview.debts.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.next_debts_empty),
            hint = stringResource(R.string.next_debts_empty_hint)
        )
        return
    }
    val formatter = LocalAmountFormatter.current
    val colors = LocalSemanticColors.current
    DetailCard {
        InfoRow(
            stringResource(R.string.next_debts_total),
            formatter.format(overview.total, viewModel.homeCurrency),
            isFirst = true,
            valueColor = colors.amountColor(overview.total.compareTo(0))
        )
    }
    val groups = if (sort == DebtSort.Payee) {
        overview.debts.groupBy { it.debt.payeeName }.toList()
    } else {
        listOf(null to overview.debts)
    }
    groups.forEach { (payee, debts) ->
        payee?.let { SectionTitle(it) }
        DetailCard {
            debts.forEachIndexed { index, summary ->
                val debt = summary.debt
                NavigationRow(
                    title = debt.label,
                    supportingText = listOfNotNull(
                        debtTitle(summary),
                        stringResource(R.string.next_debt_closed_badge).takeIf { debt.isSealed }
                    ).joinToString(" · "),
                    onClick = { onOpen(summary) },
                    isFirst = index == 0,
                    value = formatter.format(summary.balance.balance, debt.currency),
                    valueColor = colors.amountColor(summary.balance.balance.compareTo(0))
                )
            }
        }
    }
}

/** E.g. "Alice owes you", following the sign of what is left */
@Composable
internal fun debtTitle(summary: DebtSummary): String {
    val payee = summary.debt.payeeName
    val balance = summary.balance.balance
    return when {
        balance > 0 -> stringResource(R.string.next_debt_owes_me, payee)
        balance < 0 -> stringResource(R.string.next_debt_i_owe, payee)
        else -> stringResource(R.string.next_debt_settled_with, payee)
    }
}

/**
 * Card of the overview with the balance of the open debts, shown while there are some; opens the
 * debts
 */
@Composable
fun DebtOverviewCard(viewModel: DebtBookViewModel, onOpen: () -> Unit) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val open = overview?.debts?.filter { !it.debt.isSealed && !it.isSettled }.orEmpty()
    if (open.isEmpty()) return
    val formatter = LocalAmountFormatter.current
    val colors = LocalSemanticColors.current
    val total = overview?.total ?: 0L
    OverviewCard(title = stringResource(R.string.next_debts_title), onClick = onOpen) {
        InfoRow(
            stringResource(R.string.next_debts_total),
            formatter.format(total, viewModel.homeCurrency),
            isFirst = true,
            valueColor = colors.amountColor(total.compareTo(0))
        )
        open.take(OVERVIEW_DEBTS).forEach { summary ->
            InfoRow(
                debtTitle(summary),
                formatter.format(summary.balance.balance, summary.debt.currency),
                valueColor = colors.amountColor(summary.balance.balance.compareTo(0))
            )
        }
    }
}

/** The overview card shows this many debts */
private const val OVERVIEW_DEBTS = 3
