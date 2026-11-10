package org.totschnig.myexpenses.next.feature.categories

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.categories.CategoryDecisions
import org.totschnig.myexpenses.next.data.categories.CategoryRepository
import org.totschnig.myexpenses.next.data.categories.CategoryRule
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.data.categories.CategoryWriting
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.feature.categories.CategorySuggestionsViewModel.Backlog
import org.totschnig.myexpenses.next.feature.categories.CategorySuggestionsViewModel.CategoryState

/** What the user sees after each input and what the actions store or write */
@OptIn(ExperimentalCoroutinesApi::class)
class CategorySuggestionsActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val today = LocalDate.of(2026, 10, 26)

    private fun transaction(
        id: Long,
        amount: Long,
        payeeId: Long? = null,
        payeeName: String? = null,
        categoryId: Long? = null
    ) = LedgerTransaction(
        id,
        today,
        amount,
        accountId = 1,
        payeeId = payeeId,
        payeeName = payeeName,
        categoryId = categoryId
    )

    /**
     * Two payments to the supermarket and a refund from it without category, one payment there
     * with Drinks, a kiosk without history and a payment without payee
     */
    private val ledger = Ledger(
        listOf(
            transaction(1, -2000, 5, "REWE"),
            transaction(2, -2000, 5, "REWE"),
            transaction(3, 500, 5, "REWE"),
            transaction(4, -1000, 5, "REWE", categoryId = 11),
            transaction(6, -300, 7, "Kiosk"),
            transaction(7, -100)
        ),
        OwnAccounts(emptySet(), emptyMap())
    )

    /** Food is no category for income, so the refund gets no suggestion */
    private val catalog = Catalog(
        listOf(
            CategoryInfo(10, "Food", "Food", isIncome = false),
            CategoryInfo(11, "Drinks", "Drinks")
        )
    )

    private val rule = CategoryRule("r", setOf(5), 10)

    private class FakeWriting : CategoryWriting {
        val changed = mutableListOf<Triple<Long, Long?, Long>>()
        val set = mutableListOf<Pair<List<Long>, Long>>()
        val removed = mutableListOf<Pair<Long, Long>>()

        override suspend fun changeCategory(
            transactionId: Long,
            fromCategoryId: Long?,
            categoryId: Long
        ) {
            changed += Triple(transactionId, fromCategoryId, categoryId)
        }

        override suspend fun removeCategory(transactionId: Long, categoryId: Long) {
            removed += transactionId to categoryId
        }

        override suspend fun setMissingCategory(transactionIds: List<Long>, categoryId: Long) {
            set += transactionIds to categoryId
        }
    }

    private class Fixture(
        val viewModel: CategorySuggestionsViewModel,
        val repository: CategoryRepository,
        val store: CategoryStore,
        val writing: FakeWriting,
        val source: FakeLedgerSource
    ) {
        /**
         * Lets the coroutines the view model launched run and their updates of the store, if any,
         * be written: the data store does not keep the order of concurrent updates
         */
        suspend fun flush() {
            withContext(Dispatchers.Default) { delay(200) }
            store.update { it }
        }

        suspend fun decisions(): CategoryDecisions = store.decisions.first()

        /** Waits until the analysis was made with the decisions in the store */
        suspend fun analysed(predicate: (CategoryDecisions) -> Boolean) {
            awaitValue(repository.analysis) { it != null && predicate(it.decisions) }
        }
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
        decisions: CategoryDecisions = CategoryDecisions(rules = listOf(rule)),
        block: suspend Fixture.() -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val store = CategoryStore(dataStore)
            store.update { decisions }
            val writing = FakeWriting()
            val source = FakeLedgerSource(consent = consent, ledger = ledger, catalog = catalog)
            val repository = CategoryRepository(source, store, writing, backgroundScope)
            val viewModel = CategorySuggestionsViewModel(repository)
            try {
                Fixture(viewModel, repository, store, writing, source).block()
            } finally {
                // Its coroutines must not outlive the test dispatcher set as Main
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    private fun suggested(path: String) = { state: CategoryState ->
        (state as? CategoryState.Suggested)?.path == path
    }

    @Test
    fun backlogCountsCertainSuggestionsAndAllTransactionsWithoutCategory() = withViewModel {
        assertEquals(
            Backlog(certain = 2, uncategorized = 5),
            awaitValue(viewModel.backlog) { it != null }
        )
    }

    @Test
    fun listsShowPathsOfOpenSuggestions() = withViewModel {
        assertEquals(
            mapOf(1L to "Food", 2L to "Food"),
            awaitValue(viewModel.suggestedPaths) { it.isNotEmpty() }
        )
    }

    @Test
    fun nothingIsSuggestedUntilConsentIsGiven() = withViewModel(consent = false) {
        assertEquals(CategoryState.None, awaitValue(viewModel.stateOf(1, null)) { true })
        assertNull(awaitValue(viewModel.counterpartOf(1)) { true })
        source.setConsent(true)
        val state = awaitValue(viewModel.stateOf(1, null), suggested("Food"))
        assertEquals(10L, (state as CategoryState.Suggested).suggestion.categoryId)
    }

    @Test
    fun transactionWithoutSuggestionHasNoState() = withViewModel {
        analysed { true }
        assertEquals(CategoryState.None, viewModel.stateOf(6, null).first())
        assertEquals(CategoryState.None, viewModel.stateOf(99, null).first())
    }

    @Test
    fun detailsSayACategoryWasAppliedWithoutAsking() = withViewModel(
        decisions = CategoryDecisions(rules = listOf(rule), automatic = mapOf(4L to 11L))
    ) {
        analysed { 4L in it.automatic }
        assertEquals(CategoryState.Automatic(11, "Drinks"), viewModel.stateOf(4, 11).first())
    }

    @Test
    fun categoryChangedAfterTheAutomaticOneIsNotCalledAutomatic() = withViewModel(
        decisions = CategoryDecisions(rules = listOf(rule), automatic = mapOf(4L to 10L))
    ) {
        analysed { 4L in it.automatic }
        // The analysis knows the transaction has Drinks now, whatever the details loaded
        assertEquals(CategoryState.None, viewModel.stateOf(4, 10).first())
    }

    @Test
    fun stateUsesTheAnalysisOverTheLoadedCategory() = withViewModel {
        analysed { true }
        // The analysis says transaction 1 has no category (e.g. it was removed meanwhile)
        val state = awaitValue(viewModel.stateOf(1, 11), suggested("Food"))
        assertEquals(10L, (state as CategoryState.Suggested).suggestion.categoryId)
    }

    @Test
    fun stateUsesTheLoadedCategoryForTransactionsNotInTheAnalysis() = withViewModel(
        decisions = CategoryDecisions(rules = listOf(rule), automatic = mapOf(99L to 11L))
    ) {
        analysed { 99L in it.automatic }
        assertEquals(CategoryState.Automatic(11, "Drinks"), viewModel.stateOf(99, 11).first())
    }

    @Test
    fun counterpartOffersOnlyOtherPaymentsWithoutCategory() = withViewModel {
        val info = awaitValue(viewModel.counterpartOf(1)) { it != null }!!
        assertEquals(Counterpart("p:5", "REWE", payeeId = 5), info.counterpart)
        // Not the refund (3), not the one with Drinks (4)
        assertEquals(listOf(2L), info.uncategorized)
        assertEquals(emptyList<Long>(), viewModel.counterpartOf(3).first()!!.uncategorized)
        assertNull(viewModel.counterpartOf(7).first())
        assertNull(viewModel.counterpartOf(99).first())
    }

    @Test
    fun acceptSetsTheMissingCategoryOfAllTransactions() = withViewModel {
        viewModel.accept(listOf(1, 2), 11)
        flush()
        assertEquals(listOf(listOf(1L, 2L) to 11L), writing.set)
        assertEquals(CategoryDecisions(rules = listOf(rule)), decisions())
    }

    @Test
    fun justAppliedCategoriesLastUntilTheDetailsAreClosed() = withViewModel {
        viewModel.markApplied(1, 10, "Food")
        viewModel.markApplied(6, 11, "Drinks")
        assertEquals(
            mapOf(1L to (10L to "Food"), 6L to (11L to "Drinks")),
            viewModel.justApplied.value
        )
        viewModel.clearApplied(1)
        assertEquals(mapOf(6L to (11L to "Drinks")), viewModel.justApplied.value)
    }

    @Test
    fun changingAnAutomaticCategoryRejectsIt() = withViewModel(
        decisions = CategoryDecisions(rules = listOf(rule), automatic = mapOf(4L to 11L))
    ) {
        viewModel.change(4, 11, 10)
        flush()
        assertEquals(listOf(Triple<Long, Long?, Long>(4, 11, 10)), writing.changed)
        val decisions = awaitValue(store.decisions) { 4L in it.rejected }
        assertEquals(mapOf(4L to 11L), decisions.rejected)
        assertEquals(emptyMap<Long, Long>(), decisions.automatic)
    }

    @Test
    fun changingAnOwnCategoryOrASuggestionRemembersNothing() = withViewModel(
        decisions = CategoryDecisions(rules = listOf(rule), automatic = mapOf(4L to 10L))
    ) {
        viewModel.change(4, 11, 10)
        viewModel.change(1, null, 11)
        flush()
        assertEquals(
            listOf(
                Triple<Long, Long?, Long>(4, 11, 10),
                Triple<Long, Long?, Long>(1, null, 11)
            ),
            writing.changed
        )
        val decisions = decisions()
        assertEquals(emptyMap<Long, Long>(), decisions.rejected)
        assertEquals(mapOf(4L to 10L), decisions.automatic)
    }

    @Test
    fun rejectedSuggestionIsNotMadeAgain() = withViewModel {
        val state = awaitValue(viewModel.stateOf(1, null), suggested("Food"))
        viewModel.reject((state as CategoryState.Suggested).suggestion)
        assertEquals(mapOf(1L to 10L), awaitValue(store.decisions) { 1L in it.rejected }.rejected)
        assertEquals(mapOf(2L to "Food"), awaitValue(viewModel.suggestedPaths) { 1L !in it })
        assertEquals(CategoryState.None, viewModel.stateOf(1, null).first())
        assertEquals(
            Backlog(certain = 1, uncategorized = 5),
            awaitValue(viewModel.backlog) { it?.certain == 1 }
        )
        assertEquals(emptyList<Any>(), writing.set)
    }

    @Test
    fun appliedBacklogIsRememberedAsAutomatic() = withViewModel {
        awaitValue(viewModel.backlog) { it != null }
        viewModel.applyBacklog()
        val decisions = awaitValue(store.decisions) { it.backlogAnswered }
        assertEquals(mapOf(1L to 10L, 2L to 10L), decisions.automatic)
        assertNull(awaitValue(viewModel.backlog) { it == null })
    }

    @Test
    fun ruleForAnotherPayeeJoinsTheRuleOfTheCategory() = withViewModel {
        assertEquals(CategoryState.None, awaitValue(viewModel.stateOf(6, null)) { true })
        viewModel.addRule(Counterpart("p:7", "Kiosk", payeeId = 7), 10)
        val rules = awaitValue(store.decisions) { 7L in it.rules.single().payeeIds }.rules
        assertEquals(listOf(CategoryRule("r", setOf(5, 7), 10)), rules)
        awaitValue(viewModel.stateOf(6, null), suggested("Food"))
        assertEquals(
            Backlog(certain = 3, uncategorized = 5),
            awaitValue(viewModel.backlog) { it?.certain == 3 }
        )
    }

    @Test
    fun ruleForAMerchantOfAPaymentService() = withViewModel {
        viewModel.addRule(Counterpart("m:zalando", "Zalando SE", merchant = "zalando"), 11)
        val rules = awaitValue(store.decisions) { it.rules.size == 2 }.rules
        assertEquals(rule, rules.first())
        assertEquals(setOf("zalando"), rules.last().merchants)
        assertEquals(emptySet<Long>(), rules.last().payeeIds)
        assertEquals(11L, rules.last().categoryId)
    }

    @Test
    fun noRuleForACounterpartKnownOnlyByName() = withViewModel {
        viewModel.addRule(Counterpart("n:kiosk", "Kiosk"), 11)
        flush()
        assertEquals(listOf(rule), decisions().rules)
    }

    private companion object {
        /** The first value of [flow] that matches, failing after a few seconds of real time */
        suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
            withContext(Dispatchers.Default) { withTimeout(5_000) { flow.first(predicate) } }
    }
}
