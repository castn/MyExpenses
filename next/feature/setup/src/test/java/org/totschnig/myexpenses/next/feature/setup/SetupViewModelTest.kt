package org.totschnig.myexpenses.next.feature.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.data.categories.CategoryWriting
import org.totschnig.myexpenses.next.data.setup.Pace
import org.totschnig.myexpenses.next.data.setup.SetupOffer
import org.totschnig.myexpenses.next.data.setup.SetupRepository
import org.totschnig.myexpenses.next.data.setup.SetupStep
import org.totschnig.myexpenses.next.data.setup.SetupStore

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val today = LocalDate.of(2026, 11, 12)

    private fun debit(id: Long, payeeId: Long, payee: String, amount: Long) =
        LedgerTransaction(id, today, -amount, accountId = 1, payeeId = payeeId, payeeName = payee)

    private val ledger = Ledger(
        listOf(debit(1, 1, "Vermieter", 600), debit(2, 2, "REWE", 300), debit(3, 3, "Kino", 100)),
        OwnAccounts(emptySet(), emptyMap())
    )

    private class FakeWriting : CategoryWriting {
        val set = mutableListOf<Pair<List<Long>, Long>>()
        override suspend fun changeCategory(
            transactionId: Long,
            fromCategoryId: Long?,
            categoryId: Long
        ) = Unit
        val removed = mutableListOf<Pair<Long, Long>>()
        override suspend fun removeCategory(transactionId: Long, categoryId: Long) {
            removed += transactionId to categoryId
        }
        override suspend fun setMissingCategory(transactionIds: List<Long>, categoryId: Long) {
            set += transactionIds to categoryId
        }
    }

    private class Setup(
        val viewModel: SetupViewModel,
        val writing: FakeWriting,
        val store: SetupStore
    )

    private fun CoroutineScope.setup(
        dataStore: DataStore<Preferences>,
        savedState: SavedStateHandle = SavedStateHandle(),
        clock: () -> Long = { 0 },
        ledger: Ledger = this@SetupViewModelTest.ledger
    ): Setup {
        val catalog = Catalog(listOf(CategoryInfo(10, "Wohnen", "Wohnen")))
        val source = FakeLedgerSource(ledger = ledger, catalog = catalog, today = today)
        val writing = FakeWriting()
        val categories = CategoryRepository(source, CategoryStore(dataStore), writing, this)
        val store = SetupStore(dataStore)
        val repository = SetupRepository(source, categories, store, this)
        val viewModel = SetupViewModel(repository, savedState, clock)
        return Setup(viewModel, writing, store)
    }

    @Test
    fun decidesOnTheCardShownAndMeasuresTheTime() = withTestDataStore { dataStore ->
        runTest {
            var now = 1000L
            val setup = backgroundScope.setup(dataStore, clock = { now })
            val viewModel = setup.viewModel
            viewModel.categories.launchIn(backgroundScope)
            val card = viewModel.categories.first { it?.card != null }!!.card!!
            assertEquals("p:1", card.key)
            viewModel.cardShown(card)
            now += 5000
            viewModel.pickCategory(card)
            viewModel.picked(10)
            // The next card comes at once, before the ledger knows the category
            assertEquals("p:2", viewModel.categories.first { it?.card?.key != "p:1" }!!.card!!.key)
            assertEquals(listOf(listOf(1L) to 10L), setup.writing.set)
            assertEquals(Pace(1, 5000), setup.store.progress.first { it.pace.decisions == 1 }.pace)
        }
    }

    @Test
    fun putsOffAndMarksPayeesAsUnclear() = withTestDataStore { dataStore ->
        runTest {
            val viewModel = backgroundScope.setup(dataStore).viewModel
            viewModel.categories.launchIn(backgroundScope)
            val first = viewModel.categories.first { it?.card != null }!!.card!!
            viewModel.leave(first, unclear = false)
            val second = viewModel.categories.first { it?.card?.key == "p:2" }!!.card!!
            viewModel.leave(second, unclear = true)
            // Only the payee put off is left up to the target
            val step = viewModel.categories.first { it?.card?.key == "p:3" }!!
            assertEquals(true, step.hasSkipped)
            viewModel.goOn()
            assertEquals("p:1", viewModel.categories.first { it?.card?.key == "p:1" }!!.card!!.key)
        }
    }

    @Test
    fun goesBackOneDecisionAfterTheOther() = withTestDataStore { dataStore ->
        runTest {
            val setup = backgroundScope.setup(dataStore)
            val viewModel = setup.viewModel
            viewModel.categories.launchIn(backgroundScope)
            val rent = viewModel.categories.first { it?.card != null }!!.card!!
            assertEquals(false, viewModel.categories.value!!.canGoBack)
            viewModel.pickCategory(rent)
            viewModel.picked(10)
            val rewe = viewModel.categories.first { it?.card?.key == "p:2" }!!.card!!
            viewModel.leave(rewe, unclear = true)
            // The target is reached without the payee
            viewModel.categories.first { it?.isTargetReached == true }
            viewModel.back()
            val again = viewModel.categories.first { it?.card?.key == "p:2" }!!
            assertEquals(null, again.card!!.suggestion)
            assertEquals(true, again.canGoBack)
            viewModel.back()
            // The rent comes first again, with the category chosen before
            val first = viewModel.categories.first { it?.card?.key == "p:1" }!!
            assertEquals(10L, first.card!!.suggestion)
            assertEquals(false, first.canGoBack)
            assertEquals(listOf(1L to 10L), setup.writing.removed)
        }
    }

    @Test
    fun assignsSomeDebitsAndKeepsTheCard() = withTestDataStore { dataStore ->
        runTest {
            val setup = backgroundScope.setup(dataStore)
            val viewModel = setup.viewModel
            viewModel.categories.launchIn(backgroundScope)
            val rent = viewModel.categories.first { it?.card != null }!!.card!!
            viewModel.pickCategory(rent, transactionIds = listOf(1L, 99L))
            viewModel.picked(10)
            // Only debits still open of the payee, and its card stays for the others
            viewModel.categories.first { it?.canGoBack == true }
            assertEquals(listOf(listOf(1L) to 10L), setup.writing.set)
            assertEquals("p:1", viewModel.categories.value!!.card!!.key)
            assertEquals(0, setup.store.progress.first().pace.decisions)
            viewModel.back()
            // Going back offers no category for the whole payee
            val again = viewModel.categories.first { it?.canGoBack == false }!!
            assertEquals(null, again.card!!.suggestion)
            assertEquals(listOf(1L to 10L), setup.writing.removed)
        }
    }

    @Test
    fun keepsCashWithTheCategoryPicked() = withTestDataStore { dataStore ->
        runTest {
            val cash = debit(2, 2, "Sparkasse", 400).copy(methodLabel = "BARGELDAUSZAHLUNG")
            val ledger = Ledger(listOf(debit(1, 1, "Vermieter", 600), cash), ledger.ownAccounts)
            val setup = backgroundScope.setup(dataStore, ledger = ledger)
            val viewModel = setup.viewModel
            viewModel.categories.launchIn(backgroundScope)
            val rent = viewModel.categories.first { it?.card != null }!!.card!!
            viewModel.leave(rent, unclear = false)
            viewModel.categories.first { it?.cashCard != null }
            viewModel.pickCashCategory()
            viewModel.picked(10)
            val card = viewModel.categories.first { it?.cashCard?.categoryId == 10L }!!.cashCard!!
            // Nothing is written until the user decides
            assertEquals(emptyList<Pair<List<Long>, Long>>(), setup.writing.set)
            viewModel.keepCash(card, balance = null)
            viewModel.categories.first { it?.cashCard == null }
            assertEquals(listOf(listOf(2L) to 10L), setup.writing.set)
            viewModel.back()
            val again = viewModel.categories.first { it?.cashCard != null }!!.cashCard!!
            assertEquals(10L, again.categoryId)
            assertEquals(listOf(2L to 10L), setup.writing.removed)
        }
    }

    @Test
    fun keepsTheVisitWhenTheProcessEnds() = withTestDataStore { dataStore ->
        runTest {
            val savedState = SavedStateHandle()
            val before = backgroundScope.setup(dataStore, savedState).viewModel
            before.categories.launchIn(backgroundScope)
            val card = before.categories.first { it?.card != null }!!.card!!
            before.pickCategory(card)
            before.setAlways(false)
            // The picker returns to a new view model
            val after = backgroundScope.setup(dataStore, savedState)
            after.viewModel.categories.launchIn(backgroundScope)
            after.viewModel.categories.first { it != null }
            after.viewModel.picked(10)
            assertEquals(listOf(listOf(1L) to 10L), after.writing.set)
            assertEquals(false, after.viewModel.always.value)
        }
    }

    @Test
    fun offersTheSetupBigThenSmallUntilClosed() = withTestDataStore { dataStore ->
        runTest {
            val viewModel = backgroundScope.setup(dataStore).viewModel
            viewModel.offer.launchIn(backgroundScope)
            assertEquals(SetupOffer.FULL, viewModel.offer.first { it == SetupOffer.FULL })
            viewModel.opened()
            assertEquals(SetupOffer.COMPACT, viewModel.offer.first { it == SetupOffer.COMPACT })
            // Put away until the next start of the app
            viewModel.dismissCard()
            assertEquals(SetupOffer.HIDDEN, viewModel.offer.first { it == SetupOffer.HIDDEN })
            viewModel.close()
            assertEquals(SetupOffer.HIDDEN, viewModel.offer.value)
        }
    }

    @Test
    fun remembersSkippedAndFinishedSteps() = withTestDataStore { dataStore ->
        runTest {
            val setup = backgroundScope.setup(dataStore)
            setup.viewModel.endStep(SetupStep.CATEGORIES, skipped = true)
            setup.viewModel.endStep(SetupStep.CONTRACTS, skipped = false)
            setup.viewModel.close()
            val progress = setup.store.progress.first { it.closed }
            assertEquals(setOf(SetupStep.CATEGORIES), progress.skipped)
            assertEquals(setOf(SetupStep.CONTRACTS), progress.finished)
        }
    }
}
