package org.totschnig.myexpenses.next.feature.tags

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.tags.TagRepository
import org.totschnig.myexpenses.next.data.tags.TagRule
import org.totschnig.myexpenses.next.data.tags.TagStore

@OptIn(ExperimentalCoroutinesApi::class)
class TagLearningViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val today = LocalDate.of(2026, 10, 26)

    /** Three of the four payments to the gym have the tag 7 */
    private val ledger = Ledger(
        (1L..4L).map { id ->
            LedgerTransaction(
                id, today, amount = -3000, accountId = 1, payeeId = 5, payeeName = "Fitness Studio",
                tagIds = if (id < 4) setOf(7) else emptySet()
            )
        },
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

    private fun withViewModel(block: suspend (TagLearningViewModel, TagStore, FakeTagDatabase) -> Unit) =
        withTestDataStore { dataStore ->
            runTest(dispatcher) {
                val store = TagStore(dataStore)
                val database = FakeTagDatabase()
                val repository = TagRepository(FakeLedgerSource(ledger = ledger), store, backgroundScope, database)
                block(TagLearningViewModel(repository, database, dispatcher), store, database)
            }
        }

    @Test
    fun suggestsTagOfOtherPaymentsAndGivesItWhenAccepted() = withViewModel { viewModel, _, database ->
        val suggestion = viewModel.suggestions.first { it.isNotEmpty() }.getValue(4)
        assertEquals(7L, suggestion.tagId)
        viewModel.acceptSuggestion(suggestion)
        // Resumed by the flow, the write waits until the test suspends
        yield()
        assertEquals(listOf(7L), database.tags[4])
        assertEquals(suggestion, viewModel.justAccepted.value[4])
        viewModel.clearAccepted(4)
        assertEquals(emptyMap<Long, Any>(), viewModel.justAccepted.value)
    }

    @Test
    fun ruleTagsOnlyLaterPayments() = withViewModel { viewModel, store, _ ->
        val counterpart = viewModel.suggestions.first { it.isNotEmpty() }.getValue(4).counterpart
        viewModel.addRule(counterpart, 7)
        assertEquals(listOf(TagRule(counterpart, 7, afterId = 4)), store.rules.first { it.isNotEmpty() })
    }
}
