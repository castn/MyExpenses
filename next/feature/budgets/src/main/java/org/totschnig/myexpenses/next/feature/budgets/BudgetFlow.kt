package org.totschnig.myexpenses.next.feature.budgets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.totschnig.myexpenses.next.core.ui.DetailPage
import org.totschnig.myexpenses.next.core.ui.ExtraPage
import org.totschnig.myexpenses.next.core.ui.ListPage
import org.totschnig.myexpenses.next.core.ui.rememberListDetailPages
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.shownOrLeave
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.core.ui.transactions.FixedTransactionsScreen
import org.totschnig.myexpenses.next.core.ui.transactions.FixedTransactions
import org.totschnig.myexpenses.next.data.budgets.BudgetOverview
import org.totschnig.myexpenses.next.data.budgets.Pot
import org.totschnig.myexpenses.next.data.budgets.PotKind
import org.totschnig.myexpenses.next.data.budgets.PotTemplate
import org.totschnig.myexpenses.next.data.budgets.parsePots
import org.totschnig.myexpenses.next.data.budgets.serializePots

/** The pages of the budget, the budget of a period at the bottom of the back stack */
@Serializable
sealed interface BudgetKey : NavKey {
    @Serializable
    data object Home : BudgetKey

    /** @param isNew the pot was just saved and is not in the budget yet */
    @Serializable
    data class PotDetail(val id: String, val isNew: Boolean = false) : BudgetKey

    /** @param oneOffs the one-off expenses left out of the pot instead of the ones it counts */
    @Serializable
    data class Transactions(val potId: String, val oneOffs: Boolean) : BudgetKey

    /** The templates to start a new pot from */
    @Serializable
    data object Templates : BudgetKey

    /**
     * The editor of a pot; the pot being changed is state of the editor, not part of the key
     *
     * @param potId null for a new pot, which starts from [template]
     */
    @Serializable
    data class Editor(val potId: String?, val template: PotTemplate? = null) : BudgetKey
}

/** The pot being created or changed, kept across configuration changes as JSON */
private val PotSaver = Saver<Pot?, String>(
    save = { pot -> pot?.let { serializePots(listOf(it)) } },
    restore = { saved -> parsePots(saved).firstOrNull() }
)

/**
 * The pages of the budget: the budget of a period, one pot and its transactions, and the editor
 * of a pot, on a back stack of their own ([BudgetKey])
 *
 * @param onBack leaves the budget with the arrow of its top bar; back itself is handled by the back
 * stack of the shell, on which the budget is a page
 * @param openPot id of a pot shown from the start, e.g. tapped in the overview
 * @param addPot whether to start with adding a pot, asked once
 * @param requestNewPot asks whether the user may create a pot, e.g. by checking the licence, and
 * then calls [BudgetViewModel.grantNewPot]
 */
@Composable
fun BudgetFlow(
    viewModel: BudgetViewModel,
    fixedTransactions: FixedTransactions,
    currency: CurrencyInfo,
    onBack: () -> Unit,
    onConsent: () -> Unit,
    modifier: Modifier = Modifier,
    openPot: String? = null,
    addPot: Boolean = false,
    requestNewPot: () -> Unit = viewModel::grantNewPot,
) {
    val state by viewModel.overview.collectAsStateWithLifecycle()
    val editorData by viewModel.editorData.collectAsStateWithLifecycle()
    val legacyCount = viewModel.legacyOffer.collectAsStateWithLifecycle().value
    val backStack = rememberNavBackStack(
        *listOfNotNull<NavKey>(BudgetKey.Home, openPot?.let { BudgetKey.PotDetail(it) }).toTypedArray()
    )
    LaunchedEffect(Unit) { viewModel.showCurrent() }
    // Asked once, not again when the page is created anew, e.g. after rotating
    var addPotAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (addPot && !addPotAsked) {
            addPotAsked = true
            requestNewPot()
        }
    }
    // Granted also when the activity was created anew meanwhile
    val newPotGranted by viewModel.newPotGranted.collectAsStateWithLifecycle()
    LaunchedEffect(newPotGranted) {
        if (newPotGranted) {
            backStack.add(BudgetKey.Templates)
            viewModel.newPotOpened()
        }
    }
    // The entries read the latest values, whenever NavDisplay creates their content
    val current by rememberUpdatedState(
        BudgetContent(viewModel, fixedTransactions, currency, state, editorData, legacyCount, onBack, onConsent, requestNewPot)
    )
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        sceneStrategies = listOf(rememberListDetailPages()),
        entryProvider = entryProvider {
            entry<BudgetKey.Home>(metadata = ListPage(R.string.next_pick_pot)) { BudgetHome(current, backStack) }
            entry<BudgetKey.PotDetail>(metadata = DetailPage()) { key -> PotEntry(current, backStack, key) }
            entry<BudgetKey.Transactions>(metadata = ExtraPage()) { key -> PotTransactionsEntry(current, backStack, key) }
            entry<BudgetKey.Templates>(metadata = DetailPage()) { TemplatesEntry(current, backStack) }
            entry<BudgetKey.Editor>(metadata = DetailPage()) { key -> EditorEntry(current, backStack, key) }
        }
    )
}

/** What the pages on the back stack of [BudgetFlow] need */
private class BudgetContent(
    val viewModel: BudgetViewModel,
    val fixedTransactions: FixedTransactions,
    val currency: CurrencyInfo,
    val state: AnalysisState<BudgetOverview>,
    val editorData: PotEditorData?,
    val legacyCount: Int,
    val onBack: () -> Unit,
    val onConsent: () -> Unit,
    val requestNewPot: () -> Unit,
) {
    val overview = state.valueOrNull

    /** Looked up in the current state, so that it follows changes, e.g. of the period */
    fun statusOf(potId: String) = overview?.pots?.find { it.pot.id == potId }
}

/** The pot of the page, the page is left once it is gone, e.g. deleted or not in the period shown */
@Composable
private fun shownPot(content: BudgetContent, backStack: NavBackStack<NavKey>, key: BudgetKey, potId: String, isNew: Boolean = false) =
    shownOrLeave(content.statusOf(potId), isLoaded = content.overview != null, awaitNew = isNew) { backStack.remove(key) }

@Composable
private fun BudgetHome(content: BudgetContent, backStack: NavBackStack<NavKey>) {
    val viewModel = content.viewModel
    BudgetScreen(
        state = content.state,
        currency = content.currency,
        onBack = content.onBack,
        onEarlier = viewModel::showEarlier,
        onLater = viewModel::showLater,
        onOpenPot = { backStack.add(BudgetKey.PotDetail(it)) },
        onConsent = content.onConsent,
        onAddPot = content.requestNewPot,
        legacyCount = content.legacyCount,
        onImportLegacy = viewModel::importLegacy,
        onDismissLegacy = viewModel::dismissLegacy
    )
}

@Composable
private fun PotEntry(content: BudgetContent, backStack: NavBackStack<NavKey>, key: BudgetKey.PotDetail) {
    val status = shownPot(content, backStack, key, key.id, key.isNew) ?: return
    val overview = content.overview ?: return
    PotDetailScreen(
        overview = overview,
        status = status,
        currency = content.currency,
        onBack = { backStack.remove(key) },
        onShowTransactions = { backStack.add(BudgetKey.Transactions(key.id, oneOffs = false)) },
        onShowOneOffs = { backStack.add(BudgetKey.Transactions(key.id, oneOffs = true)) },
        onEdit = { backStack.add(BudgetKey.Editor(key.id)) }
    )
}

@Composable
private fun PotTransactionsEntry(content: BudgetContent, backStack: NavBackStack<NavKey>, key: BudgetKey.Transactions) {
    val status = shownPot(content, backStack, key, key.potId) ?: return
    val ids = if (key.oneOffs) status.oneOffIds else status.transactionIds
    val title = if (key.oneOffs) pluralStringResource(R.plurals.next_budget_one_offs, ids.size, ids.size) else status.pot.name
    LaunchedEffect(ids) { content.fixedTransactions.show(title, ids) }
    FixedTransactionsScreen(
        title = title,
        subtitle = if (key.oneOffs) status.pot.name else pluralStringResource(R.plurals.next_analysis_transactions, ids.size, ids.size),
        onBack = { backStack.remove(key) }
    ) {
        content.fixedTransactions.List(it, null)
    }
}

@Composable
private fun TemplatesEntry(content: BudgetContent, backStack: NavBackStack<NavKey>) {
    PotTemplateScreen(
        categories = content.editorData?.categories.orEmpty(),
        kinds = content.editorData?.kinds ?: PotKind.entries,
        onPick = { backStack.add(BudgetKey.Editor(potId = null, template = it)) },
        onBack = { backStack.remove(BudgetKey.Templates) }
    )
}

@Composable
private fun EditorEntry(content: BudgetContent, backStack: NavBackStack<NavKey>, key: BudgetKey.Editor) {
    val data = content.editorData
    val resources = LocalResources.current
    val existing = key.potId?.let { content.statusOf(it)?.pot }
    val initial = remember(key, data?.categories, existing) {
        if (key.potId != null) existing
        else data?.let {
            val name = key.template?.let { resources.getString(it.nameRes) } ?: ""
            newPot(key.template, name, it.categories, java.time.LocalDate.now())
        }
    }
    var draft by rememberSaveable(stateSaver = PotSaver) { mutableStateOf<Pot?>(null) }
    val pot = draft ?: initial
    if (data == null || pot == null) {
        CircularProgressIndicator(Modifier.fillMaxSize().wrapContentSize())
        return
    }
    fun backToBudget() = backStack.retainAll { it == BudgetKey.Home }
    PotEditorScreen(
        pot = pot,
        data = data,
        currency = content.currency,
        onChange = { draft = it },
        onSave = {
            content.viewModel.savePot(pot)
            backToBudget()
            backStack.add(BudgetKey.PotDetail(pot.id, isNew = key.potId == null))
        },
        onBack = { backStack.remove(key) },
        onDelete = if (key.potId == null) null else {
            {
                content.viewModel.deletePot(pot.id)
                backToBudget()
            }
        }
    )
}
