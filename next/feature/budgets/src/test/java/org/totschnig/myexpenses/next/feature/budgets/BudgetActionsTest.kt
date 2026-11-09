package org.totschnig.myexpenses.next.feature.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.data.budgets.BudgetRepository
import org.totschnig.myexpenses.next.data.budgets.BudgetStore
import org.totschnig.myexpenses.next.data.budgets.LegacyBudget
import org.totschnig.myexpenses.next.data.budgets.LegacyPeriod
import org.totschnig.myexpenses.next.data.budgets.Pot
import org.totschnig.myexpenses.next.data.budgets.PotKind
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.data.tags.TagFacts

/** The actions of [BudgetViewModel] and what the user sees after them */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val giro = 1L
    private val savings = 2L
    private val today = LocalDate.of(2026, 11, 9)
    private val food = CategoryInfo(10, "Essen", "Essen")
    private val travel = CategoryInfo(11, "Reisen", "Freizeit:Reisen")
    private val salary = CategoryInfo(12, "Gehalt", "Gehalt", isExpense = false)
    private val catalog = Catalog(listOf(travel, salary, food), listOf(TagInfo(5, "Urlaub")))
    private val accounts = OwnAccounts(setOf(giro), mapOf(giro to "Giro", savings to "Tagesgeld"))
    private val facts =
        ContractFacts(emptyList(), emptyList(), emptyList(), emptySet(), emptyList())
    private val foodPot = Pot(
        id = "food",
        name = "Essen",
        kind = PotKind.RUNNING,
        amount = 40000,
        categoryIds = setOf(food.id)
    )
    private val travelPot = Pot(
        id = "travel",
        name = "Reisen",
        kind = PotKind.YEARLY,
        amount = 100000,
        categoryIds = setOf(travel.id)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val viewModel: BudgetViewModel,
        val store: BudgetStore,
        val source: FakeLedgerSource
    )

    private fun withViewModel(
        source: FakeLedgerSource = FakeLedgerSource(
            ledger = Ledger(emptyList(), accounts),
            catalog = catalog,
            today = today
        ),
        legacyCount: Flow<Int> = flowOf(0),
        loadLegacy: suspend () -> List<LegacyBudget> = { emptyList() },
        receiptFacts: Flow<ReceiptFacts?> = flowOf(null),
        block: suspend Fixture.() -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val store = BudgetStore(dataStore)
            val viewModel = BudgetViewModel(
                BudgetRepository(
                    source,
                    MutableStateFlow(facts),
                    MutableStateFlow(TagFacts()),
                    store,
                    backgroundScope,
                    receiptFacts
                ),
                homeCurrency = MutableStateFlow(CurrencyInfo.Preview),
                legacyCount = legacyCount,
                loadLegacy = loadLegacy,
                savedState = SavedStateHandle(),
                compute = dispatcher
            )
            // Like a shown screen
            backgroundScope.launch { viewModel.pots.collect {} }
            backgroundScope.launch { viewModel.overview.collect {} }
            backgroundScope.launch { viewModel.editorData.collect {} }
            backgroundScope.launch { viewModel.legacyOffer.collect {} }
            backgroundScope.launch { viewModel.inviteDismissed.collect {} }
            try {
                Fixture(viewModel, store, source).block()
            } finally {
                // Its coroutines must not outlive the test dispatcher set as Main
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    private suspend fun Fixture.potsWhere(condition: (List<Pot>) -> Boolean) =
        viewModel.pots.first { it != null && condition(it) }!!

    @Test
    fun savesReplacesReordersAndDeletesPots() = withViewModel {
        assertEquals(emptyList<Pot>(), potsWhere { true })
        viewModel.savePot(foodPot)
        viewModel.savePot(travelPot)
        assertEquals(listOf(foodPot, travelPot), potsWhere { it.size == 2 })

        // Saved again after editing: stays in its place
        val edited = foodPot.copy(amount = 50000)
        viewModel.savePot(edited)
        assertEquals(listOf(edited, travelPot), potsWhere { it.first().amount == 50000L })

        viewModel.reorderPots(listOf(travelPot.id, foodPot.id))
        assertEquals(listOf(travelPot, edited), potsWhere { it.first().id == travelPot.id })
        assertEquals(listOf(travelPot, edited), store.pots.first())

        viewModel.deletePot(foodPot.id)
        assertEquals(listOf(travelPot), potsWhere { it.size == 1 })
        assertEquals(listOf(travelPot), store.pots.first())
    }

    @Test
    fun reorderKeepsPotsNotListedAtTheEnd() = withViewModel {
        val other = foodPot.copy(id = "other", name = "Sonstiges")
        viewModel.savePot(foodPot)
        viewModel.savePot(travelPot)
        viewModel.savePot(other)
        potsWhere { it.size == 3 }
        viewModel.reorderPots(listOf(other.id))
        assertEquals(listOf(other, foodPot, travelPot), potsWhere { it.first().id == other.id })
    }

    @Test
    fun offersLegacyBudgetsUntilDismissed() = withViewModel(legacyCount = flowOf(2)) {
        assertEquals(2, viewModel.legacyOffer.first { it != 0 })
        viewModel.dismissLegacy()
        assertEquals(0, viewModel.legacyOffer.first { it == 0 })
        assertEquals(true, store.legacyHandled.first())
        // Nothing was taken over
        assertEquals(emptyList<Pot>(), store.pots.first())
    }

    @Test
    fun noLegacyBudgetsNoOffer() = withViewModel(legacyCount = flowOf(0)) {
        potsWhere { true }
        assertEquals(0, viewModel.legacyOffer.value)
    }

    @Test
    fun importsLegacyBudgetsInTheHomeCurrencyAfterTheExistingPots() {
        val monthly = LegacyBudget(
            title = "Haushalt",
            currency = "EUR",
            grouping = LegacyPeriod.MONTH,
            start = null,
            end = null,
            total = 0,
            categoryAllocations = mapOf(food.id to 30000L)
        )
        val dollars = monthly.copy(title = "USA", currency = "USD")
        withViewModel(legacyCount = flowOf(2), loadLegacy = { listOf(monthly, dollars) }) {
            viewModel.savePot(travelPot)
            potsWhere { it.size == 1 }
            assertEquals(2, viewModel.legacyOffer.first { it != 0 })

            viewModel.importLegacy()

            val pots = potsWhere { it.size == 2 }
            assertEquals(travelPot, pots.first())
            val imported = pots.last()
            assertEquals("Essen", imported.name)
            assertEquals(PotKind.RUNNING, imported.kind)
            assertEquals(30000L, imported.amount)
            assertEquals(setOf(food.id), imported.categoryIds)
            assertEquals(today, imported.start)
            assertEquals(0, viewModel.legacyOffer.first { it == 0 })
            assertEquals(true, store.legacyHandled.first())
        }
    }

    @Test
    fun invitationShowsUntilDismissed() = withViewModel {
        assertEquals(false, viewModel.inviteDismissed.first { !it })
        viewModel.dismissInvite()
        assertEquals(true, viewModel.inviteDismissed.first { it })
        assertEquals(true, store.inviteDismissed.first())
    }

    @Test
    fun overviewFollowsTheConsent() = withViewModel(
        source = FakeLedgerSource(consent = null, ledger = null, catalog = catalog, today = today)
    ) {
        viewModel.overview.first { it == AnalysisState.AskConsent }
        source.setConsent(false)
        viewModel.overview.first { it == AnalysisState.Declined }
        source.setConsent(true)
        // Still no ledger
        viewModel.overview.first { it == AnalysisState.Loading }
        assertNull(viewModel.editorData.value)
        source.ledger.value = Ledger(emptyList(), accounts)
        viewModel.overview.first { it is AnalysisState.Ready }
    }

    @Test
    fun laterThanTheCurrentPeriodIsNotShown() = withViewModel {
        viewModel.overview.first { (it as? AnalysisState.Ready)?.value?.offset == 0 }
        viewModel.showLater()
        viewModel.showEarlier()
        viewModel.overview.first { (it as? AnalysisState.Ready)?.value?.offset == 1 }
    }

    @Test
    fun editorOffersExpenseCategoriesTagsAndSavingAccounts() = withViewModel {
        val data = viewModel.editorData.first { it != null }!!
        assertEquals(listOf(food, travel), data.categories)
        assertEquals(catalog.tags, data.tags)
        assertEquals(mapOf(savings to "Tagesgeld"), data.savingAccounts)
        assertEquals(PotKind.entries, data.kinds)
        assertEquals(emptyList<Any>(), data.contracts)
        assertEquals(emptyList<Any>(), data.reserves)
    }

    @Test
    fun editorLeavesOutItemGroupsWithoutReceipts() =
        withViewModel(receiptFacts = flowOf(ReceiptFacts.None)) {
            val data = viewModel.editorData.first { it != null }!!
            assertEquals(PotKind.entries - PotKind.ITEM_GROUP, data.kinds)
        }

    @Test
    fun findsThePotATransactionIsPaidFromBeforeAPotOfItemGroups() {
        val purchase =
            LedgerTransaction(7, today, amount = -2000, accountId = giro, categoryId = food.id)
        val other = LedgerTransaction(8, today, amount = -500, accountId = giro)
        val sweets = Pot(
            id = "sweets",
            name = "Süßes",
            kind = PotKind.ITEM_GROUP,
            itemGroups = setOf("sweets"),
            maxSharePercent = 10
        )
        val chocolate = ScannedItem("Schokolade", 2000, "sweets")
        val receipts = ReceiptFacts(mapOf(purchase.id to listOf(chocolate)))
        withViewModel(
            source = FakeLedgerSource(
                ledger = Ledger(listOf(purchase, other), accounts),
                catalog = catalog,
                today = today
            ),
            receiptFacts = flowOf(receipts)
        ) {
            // The pot of item groups comes first in the list
            viewModel.savePot(sweets)
            viewModel.savePot(foodPot)
            potsWhere { it.size == 2 }
            viewModel.current.first { overview ->
                overview?.pots?.size == 2 && overview.pots.all { purchase.id in it.transactionIds }
            }
            assertEquals(foodPot, viewModel.potOf(purchase.id).first()?.pot)
            assertNull(viewModel.potOf(other.id).first())

            viewModel.deletePot(foodPot.id)
            viewModel.current.first { it?.pots?.size == 1 }
            assertEquals(sweets, viewModel.potOf(purchase.id).first()?.pot)
        }
    }
}
