package org.totschnig.myexpenses.next.feature.tags

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.totschnig.myexpenses.next.data.tags.TagRepository
import org.totschnig.myexpenses.next.data.tags.TagStore

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withViewModel(database: FakeTagDatabase, block: suspend (TagsViewModel) -> Unit) =
        withTestDataStore { dataStore ->
            runTest(dispatcher) {
                val catalog = Catalog(categories = listOf(CategoryInfo(10, "Café", "Essen:Café")))
                val repository = TagRepository(FakeLedgerSource(catalog = catalog), TagStore(dataStore), backgroundScope, database)
                block(TagsViewModel(repository, database, dispatcher))
            }
        }

    @Test
    fun createsTagAndGivesItsId() {
        val database = FakeTagDatabase()
        withViewModel(database) { viewModel ->
            val id = CompletableDeferred<Long>()
            viewModel.createTag("Urlaub") { id.complete(it) }
            assertEquals(database.labels.getValue("Urlaub"), id.await())
        }
    }

    @Test
    fun setsTagsOfTransaction() {
        val database = FakeTagDatabase()
        withViewModel(database) { viewModel ->
            viewModel.setTags(1, setOf(3, 4))
            assertEquals(listOf(3L, 4L), database.tags[1])
        }
    }

    @Test
    fun findsCategoryIgnoringCaseAndAccents() = withViewModel(FakeTagDatabase()) { viewModel ->
        assertEquals("Café", viewModel.categoryNamed("cafe"))
        assertNull(viewModel.categoryNamed("Kino"))
    }
}
