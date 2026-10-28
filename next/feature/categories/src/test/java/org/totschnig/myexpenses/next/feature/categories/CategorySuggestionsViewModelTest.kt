package org.totschnig.myexpenses.next.feature.categories

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.categories.CategoryRepository
import org.totschnig.myexpenses.next.data.categories.CategoryRule
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.data.categories.CategoryWriting

@OptIn(ExperimentalCoroutinesApi::class)
class CategorySuggestionsViewModelTest {

    private val today = LocalDate.of(2026, 10, 26)

    /** Two payments to the supermarket without category, from before suggestions were started */
    private val ledger = Ledger(
        listOf(1L, 2L).map { LedgerTransaction(it, today, amount = -2000, accountId = 1, payeeId = 5, payeeName = "REWE") },
        OwnAccounts(emptySet(), emptyMap())
    )

    private val catalog = Catalog(listOf(CategoryInfo(10, "Food", "Food"), CategoryInfo(11, "Drinks", "Drinks")))

    private class FakeWriting : CategoryWriting {
        val set = mutableListOf<Pair<List<Long>, Long>>()
        val removed = mutableListOf<Pair<Long, Long>>()
        override suspend fun changeCategory(transactionId: Long, fromCategoryId: Long?, categoryId: Long) = Unit
        override suspend fun removeCategory(transactionId: Long, categoryId: Long) {
            removed += transactionId to categoryId
        }

        override suspend fun setMissingCategory(transactionIds: List<Long>, categoryId: Long) {
            set += transactionIds to categoryId
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withViewModel(block: suspend (CategorySuggestionsViewModel, CategoryStore, FakeWriting) -> Unit) =
        withTestDataStore { dataStore ->
            runTest {
                val store = CategoryStore(dataStore)
                store.update { it.copy(rules = listOf(CategoryRule("r", setOf(5), 10))) }
                val writing = FakeWriting()
                val repository = CategoryRepository(FakeLedgerSource(ledger = ledger, catalog = catalog), store, writing, backgroundScope)
                block(CategorySuggestionsViewModel(repository), store, writing)
            }
        }

    @Test
    fun appliesBacklogOnlyWhenTheUserAgrees() = withViewModel { viewModel, store, writing ->
        assertEquals(CategorySuggestionsViewModel.Backlog(certain = 2, uncategorized = 2), viewModel.backlog.first { it != null })
        assertEquals(emptyList<Any>(), writing.set)
        viewModel.applyBacklog()
        store.decisions.first { it.backlogAnswered }
        assertEquals(listOf(listOf(1L, 2L) to 10L), writing.set)
    }

    @Test
    fun declinedBacklogStaysSuggestions() = withViewModel { viewModel, store, writing ->
        viewModel.backlog.first { it != null }
        viewModel.declineBacklog()
        store.decisions.first { it.backlogAnswered }
        viewModel.backlog.first { it == null }
        val state = viewModel.stateOf(1, null).first { it is CategorySuggestionsViewModel.CategoryState.Suggested }
        assertEquals("Food", (state as CategorySuggestionsViewModel.CategoryState.Suggested).path)
        assertEquals(emptyList<Any>(), writing.set)
    }

    @Test
    fun offersCategoryForOtherPaymentsOfCounterpart() = withViewModel { viewModel, _, _ ->
        val info = viewModel.counterpartOf(1).first { it != null }!!
        assertEquals(listOf(2L), info.uncategorized)
    }

    @Test
    fun ruleMovesCounterpartToOtherCategory() = withViewModel { viewModel, store, _ ->
        val counterpart = viewModel.counterpartOf(1).first { it != null }!!.counterpart
        viewModel.addRule(counterpart, 11)
        val rules = store.decisions.first { decisions -> decisions.rules.any { it.categoryId == 11L } }.rules
        assertEquals(listOf(11L), rules.map { it.categoryId })
    }

    @Test
    fun undoneAutomaticCategoryIsNotAppliedAgain() = withViewModel { viewModel, store, writing ->
        viewModel.undoAutomatic(1, 10)
        assertEquals(10L, store.decisions.first { 1L in it.rejected }.rejected[1])
        assertEquals(listOf(1L to 10L), writing.removed)
    }
}
