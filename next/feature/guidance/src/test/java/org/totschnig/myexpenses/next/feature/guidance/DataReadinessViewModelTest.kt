package org.totschnig.myexpenses.next.feature.guidance

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.data.guidance.DataGap
import org.totschnig.myexpenses.next.data.guidance.DataReadiness
import org.totschnig.myexpenses.next.data.guidance.DataReadinessRepository

/** What the readiness of the data shows after each input of the ledger, the receipts and the day */
@OptIn(ExperimentalCoroutinesApi::class)
class DataReadinessViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val today = LocalDate.of(2026, 10, 15)

    /** Twelve debits of the last weeks with a precise category, ids 1 to 12, none tagged */
    private val described = (1L..12).map { debit(it, daysAgo = it) }

    /** Four of them at a supermarket, ids 21 to 24, without category */
    private val uncategorized = (21L..24).map {
        debit(it, daysAgo = it - 20, categoryId = null, payee = "REWE")
    }

    private val accounts = OwnAccounts(setOf(1), emptyMap())

    private val ledger = Ledger(described + uncategorized, accounts)

    private fun debit(
        id: Long,
        daysAgo: Long,
        categoryId: Long? = 1,
        payee: String = "Bäcker",
        tagIds: Set<Long> = emptySet()
    ) = LedgerTransaction(
        id,
        today.minusDays(daysAgo),
        -1000,
        accountId = 1,
        payeeName = payee,
        categoryId = categoryId,
        categoryPath = categoryId?.let { "Lebensmittel" },
        tagIds = tagIds
    )

    private fun scanned(vararg ids: Long) =
        ReceiptFacts(ids.associate { it to listOf(ScannedItem("Brot", 300)) })

    private class Fixture(
        val viewModel: DataReadinessViewModel,
        val source: FakeLedgerSource,
        val receipts: MutableStateFlow<ReceiptFacts>
    ) {
        suspend fun ready(predicate: (DataReadiness) -> Boolean = { true }): DataReadiness =
            awaitValue(viewModel.readiness) { it != null && predicate(it) }!!

        /** Lets the flows run, if they emit at all */
        suspend fun settle() = withContext(Dispatchers.Default) { delay(200) }
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
    ) = runTest(dispatcher) {
        val source = FakeLedgerSource(ledger = ledger, today = today)
        val receiptFlow = MutableStateFlow(receipts)
        val viewModel = DataReadinessViewModel(
            DataReadinessRepository(source, receiptFlow, backgroundScope)
        )
        backgroundScope.launch { viewModel.readiness.collect {} }
        try {
            Fixture(viewModel, source, receiptFlow).block()
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun isNullBeforeAnyoneLooks() = runTest(dispatcher) {
        val source = FakeLedgerSource(ledger = ledger, today = today)
        val repository = DataReadinessRepository(
            source,
            MutableStateFlow(ReceiptFacts(emptyMap())),
            backgroundScope
        )
        val viewModel = DataReadinessViewModel(repository)
        assertNull(viewModel.readiness.value)
        viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun staysNullUntilTheLedgerIsKnown() = withViewModel(ledger = null) {
        settle()
        assertNull(viewModel.readiness.value)
        source.ledger.value = ledger
        assertEquals(16, ready().debits)
    }

    @Test
    fun turnsNullWhenTheLedgerIsGone() = withViewModel {
        ready()
        // Without consent to the analysis the ledger is null
        source.ledger.value = null
        assertNull(awaitValue(viewModel.readiness) { it == null })
    }

    @Test
    fun countsTheDebitsAndWhatTheyLack() = withViewModel {
        val readiness = ready()
        assertEquals(16, readiness.debits)
        assertEquals(listOf(21L, 22L, 23L, 24L), readiness.uncategorized)
        assertEquals(emptyList<Long>(), readiness.vague)
        assertEquals(listOf(21L, 22L, 23L, 24L), readiness.supermarketPurchases)
        assertEquals(listOf(21L, 22L, 23L, 24L), readiness.unscanned)
        assertEquals(0, readiness.taggedDebits)
        assertEquals(
            listOf(
                DataGap.Uncategorized(listOf(21L, 22L, 23L, 24L), 16),
                DataGap.FewReceipts(
                    scanned = 0,
                    supermarketPurchases = 4,
                    unscanned = listOf(21L, 22L, 23L, 24L),
                    missingRecent = 4,
                    missingUsual = 6
                ),
                DataGap.NoTags
            ),
            readiness.gaps
        )
    }

    @Test
    fun tooFewDebitsHaveNoGaps() = withViewModel(
        ledger = Ledger(uncategorized, OwnAccounts(setOf(1), emptyMap()))
    ) {
        val readiness = ready()
        assertEquals(4, readiness.debits)
        assertEquals(emptyList<DataGap>(), readiness.gaps)
    }

    @Test
    fun closesTheCategoryGapOnceTheDebitsHaveACategory() = withViewModel {
        assertTrue(ready().gaps.first() is DataGap.Uncategorized)
        val categorized = uncategorized.map { it.copy(categoryId = 2, categoryPath = "Essen") }
        source.ledger.value = Ledger(described + categorized, accounts)
        val readiness = ready { it.uncategorized.isEmpty() }
        assertTrue(readiness.gaps.none { it is DataGap.Uncategorized })
    }

    @Test
    fun countsCatchAllCategoriesAsVague() = withViewModel {
        ready()
        val vague = uncategorized.map { it.copy(categoryId = 3, categoryPath = "Sonstiges") }
        source.ledger.value = Ledger(described + vague, accounts)
        val readiness = ready { it.vague.isNotEmpty() }
        assertEquals(listOf(21L, 22L, 23L, 24L), readiness.vague)
        assertEquals(
            DataGap.VagueCategories(listOf(21L, 22L, 23L, 24L), 16),
            readiness.gaps.first()
        )
    }

    @Test
    fun closesTheTagGapOnceADebitIsTagged() = withViewModel {
        assertTrue(DataGap.NoTags in ready().gaps)
        val tagged = described.first().copy(tagIds = setOf(5))
        source.ledger.value =
            Ledger(listOf(tagged) + described.drop(1) + uncategorized, accounts)
        val readiness = ready { it.taggedDebits == 1 }
        assertFalse(DataGap.NoTags in readiness.gaps)
    }

    @Test
    fun countsScannedReceiptsAsTheyArrive() = withViewModel {
        ready()
        receipts.value = scanned(21, 22)
        val readiness = ready { it.scannedRecent == 2 }
        assertEquals(listOf(23L, 24L), readiness.unscanned)
        assertEquals(2, readiness.scannedLastYear)
        assertEquals(2, readiness.scannedSupermarketLastYear)
        val gap = readiness.gaps.filterIsInstance<DataGap.FewReceipts>().single()
        assertEquals(2, gap.missingRecent)
    }

    @Test
    fun closesTheReceiptGapWithEnoughReceiptsSpreadOverTheYear() = withViewModel {
        ready()
        // Six scanned purchases in the year before the recent months
        val older = (31L..36).map { debit(it, daysAgo = 100 + (it - 30) * 30, payee = "REWE") }
        source.ledger.value = Ledger(ledger.transactions + older, accounts)
        receipts.value = scanned(21, 22, 23, 24, 31, 32, 33, 34, 35, 36)
        val readiness = ready { it.scannedUsual == 6 }
        assertEquals(4, readiness.scannedRecent)
        assertTrue(readiness.gaps.none { it is DataGap.FewReceipts })
    }

    @Test
    fun asksForNoReceiptsWhenTheyCannotBeScanned() = withViewModel(receipts = ReceiptFacts.None) {
        val readiness = ready()
        assertFalse(readiness.canScanReceipts)
        assertTrue(readiness.gaps.none { it is DataGap.FewReceipts })
        receipts.value = ReceiptFacts(emptyMap())
        assertTrue(ready { it.canScanReceipts }.gaps.any { it is DataGap.FewReceipts })
    }

    @Test
    fun takesTheDayFromTheLedgerSource() = withViewModel {
        val later = debit(40, daysAgo = -5)
        source.ledger.value = Ledger(ledger.transactions + later, accounts)
        // A debit dated after today does not count yet
        assertEquals(16, ready { it.debits == 16 }.debits)
        source.today.value = today.plusDays(5)
        assertEquals(17, ready { it.debits == 17 }.debits)
        // Three months on, the debits of the last weeks are no longer recent
        source.today.value = today.plusMonths(4)
        assertEquals(0, ready { it.debits == 0 }.debits)
    }

    private companion object {
        suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
            withContext(Dispatchers.Default) { withTimeout(5_000) { flow.first(predicate) } }
    }
}
