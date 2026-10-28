package org.totschnig.myexpenses.next.feature.tips

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.next.core.ui.DetailPage
import org.totschnig.myexpenses.next.core.ui.ExtraPage
import org.totschnig.myexpenses.next.core.ui.ListPage
import org.totschnig.myexpenses.next.core.ui.rememberListDetailPages
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.core.ui.shownOrLeave
import org.totschnig.myexpenses.next.core.ui.transactions.FixedTransactions
import org.totschnig.myexpenses.next.core.ui.transactions.FixedTransactionsScreen
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.tips.Outliers
import org.totschnig.myexpenses.next.data.tips.SavingTip
import org.totschnig.myexpenses.next.data.tips.SavingTipsOverview
import org.totschnig.myexpenses.next.data.tips.savingTips

/** The pages of the tab "Spartipps", the tips at the bottom of the back stack */
@Serializable
sealed interface SavingTipsKey : NavKey {
    @Serializable
    data object Home : SavingTipsKey

    /** Transactions a tip is about, by their key in [SavingTip.transactionGroups] */
    @Serializable
    data class Transactions(val key: String, val title: String, val subtitle: String) : SavingTipsKey
}

/**
 * The tab "Spartipps" and the pages behind its tips: transactions a tip is about, e.g. bank fees,
 * on a back stack of their own ([SavingTipsKey]). Contracts have their own tab.
 *
 * @param onOpenContract shows a contract by its signature in the contracts tab
 * @param fixedTransactions shows the transactions a tip is about
 * @param transactionDetails added to the details of the transactions a tip is about, e.g. their
 * contract; isOutlier for those of the outlier tip, which can be marked as one-off
 */
@Composable
fun SavingTipsFlow(
    tipsViewModel: SavingTipsViewModel,
    fixedTransactions: FixedTransactions,
    currency: CurrencyInfo,
    onOpenContract: (String) -> Unit,
    /** Opens the settings of the analyses */
    onInsightSettings: () -> Unit,
    modifier: Modifier = Modifier,
    transactionDetails: @Composable (transaction: ShownTransaction, isOutlier: Boolean) -> Unit = { _, _ -> },
    /** Below the tips, e.g. the tips that still lack data */
    footer: @Composable () -> Unit = {},
) {
    val state by tipsViewModel.savingTips.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(SavingTipsKey.Home)
    // The entries read the latest values, whenever NavDisplay creates their content
    val current by rememberUpdatedState(
        SavingTipsContent(tipsViewModel, fixedTransactions, currency, state, onOpenContract, onInsightSettings, transactionDetails, footer)
    )
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        sceneStrategies = listOf(rememberListDetailPages()),
        entryProvider = entryProvider {
            entry<SavingTipsKey.Home>(metadata = ListPage(R.string.next_pick_saving_tip)) { SavingTipsHome(current, backStack) }
            entry<SavingTipsKey.Transactions>(metadata = DetailPage()) { key -> TransactionsEntry(current, backStack, key) }
        }
    )
}

/** What the pages on the back stack of [SavingTipsFlow] need */
private class SavingTipsContent(
    val tipsViewModel: SavingTipsViewModel,
    val fixedTransactions: FixedTransactions,
    val currency: CurrencyInfo,
    val state: AnalysisState<SavingTipsOverview>,
    val onOpenContract: (String) -> Unit,
    val onInsightSettings: () -> Unit,
    val transactionDetails: @Composable (ShownTransaction, Boolean) -> Unit,
    val footer: @Composable () -> Unit,
)

@Composable
private fun SavingTipsHome(content: SavingTipsContent, backStack: NavBackStack<NavKey>) {
    val tipsViewModel = content.tipsViewModel
    SavingTipsScreen(
        state = content.state,
        currency = content.currency,
        onConsent = { tipsViewModel.ledgerSource.setConsent(true) },
        onSettings = content.onInsightSettings,
        onDismiss = tipsViewModel::dismissTip,
        onRestore = tipsViewModel::restoreTip,
        onMarkSeen = tipsViewModel::markTipsSeen,
        navigation = TipNavigation(
            onOpenContract = content.onOpenContract,
            onShowTransactions = { key, title, subtitle -> backStack.add(SavingTipsKey.Transactions(key, title, subtitle)) }
        ),
        footer = content.footer
    )
}

@Composable
private fun TransactionsEntry(content: SavingTipsContent, backStack: NavBackStack<NavKey>, key: SavingTipsKey.Transactions) {
    // Looked up in the current tips: once the tip is gone, so is its page
    val transactions = shownOrLeave(
        content.state.valueOrNull?.let { it.tips + it.dismissed }?.firstNotNullOfOrNull { it.transactionGroups[key.key] },
        isLoaded = content.state != AnalysisState.Loading
    ) { backStack.remove(key) } ?: return
    val ids = transactions.map { it.id }
    LaunchedEffect(ids) { content.fixedTransactions.show(key.title, ids) }
    FixedTransactionsScreen(
        title = key.title,
        subtitle = key.subtitle,
        onBack = { backStack.remove(key) }
    ) {
        val isOutlier = key.key.startsWith(Outliers.ID)
        content.fixedTransactions.List(it) { transaction -> content.transactionDetails(transaction, isOutlier) }
    }
}
