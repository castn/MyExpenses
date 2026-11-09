package org.totschnig.myexpenses.next.feature.tags

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
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
import org.totschnig.myexpenses.next.data.tags.TagMeaning
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
                id,
                today,
                amount = -3000,
                accountId = 1,
                payeeId = 5,
                payeeName = "Fitness Studio",
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

    private fun withViewModel(
        block: suspend (TagLearningViewModel, TagStore, FakeTagDatabase) -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val store = TagStore(dataStore)
            val database = FakeTagDatabase()
            val repository =
                TagRepository(FakeLedgerSource(ledger = ledger), store, backgroundScope, database)
            val viewModel = TagLearningViewModel(repository, database, dispatcher)
            try {
                block(viewModel, store, database)
            } finally {
                // Its coroutines must not outlive the test dispatcher set as Main
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun suggestsTagOfOtherPaymentsAndGivesItWhenAccepted() =
        withViewModel { viewModel, _, database ->
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
        assertEquals(
            listOf(TagRule(counterpart, 7, afterId = 4)),
            store.rules.first { it.isNotEmpty() }
        )
    }

    @Test
    fun rejectedSuggestionIsNotMadeAgain() = withViewModel { viewModel, store, _ ->
        val suggestion = viewModel.suggestions.first { it.isNotEmpty() }.getValue(4)
        viewModel.rejectSuggestion(suggestion)
        viewModel.suggestions.first { it.isEmpty() }
        assertEquals(
            setOf(suggestion.key),
            store.rejected(TagStore.Rejected.SUGGESTIONS).first()
        )
        assertEquals(emptyMap<Long, Any>(), viewModel.justAccepted.value)
    }

    @Test
    fun tagsWithMeaningOfTheSituationAreNotSuggested() = withViewModel { viewModel, store, _ ->
        viewModel.suggestions.first { it.isNotEmpty() }
        store.setMeaning(7, TagMeaning.TRIP)
        viewModel.suggestions.first { it.isEmpty() }
        store.setMeaning(7, TagMeaning.BUSINESS)
        assertEquals(7L, viewModel.suggestions.first { it.isNotEmpty() }.getValue(4).tagId)
    }

    @Test
    fun givesTagToOtherTransactionsKeepingTheirTags() = withViewModel { viewModel, _, database ->
        database.tags[2] = listOf(3)
        viewModel.addTag(listOf(2, 4), 9)
        assertEquals(listOf(3L, 9L), database.tags[2])
        assertEquals(listOf(9L), database.tags[4])
    }

    @Test
    fun acceptedSuggestionsAreKeptPerTransaction() = withViewModel { viewModel, _, _ ->
        val suggestion = viewModel.suggestions.first { it.isNotEmpty() }.getValue(4)
        val other = suggestion.copy(transactionId = 8)
        viewModel.acceptSuggestion(suggestion)
        viewModel.acceptSuggestion(other)
        assertEquals(mapOf(4L to suggestion, 8L to other), viewModel.justAccepted.value)
        viewModel.clearAccepted(4)
        assertEquals(mapOf(8L to other), viewModel.justAccepted.value)
    }
}
