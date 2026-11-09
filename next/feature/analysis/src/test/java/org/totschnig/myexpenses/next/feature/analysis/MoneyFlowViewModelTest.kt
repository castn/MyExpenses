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
import org.junit.Assert.assertTrue
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
import org.totschnig.myexpenses.next.data.analysis.MoneyFlow
import org.totschnig.myexpenses.next.data.analysis.PeriodKind
import org.totschnig.myexpenses.next.data.tags.TagFacts

@OptIn(ExperimentalCoroutinesApi::class)
class MoneyFlowViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today: LocalDate = LocalDate.of(2026, 10, 13)

    private val food = CategoryInfo(10, "Essen", "Essen", isIncome = false)
    private val clothes = CategoryInfo(20, "Kleidung", "Kleidung", isIncome = false)
    private val salary = CategoryInfo(30, "Gehalt", "Gehalt", isExpense = false)
    private val catalog = Catalog(categories = listOf(food, clothes, salary))
    private var nextId = 1L

    private fun transaction(date: LocalDate, amount: Long, category: CategoryInfo) =
        LedgerTransaction(nextId++, date, amount, accountId = 1, categoryId = category.id)

    private val pay = transaction(today, 300000, salary)
    private val groceries = transaction(today, -5000, food)
    private val earlierGroceries = transaction(LocalDate.of(2026, 9, 10), -2000, food)

    /** Last year, before the last twelve months */
    private val coat = transaction(LocalDate.of(2025, 7, 1), -3000, clothes)

    private val ledger = Ledger(
        listOf(pay, groceries, earlierGroceries, coat),
        OwnAccounts(emptySet(), emptyMap())
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
        val viewModel: MoneyFlowViewModel,
        val source: FakeLedgerSource,
        val tagFacts: MutableStateFlow<TagFacts>
    ) {
        val flow get() = viewModel.flow.value.ready()
        val lastTwelveMonths get() = viewModel.lastTwelveMonths.value.ready()

        fun AnalysisState<MoneyFlow>.ready(): MoneyFlow =
            valueOrNull ?: throw AssertionError("Not ready: $this")
    }

    private fun withViewModel(
        source: FakeLedgerSource = FakeLedgerSource(
            ledger = ledger,
            catalog = catalog,
            today = today
        ),
        block: suspend Fixture.() -> Unit
    ) = runTest(dispatcher) {
        val tagFacts = MutableStateFlow(TagFacts())
        val viewModel = MoneyFlowViewModel(source, tagFacts, dispatcher)
        // Like shown screens, which keep the analyses running
        backgroundScope.launch { viewModel.flow.collect {} }
        backgroundScope.launch { viewModel.lastTwelveMonths.collect {} }
        try {
            Fixture(viewModel, source, tagFacts).block()
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun loadsUntilTheLedgerIsRead() = withViewModel(
        FakeLedgerSource(catalog = catalog, today = today)
    ) {
        assertSame(AnalysisState.Loading, viewModel.flow.value)
        assertSame(AnalysisState.Loading, viewModel.lastTwelveMonths.value)
        source.ledger.value = ledger
        assertEquals(7000, flow.spent)
    }

    @Test
    fun asksForConsentOrTellsItWasDeclined() = withViewModel(
        FakeLedgerSource(consent = null, ledger = ledger, catalog = catalog, today = today)
    ) {
        assertSame(AnalysisState.AskConsent, viewModel.flow.value)
        assertSame(AnalysisState.AskConsent, viewModel.lastTwelveMonths.value)
        source.setConsent(false)
        assertSame(AnalysisState.Declined, viewModel.flow.value)
        source.setConsent(true)
        assertEquals(300000, flow.earned)
    }

    @Test
    fun showsTheLastTwelveMonths() = withViewModel {
        assertEquals(PeriodKind.TWELVE_MONTHS, flow.period.kind)
        assertEquals(0, flow.offset)
        assertEquals(today, flow.period.end)
        assertEquals(300000, flow.earned)
        assertEquals(7000, flow.spent)
        assertEquals(listOf(10L), flow.expenses.map { it.key })
        assertEquals(flow, lastTwelveMonths)
    }

    @Test
    fun goesToEarlierMonthsAndBackButNotPastTheCurrentOne() = withViewModel {
        viewModel.selectKind(PeriodKind.MONTH)
        assertEquals(LocalDate.of(2026, 10, 1), flow.period.start)
        assertEquals(5000, flow.spent)
        viewModel.showEarlier()
        assertEquals(1, flow.offset)
        assertEquals(LocalDate.of(2026, 9, 1), flow.period.start)
        assertEquals(2000, flow.spent)
        assertEquals(0, flow.earned)
        viewModel.showLater()
        viewModel.showLater()
        assertEquals(0, flow.offset)
        assertEquals(5000, flow.spent)
    }

    @Test
    fun switchesToYearsStartingWithTheCurrentOne() = withViewModel {
        viewModel.selectKind(PeriodKind.YEAR)
        assertEquals(LocalDate.of(2026, 1, 1), flow.period.start)
        assertEquals(0, flow.offset)
        viewModel.showEarlier()
        assertEquals(LocalDate.of(2025, 1, 1), flow.period.start)
        assertEquals(listOf(20L), flow.expenses.map { it.key })
        assertEquals(3000, flow.spent)
        // Another kind starts again with the current period
        viewModel.selectKind(PeriodKind.MONTH)
        assertEquals(0, flow.offset)
    }

    @Test
    fun showLastTwelveMonthsResetsKindAndOffset() = withViewModel {
        viewModel.selectKind(PeriodKind.YEAR)
        viewModel.showEarlier()
        viewModel.showLastTwelveMonths()
        assertEquals(PeriodKind.TWELVE_MONTHS, flow.period.kind)
        assertEquals(0, flow.offset)
    }

    @Test
    fun lastTwelveMonthsStayWhileTheUserPicksAnotherPeriod() = withViewModel {
        viewModel.selectKind(PeriodKind.YEAR)
        viewModel.showEarlier()
        assertEquals(PeriodKind.TWELVE_MONTHS, lastTwelveMonths.period.kind)
        assertEquals(0, lastTwelveMonths.offset)
        assertEquals(7000, lastTwelveMonths.spent)
    }

    @Test
    fun leavesOutWhatTheTagsExcludeFromSpending() = withViewModel {
        tagFacts.value = TagFacts(excludedFromSpending = setOf(groceries.id))
        assertEquals(2000, flow.spent)
        assertEquals(2000, lastTwelveMonths.spent)
    }

    @Test
    fun followsTheDayOfTheLedger() = withViewModel {
        viewModel.selectKind(PeriodKind.MONTH)
        source.today.value = LocalDate.of(2026, 11, 2)
        assertEquals(LocalDate.of(2026, 11, 1), flow.period.start)
        assertTrue(flow.isEmpty)
        assertEquals(LocalDate.of(2026, 11, 2), lastTwelveMonths.period.end)
    }

    @Test
    fun followsNewTransactionsAndCategories() = withViewModel {
        source.ledger.value = Ledger(
            listOf(pay, transaction(today, -1000, clothes)),
            OwnAccounts(emptySet(), emptyMap())
        )
        assertEquals(1000, flow.spent)
        source.catalog.value = Catalog(
            categories = listOf(food, clothes.copy(label = "Mode"), salary)
        )
        assertEquals("Mode", flow.expenses.single().label)
    }
}
