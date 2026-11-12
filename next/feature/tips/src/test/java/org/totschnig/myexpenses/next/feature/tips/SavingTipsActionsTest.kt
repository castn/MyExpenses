package org.totschnig.myexpenses.next.feature.tips

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.data.budgets.BudgetFacts
import org.totschnig.myexpenses.next.data.budgets.Pot
import org.totschnig.myexpenses.next.data.budgets.PotKind
import org.totschnig.myexpenses.next.data.budgets.PotStatus
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractDirection
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.data.tags.TagFacts
import org.totschnig.myexpenses.next.data.tips.PotsOverspent
import org.totschnig.myexpenses.next.data.tips.SavingTip
import org.totschnig.myexpenses.next.data.tips.SavingTipsOverview
import org.totschnig.myexpenses.next.data.tips.SavingTipsRepository
import org.totschnig.myexpenses.next.data.tips.TipDecisions
import org.totschnig.myexpenses.next.data.tips.TipStore

/** Which tips the user sees after each input and what dismissing, restoring and seeing store */
@OptIn(ExperimentalCoroutinesApi::class)
class SavingTipsActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val today = LocalDate.of(2026, 10, 26)

    /** Ten payments of 13.99 and then four of 17.99, the last one three days ago */
    private val contract: Contract = run {
        val last = today.minusDays(3)
        val amounts = List(10) { 1399L } + List(4) { 1799L }
        Contract(
            signature = "r:Contract",
            name = "Contract",
            interval = ContractInterval.MONTHLY,
            transactions = amounts.mapIndexed { i, amount ->
                ContractTransaction(
                    id = i + 1L,
                    date = last.minusMonths(amounts.size - 1L - i),
                    amount = -amount,
                    accountId = 1
                )
            },
            nextExpectedDate = last.plusMonths(1),
            isActive = true,
            direction = ContractDirection.EXPENSE,
            isConfirmed = true
        )
    }

    private val contractFacts = ContractFacts(
        active = listOf(contract),
        confirmed = listOf(contract),
        salaries = emptyList(),
        paymentIds = contract.transactions.mapTo(HashSet()) { it.id },
        transactions = contract.transactions
    )

    private val priceIncreasesId = SavingTip.PriceIncreases.ID

    /** A food pot of 400 overspent in each of the last [periods] periods by 100 */
    private fun budget(periods: Int) = BudgetFacts(
        listOf(
            PotStatus(
                Pot(id = "food", name = "Food", kind = PotKind.RUNNING, amount = 40000),
                planned = 40000,
                history = List(PotsOverspent.PERIODS - periods) { 30000L } +
                    List(periods) { 50000L }
            )
        )
    )

    private class Fixture(
        val viewModel: SavingTipsViewModel,
        val store: TipStore,
        val source: FakeLedgerSource,
        val contracts: MutableStateFlow<ContractFacts?>,
        val budget: MutableStateFlow<BudgetFacts?>
    ) {
        /** Lets the coroutines the view model launched write to the store, if they write at all */
        suspend fun flush(): TipDecisions {
            withContext(Dispatchers.Default) { delay(200) }
            return store.decisions.first()
        }

        suspend fun overview(predicate: (SavingTipsOverview) -> Boolean): SavingTipsOverview {
            val state = awaitValue(viewModel.savingTips) {
                it is AnalysisState.Ready && predicate(it.value)
            }
            return (state as AnalysisState.Ready).value
        }

        /** Both tips: the first state may lack the tip of the budget, which need not be waited for */
        suspend fun bothTips() = overview { it.tips.size + it.dismissed.size == 2 }

        suspend fun tip(id: String) = overview { tips -> tips.tips.any { it.id == id } }
            .tips.single { it.id == id }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withViewModel(
        consent: Boolean = true,
        contracts: ContractFacts? = contractFacts,
        budget: BudgetFacts? = budget(PotsOverspent.PERIODS),
        block: suspend Fixture.() -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val store = TipStore(dataStore)
            val source = FakeLedgerSource(consent = consent, today = today)
            val contractFlow = MutableStateFlow(contracts)
            val budgetFlow = MutableStateFlow(budget)
            val repository = SavingTipsRepository(
                source,
                store,
                contractFlow,
                flowOf(TagFacts()),
                flowOf(ReceiptFacts.None),
                backgroundScope,
                budgetFlow
            )
            val viewModel = SavingTipsViewModel(repository)
            try {
                Fixture(viewModel, store, source, contractFlow, budgetFlow).block()
            } finally {
                // Its coroutines must not outlive the test dispatcher set as Main
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun showsTipsOnceTheAnalysisIsTurnedOn() = withViewModel(consent = false) {
        assertEquals(
            AnalysisState.Declined,
            awaitValue(viewModel.savingTips) { it == AnalysisState.Declined }
        )
        source.setConsent(true)
        assertEquals(listOf(PotsOverspent.ID, priceIncreasesId), bothTips().tips.map { it.id })
    }

    @Test
    fun loadsUntilTheContractsAreKnown() = withViewModel(contracts = null) {
        withContext(Dispatchers.Default) { delay(200) }
        assertEquals(AnalysisState.Loading, viewModel.savingTips.value)
        contracts.value = contractFacts
        overview { it.tips.isNotEmpty() }
    }

    @Test
    fun showsAllTipsHighestAmountFirstAndAsNew() = withViewModel {
        val overview = bothTips()
        // 1200.00 a year for the pot, 48.00 for the price increase
        assertEquals(listOf(PotsOverspent.ID, priceIncreasesId), overview.tips.map { it.id })
        assertEquals(setOf(PotsOverspent.ID, priceIncreasesId), overview.newIds)
        assertEquals(emptyList<SavingTip>(), overview.dismissed)
    }

    @Test
    fun tipsOfTheContractsDoNotWaitForTheBudget() = withViewModel(budget = null) {
        assertEquals(listOf(priceIncreasesId), overview { true }.tips.map { it.id })
        budget.value = budget(PotsOverspent.PERIODS)
        overview { it.tips.size == 2 }
    }

    @Test
    fun dismissedTipMovesToTheDismissedOnesAndIsNotNew() = withViewModel {
        val tip = tip(PotsOverspent.ID)
        viewModel.dismissTip(tip)
        val overview = overview { it.dismissed.isNotEmpty() }
        assertEquals(listOf(tip), overview.dismissed)
        assertEquals(listOf(priceIncreasesId), overview.tips.map { it.id })
        assertEquals(setOf(priceIncreasesId), overview.newIds)
        val decisions = flush()
        assertEquals(mapOf(tip.id to tip.facts), decisions.dismissed)
        assertEquals(emptyMap<String, Set<String>>(), decisions.seen)
    }

    @Test
    fun restoredTipIsShownAgainAndStillNew() = withViewModel {
        val tip = tip(PotsOverspent.ID)
        viewModel.dismissTip(tip)
        overview { it.dismissed.isNotEmpty() }
        viewModel.restoreTip(tip)
        val overview = overview { it.dismissed.isEmpty() }
        assertEquals(listOf(PotsOverspent.ID, priceIncreasesId), overview.tips.map { it.id })
        assertTrue(tip.id in overview.newIds)
        assertEquals(TipDecisions(), flush())
    }

    @Test
    fun restoringATipNeverDismissedStoresNothing() = withViewModel {
        viewModel.restoreTip(tip(priceIncreasesId))
        assertEquals(TipDecisions(), flush())
    }

    @Test
    fun seenTipsAreNoLongerNew() = withViewModel {
        val tips = bothTips().tips
        viewModel.markTipsSeen(tips)
        assertEquals(emptySet<String>(), overview { it.newIds.isEmpty() }.newIds)
        val decisions = flush()
        assertEquals(tips.associate { it.id to it.facts }, decisions.seen)
        assertEquals(emptyMap<String, Set<String>>(), decisions.dismissed)
    }

    @Test
    fun markingNoTipsSeenStoresNothing() = withViewModel {
        bothTips()
        viewModel.markTipsSeen(emptyList())
        assertEquals(TipDecisions(), flush())
    }

    @Test
    fun dismissedAndSeenTipComesBackAsNewWithANewFact() = withViewModel(
        budget = budget(PotsOverspent.MIN_OVERSPENT)
    ) {
        val tip = tip(PotsOverspent.ID)
        viewModel.markTipsSeen(listOf(tip))
        awaitValue(store.decisions) { !it.isNew(tip) }
        viewModel.dismissTip(tip)
        awaitValue(store.decisions) { it.isDismissed(tip) }
        overview { it.dismissed.isNotEmpty() }
        // Overspent once more
        budget.value = budget(PotsOverspent.PERIODS)
        val overview = overview { it.dismissed.isEmpty() }
        val back = overview.tips.single { it.id == PotsOverspent.ID }
        assertEquals(setOf("food:4"), back.facts)
        assertTrue(back.id in overview.newIds)
        val decisions = store.decisions.first()
        assertFalse(decisions.isDismissed(back))
        // The facts known stay stored, so that the old one is not new again
        assertEquals(setOf("food:3"), decisions.dismissed[tip.id])
    }

    private companion object {
        /** The first value of [flow] that matches, failing after a few seconds of real time */
        suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
            withContext(Dispatchers.Default) { withTimeout(5_000) { flow.first(predicate) } }
    }
}
