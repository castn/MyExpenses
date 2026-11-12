package org.totschnig.myexpenses.next.feature.analysis

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.data.analysis.CategoryAnalysis
import org.totschnig.myexpenses.next.data.analysis.PeriodKind
import org.totschnig.myexpenses.next.data.tags.TagFacts

@OptIn(ExperimentalCoroutinesApi::class)
class CategorySpendingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** The view model reads the clock itself, so the transactions are dated relative to it */
    private val today: LocalDate = LocalDate.now()
    private val lastMonth: LocalDate = today.withDayOfMonth(1).minusMonths(1)
    private val lastYear: LocalDate = today.withDayOfYear(1).minusYears(1)

    private val food = CategoryInfo(10, "Essen", "Essen")
    private val clothes = CategoryInfo(20, "Kleidung", "Kleidung")
    private val catalog = Catalog(categories = listOf(food, clothes))
    private var nextId = 1L

    private fun transaction(date: LocalDate, amount: Long, category: CategoryInfo) =
        LedgerTransaction(nextId++, date, amount, accountId = 1, categoryId = category.id)

    private val groceries = transaction(today, -5000, food)
    private val shirt = transaction(today, -3000, clothes)
    private val earlierGroceries = transaction(lastMonth, -2000, food)
    private val coat = transaction(lastYear, -9000, clothes)

    private fun ledger(vararg transactions: LedgerTransaction) =
        Ledger(transactions.toList(), OwnAccounts(emptySet(), emptyMap()))

    private val ledger = ledger(groceries, shirt, earlierGroceries, coat)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val viewModel: CategorySpendingViewModel,
        val source: FakeLedgerSource,
        val tagFacts: MutableStateFlow<TagFacts>
    ) {
        val analysis get() = viewModel.analysis.value.ready()
        val currentMonth get() = viewModel.currentMonth.value.ready()

        fun AnalysisState<CategoryAnalysis>.ready(): CategoryAnalysis =
            valueOrNull ?: throw AssertionError("Not ready: $this")
    }

    private fun withViewModel(
        source: FakeLedgerSource = FakeLedgerSource(ledger = ledger, catalog = catalog),
        block: suspend Fixture.() -> Unit
    ) = runTest(dispatcher) {
        val tagFacts = MutableStateFlow(TagFacts())
        val viewModel = CategorySpendingViewModel(source, tagFacts, dispatcher)
        // Like shown screens, which keep the analyses running
        backgroundScope.launch { viewModel.analysis.collect {} }
        backgroundScope.launch { viewModel.currentMonth.collect {} }
        try {
            Fixture(viewModel, source, tagFacts).block()
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
    }

    private val CategoryAnalysis.amounts get() = categories.associate { it.key to it.amount }

    @Test
    fun loadsUntilTheLedgerIsRead() = withViewModel(FakeLedgerSource(catalog = catalog)) {
        assertSame(AnalysisState.Loading, viewModel.analysis.value)
        assertSame(AnalysisState.Loading, viewModel.currentMonth.value)
        source.ledger.value = ledger
        assertEquals(8000, analysis.total)
    }

    @Test
    fun tellsTheAnalysisIsTurnedOff() = withViewModel(
        FakeLedgerSource(consent = false, ledger = ledger, catalog = catalog)
    ) {
        assertSame(AnalysisState.Declined, viewModel.analysis.value)
        assertSame(AnalysisState.Declined, viewModel.currentMonth.value)
        source.setConsent(true)
        assertEquals(8000, analysis.total)
    }

    @Test
    fun showsTheCurrentMonthByCategory() = withViewModel {
        assertEquals(PeriodKind.MONTH, analysis.period.kind)
        assertEquals(0, analysis.offset)
        assertEquals(today.withDayOfMonth(1), analysis.period.start)
        assertEquals(mapOf(10L to 5000L, 20L to 3000L), analysis.amounts)
        assertEquals("Essen", analysis.categories.first().label)
        assertEquals(analysis, currentMonth)
    }

    @Test
    fun goesToEarlierMonthsAndBackButNotPastTheCurrentOne() = withViewModel {
        viewModel.showEarlier()
        assertEquals(1, analysis.offset)
        assertEquals(lastMonth, analysis.period.start)
        assertEquals(mapOf(10L to 2000L), analysis.amounts)
        viewModel.showLater()
        assertEquals(0, analysis.offset)
        viewModel.showLater()
        assertEquals(0, analysis.offset)
        assertEquals(8000, analysis.total)
    }

    @Test
    fun switchesToYearsStartingWithTheCurrentOne() = withViewModel {
        viewModel.showEarlier()
        viewModel.selectKind(PeriodKind.YEAR)
        assertEquals(PeriodKind.YEAR, analysis.period.kind)
        assertEquals(0, analysis.offset)
        assertEquals(today.withDayOfYear(1), analysis.period.start)
        viewModel.showEarlier()
        assertEquals(lastYear, analysis.period.start)
        assertEquals(9000L, analysis.amounts[20L])
    }

    @Test
    fun showCurrentMonthResetsKindAndOffset() = withViewModel {
        viewModel.selectKind(PeriodKind.YEAR)
        viewModel.showEarlier()
        viewModel.showCurrentMonth()
        assertEquals(PeriodKind.MONTH, analysis.period.kind)
        assertEquals(0, analysis.offset)
    }

    @Test
    fun currentMonthStaysWhileTheUserPicksAnotherPeriod() = withViewModel {
        viewModel.selectKind(PeriodKind.YEAR)
        viewModel.showEarlier()
        assertEquals(PeriodKind.MONTH, currentMonth.period.kind)
        assertEquals(0, currentMonth.offset)
        assertEquals(8000, currentMonth.total)
    }

    @Test
    fun leavesOutWhatTheTagsExcludeFromSpending() = withViewModel {
        tagFacts.value = TagFacts(excludedFromSpending = setOf(shirt.id))
        assertEquals(mapOf(10L to 5000L), analysis.amounts)
        assertEquals(mapOf(10L to 5000L), currentMonth.amounts)
    }

    @Test
    fun followsNewTransactionsAndCategories() = withViewModel {
        source.ledger.value = ledger(groceries, transaction(today, -1000, food))
        assertEquals(mapOf(10L to 6000L), analysis.amounts)
        source.catalog.value = Catalog(categories = listOf(food.copy(label = "Lebensmittel")))
        assertEquals("Lebensmittel", analysis.categories.single().label)
    }

    @Test
    fun followsTheDayOfTheLedgerSource() = withViewModel(
        FakeLedgerSource(ledger = ledger, catalog = catalog, today = today.minusYears(1))
    ) {
        assertEquals(source.today.value, analysis.period.end)
        assertEquals(source.today.value, currentMonth.period.end)

        source.today.value = today
        assertEquals(today, analysis.period.end)
        assertEquals(8000, analysis.total)
    }
}
