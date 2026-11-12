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
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.data.analysis.TagAnalysis
import org.totschnig.myexpenses.next.data.tags.TagFacts
import org.totschnig.myexpenses.next.data.tags.TagMeaning

@OptIn(ExperimentalCoroutinesApi::class)
class TagAnalysisViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today: LocalDate = LocalDate.of(2026, 10, 5)

    private val italy = TagInfo(1, "Urlaub Italien")
    private val dog = TagInfo(2, "Hund")
    private val work = TagInfo(3, "Beruflich")
    private val catalog = Catalog(tags = listOf(italy, dog, work))
    private val meanings = mapOf(italy.id to TagMeaning.TRIP, work.id to TagMeaning.BUSINESS)
    private var nextId = 1L

    private fun transaction(date: LocalDate, amount: Long, tag: TagInfo) =
        LedgerTransaction(nextId++, date, amount, accountId = 1, tagIds = setOf(tag.id))

    private fun ledger(vararg transactions: LedgerTransaction) =
        Ledger(transactions.toList(), OwnAccounts(emptySet(), emptyMap()))

    private val ledger = ledger(
        transaction(LocalDate.of(2026, 8, 1), -20000, italy),
        transaction(LocalDate.of(2026, 3, 1), -1000, work),
        transaction(LocalDate.of(2025, 5, 1), -4000, work),
        transaction(LocalDate.of(2026, 10, 2), -1200, dog)
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
        val viewModel: TagAnalysisViewModel,
        val source: FakeLedgerSource,
        val tagFacts: MutableStateFlow<TagFacts>
    ) {
        val analysis: TagAnalysis
            get() = viewModel.analysis.value.let {
                it.valueOrNull ?: throw AssertionError("Not ready: $it")
            }
    }

    private fun withViewModel(
        source: FakeLedgerSource = FakeLedgerSource(
            ledger = ledger,
            catalog = catalog,
            today = today
        ),
        block: suspend Fixture.() -> Unit
    ) = runTest(dispatcher) {
        val tagFacts = MutableStateFlow(TagFacts(meanings = meanings))
        val viewModel = TagAnalysisViewModel(source, tagFacts, dispatcher)
        // Like a shown screen, which keeps the analysis running
        backgroundScope.launch { viewModel.analysis.collect {} }
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
        assertSame(AnalysisState.Loading, viewModel.analysis.value)
        source.ledger.value = ledger
        assertEquals(listOf(italy.id), analysis.trips.map { it.tagId })
    }

    @Test
    fun tellsTheAnalysisIsTurnedOff() = withViewModel(
        FakeLedgerSource(consent = false, ledger = ledger, catalog = catalog, today = today)
    ) {
        assertSame(AnalysisState.Declined, viewModel.analysis.value)
        source.setConsent(true)
        assertEquals(listOf(dog.id), analysis.themes.map { it.tagId })
    }

    @Test
    fun showsTripsThemesAndBusinessOfTheLatestYear() = withViewModel {
        assertEquals(20000, analysis.trips.single().total)
        assertEquals(1200, analysis.themes.single().total)
        assertEquals(listOf(2026, 2025), analysis.businessYears)
        assertEquals(2026, analysis.year)
        assertEquals(1000, analysis.business.single().total)
    }

    @Test
    fun selectYearShowsTheBusinessExpensesOfThatYear() = withViewModel {
        viewModel.selectYear(2025)
        assertEquals(2025, analysis.year)
        assertEquals(4000, analysis.business.single().total)
        // The other groups do not depend on the year
        assertEquals(20000, analysis.trips.single().total)
        assertEquals(1200, analysis.themes.single().total)
    }

    @Test
    fun selectYearWithoutBusinessExpensesShowsNone() = withViewModel {
        viewModel.selectYear(2020)
        assertEquals(2020, analysis.year)
        assertTrue(analysis.business.isEmpty())
        assertEquals(listOf(2026, 2025), analysis.businessYears)
    }

    @Test
    fun followsTheMeaningsOfTheTags() = withViewModel {
        tagFacts.value = TagFacts(meanings = mapOf(work.id to TagMeaning.BUSINESS))
        // Without meaning the trip is a theme like any other tag
        assertTrue(analysis.trips.isEmpty())
        assertEquals(listOf(italy.id, dog.id), analysis.themes.map { it.tagId })
    }

    @Test
    fun followsNewTransactionsAndTags() = withViewModel {
        source.ledger.value = ledger(transaction(LocalDate.of(2026, 9, 1), -500, italy))
        assertEquals(500, analysis.trips.single().total)
        assertTrue(analysis.business.isEmpty())
        source.catalog.value = Catalog(tags = listOf(italy.copy(label = "Toskana")))
        assertEquals("Toskana", analysis.trips.single().label)
    }

    @Test
    fun followsTheDayOfTheLedger() = withViewModel {
        // A year later the theme is older than the twelve months it is evaluated in
        source.today.value = today.plusYears(1)
        assertTrue(analysis.themes.isEmpty())
        assertEquals(20000, analysis.trips.single().total)
    }
}
