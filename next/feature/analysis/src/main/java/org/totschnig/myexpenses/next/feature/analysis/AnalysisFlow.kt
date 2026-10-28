package org.totschnig.myexpenses.next.feature.analysis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.guidance.DataGap
import kotlinx.coroutines.flow.StateFlow
import org.totschnig.myexpenses.next.data.guidance.DataReadiness
import org.totschnig.myexpenses.next.feature.analysis.gapTitle
import org.totschnig.myexpenses.next.core.ui.shownOrLeave
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.core.ui.transactions.FixedTransactionsScreen
import org.totschnig.myexpenses.next.core.ui.transactions.FixedTransactions
import org.totschnig.myexpenses.next.data.analysis.CategoryAnalysis
import org.totschnig.myexpenses.next.data.analysis.CategorySpending
import org.totschnig.myexpenses.next.data.analysis.MoneyFlow
import org.totschnig.myexpenses.next.data.analysis.TagAnalysis
import org.totschnig.myexpenses.next.data.analysis.TagGroup

/** The pages of the tab "Analyse", the overview at the bottom of the back stack */
@Serializable
sealed interface AnalysisKey : NavKey {
    @Serializable
    data object Home : AnalysisKey

    /** The transactions of a gap of the data, by [DataGap.key] */
    @Serializable
    data class Gap(val key: String) : AnalysisKey

    /** The evaluation of a tag */
    @Serializable
    data class Tag(val tagId: Long) : AnalysisKey

    @Serializable
    data class TagTransactions(val tagId: Long) : AnalysisKey

    /** Spending by category, in the period chosen in [CategorySpendingViewModel] */
    @Serializable
    data object Spending : AnalysisKey

    /** A category of [Spending], by [CategorySpending.key] */
    @Serializable
    data class Category(val key: Long) : AnalysisKey

    @Serializable
    data class CategoryTransactions(val key: Long) : AnalysisKey

    @Serializable
    data object MoneyFlow : AnalysisKey

    /** What was tapped in the money flow */
    @Serializable
    data class FlowTransactions(val title: String, val ids: List<Long>) : AnalysisKey
}

/**
 * The tab "Analyse": the overview, spending by category with the details of one category, the
 * money flow, the evaluation of one tag, and the transactions of each, on a back stack of their
 * own ([AnalysisKey])
 *
 * @param readiness tells what the analysis still lacks
 * @param transactionDetails added to the details of the transactions shown, e.g. to give a category
 */
@Composable
fun AnalysisFlow(
    viewModel: TagAnalysisViewModel,
    spendingViewModel: CategorySpendingViewModel,
    moneyFlowViewModel: MoneyFlowViewModel,
    fixedTransactions: FixedTransactions,
    currency: CurrencyInfo,
    /** Opens the settings of the analyses */
    onInsightSettings: () -> Unit,
    modifier: Modifier = Modifier,
    readiness: StateFlow<DataReadiness?>? = null,
    transactionDetails: (@Composable (ShownTransaction) -> Unit)? = null,
) {
    val content = AnalysisContent(
        viewModel = viewModel,
        spendingViewModel = spendingViewModel,
        moneyFlowViewModel = moneyFlowViewModel,
        fixedTransactions = fixedTransactions,
        currency = currency,
        onInsightSettings = onInsightSettings,
        transactionDetails = transactionDetails,
        gaps = readiness?.collectAsStateWithLifecycle()?.value?.gaps,
        state = viewModel.analysis.collectAsStateWithLifecycle().value,
        currentMonth = spendingViewModel.currentMonth.collectAsStateWithLifecycle().value,
        spendingState = spendingViewModel.analysis.collectAsStateWithLifecycle().value,
        moneyFlowOverview = moneyFlowViewModel.lastTwelveMonths.collectAsStateWithLifecycle().value.valueOrNull,
        moneyFlowState = moneyFlowViewModel.flow.collectAsStateWithLifecycle().value,
    )
    val backStack = rememberNavBackStack(AnalysisKey.Home)
    // The entries read the latest values, whenever NavDisplay creates their content
    val current by rememberUpdatedState(content)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<AnalysisKey.Home> { AnalysisHome(current, backStack) }
            entry<AnalysisKey.Gap> { key -> GapEntry(current, backStack, key) }
            entry<AnalysisKey.Tag> { key -> TagEntry(current, backStack, key) }
            entry<AnalysisKey.TagTransactions> { key -> TagTransactionsEntry(current, backStack, key) }
            entry<AnalysisKey.Spending> { SpendingEntry(current, backStack) }
            entry<AnalysisKey.Category> { key -> CategoryEntry(current, backStack, key) }
            entry<AnalysisKey.CategoryTransactions> { key -> CategoryTransactionsEntry(current, backStack, key) }
            entry<AnalysisKey.MoneyFlow> { MoneyFlowEntry(current, backStack) }
            entry<AnalysisKey.FlowTransactions> { key ->
                Transactions(current, key.title, key.ids) { backStack.remove(key) }
            }
        }
    )
}

/** What the pages on the back stack of [AnalysisFlow] need */
private class AnalysisContent(
    val viewModel: TagAnalysisViewModel,
    val spendingViewModel: CategorySpendingViewModel,
    val moneyFlowViewModel: MoneyFlowViewModel,
    val fixedTransactions: FixedTransactions,
    val currency: CurrencyInfo,
    val onInsightSettings: () -> Unit,
    val transactionDetails: (@Composable (ShownTransaction) -> Unit)?,
    /** What the analysis still lacks, null while it is not known */
    val gaps: List<DataGap>?,
    val state: AnalysisState<TagAnalysis>,
    val currentMonth: AnalysisState<CategoryAnalysis>,
    val spendingState: AnalysisState<CategoryAnalysis>,
    val moneyFlowOverview: MoneyFlow?,
    val moneyFlowState: AnalysisState<MoneyFlow>,
) {
    val analysis = state.valueOrNull
    val spending = spendingState.valueOrNull
    val moneyFlow = moneyFlowState.valueOrNull

    /** Looked up in the current analysis, so that it follows changes, e.g. of the year */
    fun evaluationOf(tagId: Long) =
        analysis?.let { (it.trips + it.themes + it.business).find { evaluation -> evaluation.tagId == tagId } }

    /** Looked up in the period of [spending] */
    fun categoryOf(key: Long) = spending?.categories?.find { it.key == key }
}

private val AnalysisState<*>.isLoaded get() = this != AnalysisState.Loading

@Composable
private fun Transactions(content: AnalysisContent, title: String, ids: List<Long>, onBack: () -> Unit) {
    LaunchedEffect(ids) { content.fixedTransactions.show(title, ids) }
    FixedTransactionsScreen(
        title = title,
        subtitle = pluralStringResource(R.plurals.next_analysis_transactions, ids.size, ids.size),
        onBack = onBack
    ) {
        content.fixedTransactions.List(it, content.transactionDetails)
    }
}

@Composable
private fun GapEntry(content: AnalysisContent, backStack: NavBackStack<NavKey>, key: AnalysisKey.Gap) {
    // Looked up in the current gaps: once fixed, the gap and its list go away
    val gap = shownOrLeave(content.gaps?.find { it.key == key.key }, isLoaded = content.gaps != null) {
        backStack.remove(key)
    } ?: return
    Transactions(content, gapTitle(gap), gap.transactionIds) { backStack.remove(key) }
}

@Composable
private fun TagEntry(content: AnalysisContent, backStack: NavBackStack<NavKey>, key: AnalysisKey.Tag) {
    val evaluation = shownOrLeave(content.evaluationOf(key.tagId), isLoaded = content.state.isLoaded) {
        backStack.remove(key)
    } ?: return
    val analysis = content.analysis ?: return
    TagEvaluationScreen(
        evaluation = evaluation,
        currency = content.currency,
        onBack = { backStack.remove(key) },
        onShowTransactions = { backStack.add(AnalysisKey.TagTransactions(key.tagId)) },
        years = analysis.businessYears,
        year = analysis.year,
        onSelectYear = if (evaluation.group == TagGroup.BUSINESS) content.viewModel::selectYear else null
    )
}

@Composable
private fun TagTransactionsEntry(content: AnalysisContent, backStack: NavBackStack<NavKey>, key: AnalysisKey.TagTransactions) {
    val evaluation = shownOrLeave(content.evaluationOf(key.tagId), isLoaded = content.state.isLoaded) {
        backStack.remove(key)
    } ?: return
    Transactions(content, evaluation.label, evaluation.transactionIds) { backStack.remove(key) }
}

@Composable
private fun SpendingEntry(content: AnalysisContent, backStack: NavBackStack<NavKey>) {
    val leave = { backStack -= AnalysisKey.Spending }
    val spending = shownOrLeave(content.spending, isLoaded = content.spendingState.isLoaded, onGone = leave) ?: return
    val spendingViewModel = content.spendingViewModel
    CategorySpendingScreen(
        analysis = spending,
        currency = content.currency,
        onBack = leave,
        onSelectKind = spendingViewModel::selectKind,
        onEarlier = spendingViewModel::showEarlier,
        onLater = spendingViewModel::showLater,
        onOpenCategory = { backStack.add(AnalysisKey.Category(it)) }
    )
}

@Composable
private fun CategoryEntry(content: AnalysisContent, backStack: NavBackStack<NavKey>, key: AnalysisKey.Category) {
    val category = shownOrLeave(content.categoryOf(key.key), isLoaded = content.spendingState.isLoaded) {
        backStack.remove(key)
    } ?: return
    val spending = content.spending ?: return
    CategoryDetailScreen(
        analysis = spending,
        category = category,
        currency = content.currency,
        onBack = { backStack.remove(key) },
        onShowTransactions = { backStack.add(AnalysisKey.CategoryTransactions(key.key)) }
    )
}

@Composable
private fun CategoryTransactionsEntry(content: AnalysisContent, backStack: NavBackStack<NavKey>, key: AnalysisKey.CategoryTransactions) {
    val category = shownOrLeave(content.categoryOf(key.key), isLoaded = content.spendingState.isLoaded) {
        backStack.remove(key)
    } ?: return
    Transactions(content, category.label ?: stringResource(R.string.next_analysis_no_category), category.transactionIds) {
        backStack.remove(key)
    }
}

@Composable
private fun MoneyFlowEntry(content: AnalysisContent, backStack: NavBackStack<NavKey>) {
    val leave = { backStack -= AnalysisKey.MoneyFlow }
    val moneyFlow = shownOrLeave(content.moneyFlow, isLoaded = content.moneyFlowState.isLoaded, onGone = leave) ?: return
    val moneyFlowViewModel = content.moneyFlowViewModel
    MoneyFlowScreen(
        flow = moneyFlow,
        currency = content.currency,
        onBack = leave,
        onSelectKind = moneyFlowViewModel::selectKind,
        onEarlier = moneyFlowViewModel::showEarlier,
        onLater = moneyFlowViewModel::showLater,
        onShowTransactions = { title, ids -> backStack.add(AnalysisKey.FlowTransactions(title, ids)) }
    )
}

@Composable
private fun AnalysisHome(content: AnalysisContent, backStack: NavBackStack<NavKey>) {
    val spendingViewModel = content.spendingViewModel
    AnalysisScreen(
        state = content.state,
        spending = content.currentMonth.valueOrNull,
        currency = content.currency,
        moneyFlow = content.moneyFlowOverview,
        onConsent = { content.viewModel.ledgerSource.setConsent(true) },
        onSettings = content.onInsightSettings,
        onOpen = { backStack.add(AnalysisKey.Tag(it.tagId)) },
        onOpenCategory = {
            // The overview shows the current month, so does the category
            spendingViewModel.showCurrentMonth()
            backStack.add(AnalysisKey.Category(it))
        },
        onOpenSpending = {
            spendingViewModel.showCurrentMonth()
            backStack.add(AnalysisKey.Spending)
        },
        onOpenMoneyFlow = {
            content.moneyFlowViewModel.showLastTwelveMonths()
            backStack.add(AnalysisKey.MoneyFlow)
        },
        gaps = content.gaps.orEmpty(),
        onShowGap = { backStack.add(AnalysisKey.Gap(it.key)) }
    )
}
