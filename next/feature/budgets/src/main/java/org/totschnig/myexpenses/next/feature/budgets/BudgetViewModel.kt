package org.totschnig.myexpenses.next.feature.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.ledger.Memo
import org.totschnig.myexpenses.next.core.model.ledger.analysisState
import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo
import org.totschnig.myexpenses.next.data.budgets.BudgetAnalyzer
import org.totschnig.myexpenses.next.data.budgets.BudgetInput
import org.totschnig.myexpenses.next.data.budgets.BudgetOverview
import org.totschnig.myexpenses.next.data.budgets.BudgetRepository
import org.totschnig.myexpenses.next.data.budgets.BudgetStore
import org.totschnig.myexpenses.next.data.budgets.LegacyBudget
import org.totschnig.myexpenses.next.data.budgets.LegacyBudgets
import org.totschnig.myexpenses.next.data.budgets.Pot
import org.totschnig.myexpenses.next.data.budgets.PotKind
import org.totschnig.myexpenses.next.data.budgets.PotStatus
import org.totschnig.myexpenses.next.data.budgets.PotSuggestions

/**
 * What the user can do with the pots of the budget
 */
interface BudgetActions {
    fun savePot(pot: Pot)
    fun deletePot(id: String)
    fun reorderPots(ids: List<String>)

    /** Takes over the budgets of the classic screens as pots */
    fun importLegacy()

    /** Does not offer the budgets of the classic screens again */
    fun dismissLegacy()
}

/**
 * The budget for the screens: the current period of [repository] for the overview, the period
 * picked by the user for the budget page, and what the user does with the pots.
 *
 * @param homeCurrency the currency all amounts are in, which the user can change
 * @param legacyCount number of budgets of the classic screens
 * @param loadLegacy reads them for [importLegacy]
 * @param savedState keeps [newPotGranted] when Android ends the process, e.g. during the licence check
 */
class BudgetViewModel(
    private val repository: BudgetRepository,
    private val homeCurrency: StateFlow<CurrencyInfo> = MutableStateFlow(CurrencyInfo.Preview),
    legacyCount: Flow<Int> = flowOf(0),
    private val loadLegacy: suspend () -> List<LegacyBudget> = { emptyList() },
    private val savedState: SavedStateHandle,
) : ViewModel(), BudgetActions {

    /** The user may create a pot, e.g. after the licence was checked, until its editor is opened */
    val newPotGranted: StateFlow<Boolean> = savedState.getStateFlow(KEY_NEW_POT_GRANTED, false)

    fun grantNewPot() {
        savedState[KEY_NEW_POT_GRANTED] = true
    }

    fun newPotOpened() {
        savedState[KEY_NEW_POT_GRANTED] = false
    }

    private val ledgerSource: LedgerSource
        get() = repository.ledgerSource

    private val store: BudgetStore
        get() = repository.store

    val pots: StateFlow<List<Pot>?> = store.pots.stateWhileShown(viewModelScope, null)

    private val offset = MutableStateFlow(0)

    internal val selectedMemo = Memo<BudgetInput, BudgetOverview>("budget: selected", BudgetAnalyzer::analyse)

    private val input: Flow<BudgetInput?>
        get() = repository.input

    /** See [BudgetRepository.current] */
    val current: SharedFlow<BudgetOverview?>
        get() = repository.current

    /** The period the user picked on the budget page */
    val overview: StateFlow<AnalysisState<BudgetOverview>> =
        combine(ledgerSource.consent, input, offset) { consent, input, offset ->
            analysisState(consent, input?.let { selectedMemo.of(it.copy(offset = offset)) })
        }
            .logTimings("budget: selected")
            .flowOn(Dispatchers.Default)
            .stateWhileShown(viewModelScope, AnalysisState.Loading)

    /**
     * The pot [transactionId] is paid from in the current period, null if it is in none. Pots of
     * item groups only watch, so the pot that holds the money comes first.
     */
    fun potOf(transactionId: Long): Flow<PotStatus?> = current.map { overview ->
        overview?.pots
            ?.filter { transactionId in it.transactionIds || transactionId in it.oneOffIds }
            ?.minByOrNull { if (it.pot.kind == PotKind.ITEM_GROUP) 1 else 0 }
    }

    /** What the editor of a pot offers to choose from, null while loading or without consent */
    val editorData: StateFlow<PotEditorData?> =
        combine(input, ledgerSource.catalog, repository.canScanReceipts) { input, catalog, canScan ->
            input?.let { PotEditorData.of(it, catalog, canScan) }
        }
            .flowOn(Dispatchers.Default)
            .stateWhileShown(viewModelScope, null)

    /** Budgets of the classic screens to offer to take over, 0 if there are none or they were handled */
    val legacyOffer: StateFlow<Int> = combine(legacyCount, store.legacyHandled) { count, handled -> if (handled) 0 else count }
        .stateWhileShown(viewModelScope, 0)

    /** Whether the user closed the invitation to a first budget, true until known so it does not flash */
    val inviteDismissed: StateFlow<Boolean> = store.inviteDismissed.stateWhileShown(viewModelScope, true)

    fun dismissInvite() {
        viewModelScope.launch { store.dismissInvite() }
    }

    override fun importLegacy() {
        viewModelScope.launch {
            val pots = LegacyBudgets.pots(
                loadLegacy(), homeCurrency.value.code, ledgerSource.catalog.first().categoriesById, ledgerSource.today.first()
            )
            store.addFromLegacy(pots)
        }
    }

    override fun dismissLegacy() {
        viewModelScope.launch { store.setLegacyHandled() }
    }

    fun showCurrent() {
        offset.value = 0
    }

    fun showEarlier() {
        offset.update { it + 1 }
    }

    fun showLater() {
        offset.update { (it - 1).coerceAtLeast(0) }
    }

    override fun savePot(pot: Pot) {
        viewModelScope.launch { store.save(pot) }
    }

    override fun deletePot(id: String) {
        viewModelScope.launch { store.delete(id) }
    }

    override fun reorderPots(ids: List<String>) {
        viewModelScope.launch { store.reorder(ids) }
    }

    private companion object {
        const val KEY_NEW_POT_GRANTED = "newPotGranted"
    }
}

/**
 * What the editor of a pot offers to choose from
 *
 * @param input for the suggestions, see [PotSuggestions]
 * @param categories for expenses, by path
 * @param contracts confirmed expense contracts that are paid now, without reserves
 * @param reserves confirmed reserve contracts, which a goal can be saved with
 * @param savingAccounts own accounts that are not for daily use, by id, with their label
 * @param kinds of pots that can be chosen, without those of item groups when receipts cannot be scanned
 */
data class PotEditorData(
    val input: BudgetInput,
    val categories: List<CategoryInfo>,
    val tags: List<TagInfo>,
    val contracts: List<Contract>,
    val reserves: List<Contract>,
    val savingAccounts: Map<Long, String>,
    val kinds: List<PotKind> = PotKind.entries,
) {
    companion object {
        fun of(input: BudgetInput, catalog: Catalog, canScanReceipts: Boolean = true): PotEditorData {
            val (reserves, contracts) = input.contracts
                .filter { !it.isIncome && it.rule != null && !it.isCancelled }
                .partition { it.isReserve }
            val accounts = input.ledger.ownAccounts
            return PotEditorData(
                input = input,
                categories = catalog.categories.filter { it.isExpense }.sortedBy { it.path.lowercase() },
                tags = catalog.tags,
                contracts = contracts.sortedBy { it.title.lowercase() },
                reserves = reserves.sortedBy { it.title.lowercase() },
                savingAccounts = accounts.labels.filterKeys { it !in accounts.dailyIds },
                kinds = if (canScanReceipts) PotKind.entries else PotKind.entries - PotKind.ITEM_GROUP
            )
        }
    }
}
