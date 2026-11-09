package org.totschnig.myexpenses.next.feature.tags

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.data.tags.TagRepository
import org.totschnig.myexpenses.next.data.tags.TagStore

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val catalog = Catalog(
        categories = listOf(CategoryInfo(10, "Café", "Essen:Café")),
        tags = listOf(TagInfo(3, "Hochzeit"), TagInfo(4, "Erstattet"), TagInfo(5, "Zurück"))
    )

    private val transaction = ShownTransaction(1, -2500, CurrencyInfo.Preview)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val viewModel: TagsViewModel,
        val store: TagStore,
        val database: FakeTagDatabase,
        val source: FakeLedgerSource
    )

    private fun withViewModel(block: suspend Fixture.() -> Unit) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val source = FakeLedgerSource(catalog = catalog)
            val store = TagStore(dataStore)
            val database = FakeTagDatabase()
            val repository = TagRepository(source, store, backgroundScope, database)
            val viewModel = TagsViewModel(repository, database, dispatcher)
            // Like a shown screen
            backgroundScope.launch { viewModel.tags.collect {} }
            backgroundScope.launch { viewModel.meanings.collect {} }
            try {
                Fixture(viewModel, store, database, source).block()
            } finally {
                // Its coroutines must not outlive the test dispatcher set as Main
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun createsTagAndGivesItsId() = withViewModel {
        val id = CompletableDeferred<Long>()
        viewModel.createTag("Urlaub") { id.complete(it) }
        assertEquals(database.labels.getValue("Urlaub"), id.await())
    }

    @Test
    fun setsTagsOfTransaction() = withViewModel {
        viewModel.setTags(1, setOf(3, 4))
        assertEquals(listOf(3L, 4L), database.tags[1])
    }

    @Test
    fun findsCategoryIgnoringCaseAndAccents() = withViewModel {
        assertEquals("Café", viewModel.categoryNamed("cafe"))
        assertNull(viewModel.categoryNamed("Kino"))
    }

    @Test
    fun showsTagsOfCatalogAndFollowsIt() = withViewModel {
        assertEquals(catalog.tags, viewModel.tags.value)
        source.catalog.value = catalog.copy(tags = listOf(TagInfo(6, "Umzug")))
        viewModel.tags.first { it == listOf(TagInfo(6, "Umzug")) }
    }

    @Test
    fun storesAndShowsMeaningAndThemeRemovesIt() = withViewModel {
        viewModel.setMeaning(3, TagMeaning.TRIP)
        assertEquals(mapOf(3L to TagMeaning.TRIP), viewModel.meanings.first { it.isNotEmpty() })
        viewModel.setMeaning(3, null)
        viewModel.meanings.first { it.isEmpty() }
        assertEquals(emptyMap<Long, TagMeaning>(), store.meanings.first())
    }

    @Test
    fun marksWithTheTagThatHasTheMeaning() = withViewModel {
        store.setMeaning(4, TagMeaning.REIMBURSED)
        database.tags[1] = listOf(3)
        viewModel.setMarked(transaction, TagMeaning.REIMBURSED, marked = true, label = "Erstattet")
        database.writes.first { it > 0 }
        assertEquals(listOf(3L, 4L), database.tags[1])
        assertEquals(emptyMap<String, Long>(), database.labels)
    }

    @Test
    fun marksWithNewTagWhenNoneHasTheMeaning() = withViewModel {
        database.tags[1] = listOf(3)
        viewModel.setMarked(transaction, TagMeaning.ONE_OFF, marked = true, label = "Einmalig")
        database.writes.first { it > 0 }
        val tagId = database.labels.getValue("Einmalig")
        assertEquals(listOf(3L, tagId), database.tags[1])
        assertEquals(TagMeaning.ONE_OFF, store.meanings.first()[tagId])
    }

    @Test
    fun marksWithNewTagWhenTheTagWithTheMeaningWasDeleted() = withViewModel {
        // Tag 99 is not in the catalog any more
        store.setMeaning(99, TagMeaning.BUSINESS)
        viewModel.setMarked(transaction, TagMeaning.BUSINESS, marked = true, label = "Beruflich")
        database.writes.first { it > 0 }
        val tagId = database.labels.getValue("Beruflich")
        assertEquals(listOf(tagId), database.tags[1])
        assertEquals(
            mapOf(99L to TagMeaning.BUSINESS, tagId to TagMeaning.BUSINESS),
            store.meanings.first()
        )
    }

    @Test
    fun unmarkingRemovesAllTagsWithTheMeaning() = withViewModel {
        store.setMeaning(4, TagMeaning.REIMBURSED)
        store.setMeaning(5, TagMeaning.REIMBURSED)
        database.tags[1] = listOf(3, 4, 5)
        viewModel.setMarked(transaction, TagMeaning.REIMBURSED, marked = false, label = "Erstattet")
        database.writes.first { it > 0 }
        assertEquals(listOf(3L), database.tags[1])
        assertEquals(emptyMap<String, Long>(), database.labels)
    }
}
