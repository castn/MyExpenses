package org.totschnig.myexpenses.next.feature.guidance

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.data.guidance.DataGap
import org.totschnig.myexpenses.next.data.guidance.DataReadinessRepository
import org.totschnig.myexpenses.next.data.guidance.HintStore
import org.totschnig.myexpenses.next.data.guidance.HintTopic

/** Which hints show after each input and action, and how closing them is stored */
@OptIn(ExperimentalCoroutinesApi::class)
class HintsActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val today = LocalDate.of(2026, 10, 15)

    /** Twelve debits of the last weeks at a bakery with a precise category, ids 1 to 12 */
    private val described = (1L..12).map { debit(it, daysAgo = it) }

    /** Four supermarket purchases without category, ids 21 to 24 */
    private val uncategorized = (21L..24).map {
        debit(it, daysAgo = it - 20, categoryId = null, payee = "REWE")
    }

    /** A supermarket purchase with a category */
    private val shopping = debit(SHOPPING, daysAgo = 2, payee = "REWE")

    /** A credit, no purchase */
    private val refund = debit(REFUND, daysAgo = 3, categoryId = null).copy(amount = 1000)

    private val accounts = OwnAccounts(setOf(1), emptyMap())

    private val ledger = Ledger(described + uncategorized + shopping + refund, accounts)

    private fun debit(id: Long, daysAgo: Long, categoryId: Long? = 1, payee: String = "Bäcker") =
        LedgerTransaction(
            id,
            today.minusDays(daysAgo),
            -1000,
            accountId = 1,
            payeeName = payee,
            categoryId = categoryId,
            categoryPath = categoryId?.let { "Lebensmittel" }
        )

    private fun scanned(vararg ids: Long) =
        ReceiptFacts(ids.associate { it to listOf(ScannedItem("Brot", 300)) })

    private class Fixture(
        val viewModel: HintsViewModel,
        val source: FakeLedgerSource,
        val receipts: MutableStateFlow<ReceiptFacts>,
        val store: HintStore,
        val scope: TestScope
    ) {
        /** The hint after saving [transactionId], once it is shown */
        suspend fun afterSave(transactionId: Long): HintTopic {
            val shown = CompletableDeferred<HintTopic>()
            viewModel.afterSave(transactionId) { shown.complete(it) }
            return withContext(Dispatchers.Default) { withTimeout(5_000) { shown.await() } }
        }

        /** No hint after saving [transactionId] within a moment */
        suspend fun afterSaveShowsNothing(transactionId: Long) {
            val shown = CompletableDeferred<HintTopic>()
            viewModel.afterSave(transactionId) { shown.complete(it) }
            settle()
            assertFalse(shown.isCompleted)
        }

        suspend fun dismissed(predicate: (Set<HintTopic>) -> Boolean) =
            awaitValue(viewModel.dismissed) { it != null && predicate(it) }!!

        suspend fun overviewHint(predicate: (DataGap?) -> Boolean) =
            awaitValue(viewModel.overviewHint, predicate)

        /** Lets the flows and the data store run, if they do anything at all */
        suspend fun settle() = withContext(Dispatchers.Default) { delay(300) }
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
        ledger: Ledger? = this.ledger,
        receipts: ReceiptFacts = ReceiptFacts(emptyMap()),
        block: suspend Fixture.() -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val source = FakeLedgerSource(ledger = ledger, today = today)
            val receiptFlow = MutableStateFlow(receipts)
            val store = HintStore(dataStore)
            val repository = DataReadinessRepository(source, receiptFlow, backgroundScope)
            val viewModel = HintsViewModel(source, receiptFlow, repository.readiness, store)
            backgroundScope.launch { viewModel.dismissed.collect {} }
            backgroundScope.launch { viewModel.overviewHint.collect {} }
            backgroundScope.launch { viewModel.scanned.collect {} }
            try {
                Fixture(viewModel, source, receiptFlow, store, this).block()
            } finally {
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun showsNothingBeforeAnyoneLooks() = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val source = FakeLedgerSource(ledger = ledger, today = today)
            val receipts = MutableStateFlow(ReceiptFacts(emptyMap()))
            val repository = DataReadinessRepository(source, receipts, backgroundScope)
            val viewModel =
                HintsViewModel(source, receipts, repository.readiness, HintStore(dataStore))
            assertNull(viewModel.dismissed.value)
            assertNull(viewModel.overviewHint.value)
            assertNull(viewModel.scanned.value)
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun theOverviewTellsAboutTheMissingCategoriesFirst() = withViewModel {
        assertEquals(emptySet<HintTopic>(), dismissed { true })
        val hint = overviewHint { it != null }
        assertEquals(DataGap.Uncategorized(listOf(21L, 22L, 23L, 24L), 17), hint)
    }

    @Test
    fun theOverviewShowsNothingWithoutTheLedger() = withViewModel(ledger = null) {
        settle()
        assertNull(viewModel.overviewHint.value)
        source.ledger.value = ledger
        assertTrue(overviewHint { it != null } is DataGap.Uncategorized)
    }

    @Test
    fun closingTheCategoryHintIsStoredAndShowsTheReceiptHint() = withViewModel {
        overviewHint { it is DataGap.Uncategorized }
        viewModel.dismiss(HintTopic.CATEGORY)
        assertEquals(setOf(HintTopic.CATEGORY), dismissed { it.isNotEmpty() })
        assertEquals(setOf(HintTopic.CATEGORY), store.dismissed.first())
        val hint = overviewHint { it is DataGap.FewReceipts } as DataGap.FewReceipts
        assertEquals(listOf(21L, 22L, 23L, 24L, SHOPPING), hint.unscanned)
    }

    @Test
    fun closingBothHintsLeavesNoneForTheMissingTags() = withViewModel {
        viewModel.dismiss(HintTopic.CATEGORY)
        dismissed { HintTopic.CATEGORY in it }
        viewModel.dismiss(HintTopic.RECEIPT)
        assertEquals(HintTopic.entries.toSet(), dismissed { it.size == 2 })
        // The gap of the tags has no hint
        assertNull(overviewHint { it == null })
    }

    @Test
    fun noReceiptHintWhenNothingIsLeftToScan() = withViewModel(
        receipts = scanned(21, 22, 23, 24, SHOPPING)
    ) {
        overviewHint { it is DataGap.Uncategorized }
        viewModel.dismiss(HintTopic.CATEGORY)
        dismissed { HintTopic.CATEGORY in it }
        // Too few receipts are still a gap, but there is no purchase left to scan
        assertNull(overviewHint { it == null })
    }

    @Test
    fun noReceiptHintWhenReceiptsCannotBeScanned() = withViewModel(receipts = ReceiptFacts.None) {
        overviewHint { it is DataGap.Uncategorized }
        viewModel.dismiss(HintTopic.CATEGORY)
        dismissed { HintTopic.CATEGORY in it }
        assertNull(overviewHint { it == null })
    }

    @Test
    fun theOverviewHintGoesOnceTheDebitsHaveACategory() = withViewModel {
        overviewHint { it is DataGap.Uncategorized }
        val categorized = uncategorized.map { it.copy(categoryId = 2, categoryPath = "Essen") }
        source.ledger.value = Ledger(described + categorized + shopping + refund, accounts)
        assertTrue(overviewHint { it !is DataGap.Uncategorized } is DataGap.FewReceipts)
    }

    @Test
    fun resetShowsTheClosedHintsAgain() = withViewModel {
        viewModel.dismiss(HintTopic.CATEGORY)
        dismissed { HintTopic.CATEGORY in it }
        overviewHint { it is DataGap.FewReceipts }
        viewModel.reset()
        assertEquals(emptySet<HintTopic>(), dismissed { it.isEmpty() })
        assertEquals(emptySet<HintTopic>(), store.dismissed.first())
        assertTrue(overviewHint { it is DataGap.Uncategorized } is DataGap.Uncategorized)
    }

    @Test
    fun scannedFollowsTheReceipts() = withViewModel {
        assertEquals(emptySet<Long>(), awaitValue(viewModel.scanned) { it != null })
        receipts.value = scanned(21, SHOPPING)
        assertEquals(setOf(21L, SHOPPING), awaitValue(viewModel.scanned) { it?.size == 2 })
        receipts.value = ReceiptFacts(mapOf(21L to emptyList()), canScan = false)
        assertNull(awaitValue(viewModel.scanned) { it == null })
    }

    @Test
    fun afterSavingADebitWithoutCategoryAsksForOne() = withViewModel {
        assertEquals(HintTopic.CATEGORY, afterSave(21))
    }

    @Test
    fun afterSavingASupermarketPurchaseAsksForTheReceipt() = withViewModel {
        assertEquals(HintTopic.RECEIPT, afterSave(SHOPPING))
    }

    @Test
    fun afterSavingWaitsForTheLedgerOnlyForAMoment() = withViewModel {
        val start = scope.testScheduler.currentTime
        // The ledger does not change: the one shown before is used after the wait
        assertEquals(HintTopic.RECEIPT, afterSave(SHOPPING))
        assertEquals(3000L, scope.testScheduler.currentTime - start)
    }

    @Test
    fun afterSavingAPurchaseThatLacksNothingShowsNothing() = withViewModel {
        afterSaveShowsNothing(1)
    }

    @Test
    fun afterSavingACreditShowsNothing() = withViewModel {
        afterSaveShowsNothing(REFUND)
    }

    @Test
    fun afterSavingATransactionNotInTheLedgerShowsNothing() = withViewModel {
        afterSaveShowsNothing(99)
    }

    @Test
    fun afterSavingShowsNothingWithoutTheLedger() = withViewModel(ledger = null) {
        afterSaveShowsNothing(21)
    }

    @Test
    fun afterSavingAScannedPurchaseShowsNothing() = withViewModel(receipts = scanned(SHOPPING)) {
        afterSaveShowsNothing(SHOPPING)
    }

    @Test
    fun afterSavingAsksForNoReceiptWhenItCannotBeScanned() =
        withViewModel(receipts = ReceiptFacts.None) {
            afterSaveShowsNothing(SHOPPING)
            assertEquals(HintTopic.CATEGORY, afterSave(21))
        }

    @Test
    fun afterSavingSkipsAClosedTopic() = withViewModel {
        viewModel.dismiss(HintTopic.CATEGORY)
        dismissed { HintTopic.CATEGORY in it }
        // A supermarket purchase without category gets the next hint
        assertEquals(HintTopic.RECEIPT, afterSave(21))
        afterSaveShowsNothing(1)
    }

    @Test
    fun afterSavingAHintShowsAtMostThreeTimes() = withViewModel {
        repeat(HintTopic.MAX_AFTER_SAVE) { assertEquals(HintTopic.RECEIPT, afterSave(SHOPPING)) }
        assertEquals(setOf(HintTopic.RECEIPT), store.hiddenAfterSave.first())
        afterSaveShowsNothing(SHOPPING)
        // Counted after saving, not closed: the overview still tells about it
        assertEquals(emptySet<HintTopic>(), store.dismissed.first())
        // Other topics are counted on their own
        assertEquals(HintTopic.CATEGORY, afterSave(21))
    }

    @Test
    fun resetShowsTheHintsAfterSavingAgain() = withViewModel {
        repeat(HintTopic.MAX_AFTER_SAVE) { afterSave(SHOPPING) }
        afterSaveShowsNothing(SHOPPING)
        viewModel.reset()
        awaitValue(store.hiddenAfterSave) { it.isEmpty() }
        assertEquals(HintTopic.RECEIPT, afterSave(SHOPPING))
    }

    private companion object {
        const val SHOPPING = 30L
        const val REFUND = 40L

        suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
            withContext(Dispatchers.Default) { withTimeout(5_000) { flow.first(predicate) } }
    }
}
