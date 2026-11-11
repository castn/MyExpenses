package org.totschnig.myexpenses.next.feature.portfolio

import androidx.lifecycle.SavedStateHandle
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.data.portfolio.Asset
import org.totschnig.myexpenses.next.data.portfolio.AssetKind
import org.totschnig.myexpenses.next.data.portfolio.CostDraft
import org.totschnig.myexpenses.next.data.portfolio.Funding
import org.totschnig.myexpenses.next.data.portfolio.MatchQuery
import org.totschnig.myexpenses.next.data.portfolio.MatchingTransaction
import org.totschnig.myexpenses.next.data.portfolio.TradeKind
import org.totschnig.myexpenses.next.data.portfolio.withCost

@OptIn(ExperimentalCoroutinesApi::class)
class TradeEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val now = LocalDateTime.of(2026, 3, 10, 9, 15)
    private val store = FakeTradeStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        TradeEditViewModel(store, savedState, ',', { now }, dispatcher).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
        }

    @Test
    fun newBuyStartsWithTheFirstHoldingItsPriceAndTheLastFees() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startNew(1, TradeKind.BUY)
        val state = vm.state.value
        val draft = state.draft!!
        assertEquals("AAPL", draft.assetCode)
        assertEquals("180,5", draft.priceText)
        assertEquals(listOf(CostDraft("", store.fees)), draft.costs)
        assertEquals(now, draft.date)
        assertEquals(store.apple, state.asset)
        // Bitcoin is known but not held
        assertEquals(listOf(store.bitcoin), state.assets)
        assertEquals(listOf(store.giro), state.accounts.funding)
    }

    @Test
    fun typedQuantityShowsFiguresAndSaveClosesTheScreen() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startNew(1, TradeKind.BUY)
        vm.changeDraft(vm.state.value.draft!!.copy(quantityText = "2"))
        vm.changeDraft(vm.state.value.draft!!.withCost(0, CostDraft("4,95", store.fees)))
        assertEquals(36100L, vm.state.value.figures?.principal)
        assertEquals(36595L, vm.state.value.figures?.total)
        vm.save()
        val (portfolioId, values) = store.saved.single()
        assertEquals(1L, portfolioId)
        assertEquals(2L, values.quantity)
        assertEquals(36100L, values.principal)
        assertTrue(vm.state.value.isDone)
        assertNull(vm.state.value.draft)
    }

    @Test
    fun saveAndNewKeepsTheFormOpenWithTheFeesOfTheTradeSaved() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startNew(1, TradeKind.BUY)
        vm.changeDraft(vm.state.value.draft!!.copy(quantityText = "1", costs = emptyList()))
        vm.save(andNew = true)
        val draft = vm.state.value.draft!!
        assertTrue(draft.isNew)
        assertEquals("", draft.quantityText)
        assertEquals(emptyList<CostDraft>(), draft.costs)
        assertEquals(TradeMessage.SAVED, vm.state.value.message)
        assertFalse(vm.state.value.isDone)
        vm.messageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun failedSaveIsShownAndTheDraftStays() = runTest(dispatcher) {
        store.failSave = true
        val vm = viewModel()
        vm.startNew(1, TradeKind.DEPOSIT)
        vm.changeDraft(vm.state.value.draft!!.copy(quantityText = "100"))
        vm.save()
        assertEquals(TradeMessage.SAVE_FAILED, vm.state.value.message)
        assertEquals("100", vm.state.value.draft?.quantityText)
        vm.messageShown()
        assertNull(vm.state.value.message)
    }

    @Test
    fun editShowsTheTradeAndCountsWhatItSoldAsAvailable() = runTest(dispatcher) {
        val vm = viewModel()
        vm.edit(5)
        val state = vm.state.value
        val draft = state.draft!!
        assertEquals(5L, draft.id)
        assertEquals("4", draft.quantityText)
        assertEquals("175,25", draft.priceText)
        assertEquals(Funding.ACCOUNT, draft.funding)
        assertEquals(14L, state.figures?.available?.amount)
        vm.save()
        assertEquals(5L, store.saved.single().second.tradeId)
    }

    @Test
    fun editOfATradeThatIsGoneShowsSo() = runTest(dispatcher) {
        val vm = viewModel()
        vm.edit(99)
        assertTrue(vm.state.value.isNotFound)
        assertNull(vm.state.value.draft)
    }

    @Test
    fun theDraftSurvivesTheEndOfTheProcess() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val first = viewModel(savedState)
        first.startNew(1, TradeKind.SELL)
        first.changeDraft(first.state.value.draft!!.copy(quantityText = "3", comment = "Rest"))
        val second = viewModel(savedState)
        second.startNew(1, TradeKind.BUY)
        val draft = second.state.value.draft!!
        assertEquals(TradeKind.SELL, draft.kind)
        assertEquals("3", draft.quantityText)
        assertEquals("Rest", draft.comment)
        assertEquals(listOf(store.giro), second.state.value.accounts.funding)
    }

    @Test
    fun theOnlyMatchOfTheFundingAccountIsLinkedAndCanBeUnlinked() = runTest(dispatcher) {
        val match = MatchingTransaction(42, "Broker", -36100, LocalDate.of(2026, 3, 10))
        store.matches.value =
            mapOf(MatchQuery(3, -36100, LocalDate.of(2026, 3, 10)) to listOf(match))
        val vm = viewModel()
        vm.startNew(1, TradeKind.BUY)
        vm.changeDraft(vm.state.value.draft!!.copy(quantityText = "2", costs = emptyList()))
        assertEquals(emptyList<MatchingTransaction>(), vm.state.value.matches)
        vm.chooseFunding(Funding.ACCOUNT, 3)
        assertEquals(listOf(match), vm.state.value.matches)
        assertEquals(42L, vm.state.value.draft?.linkedTransactionId)
        vm.link(null)
        assertNull(vm.state.value.draft?.linkedTransactionId)
        vm.save()
        assertNull(store.saved.single().second.linkedTransactionId)
    }

    @Test
    fun roundingModeIsKeptPerPortfolio() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startNew(1, TradeKind.BUY)
        vm.changeDraft(
            vm.state.value.draft!!.copy(
                quantityText = "3",
                priceText = "10,005",
                costs = emptyList()
            )
        )
        assertTrue(vm.state.value.figures!!.isRoundingNecessary)
        assertEquals(3002L, vm.state.value.figures?.principal)
        vm.chooseRounding(RoundingMode.DOWN)
        assertEquals(RoundingMode.DOWN, vm.state.value.portfolio?.rounding)
        assertEquals(3001L, vm.state.value.figures?.principal)
    }

    @Test
    fun newAssetIsChosenAndATakenCodeIsShown() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startNew(1, TradeKind.BUY)
        val etf = Asset("VWRL", "Vanguard All-World", 0, AssetKind.SECURITY)
        vm.createAsset(etf)
        assertEquals("VWRL", vm.state.value.draft?.assetCode)
        assertEquals(etf, vm.state.value.asset)
        vm.createAsset(etf.copy(label = "Doppelt"))
        assertEquals(TradeMessage.ASSET_TAKEN, vm.state.value.message)
        vm.messageShown()
        assertNull(vm.state.value.message)
    }
}
