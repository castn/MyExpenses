package org.totschnig.myexpenses.next.feature.budgets

import java.time.LocalDate
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.tags.TagFacts
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.data.budgets.BudgetRepository
import org.totschnig.myexpenses.next.data.budgets.BudgetStore
import org.totschnig.myexpenses.next.data.budgets.Pot
import org.totschnig.myexpenses.next.data.budgets.PotKind

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private val giro = 1L
    private val food = CategoryInfo(10, "Essen", "Essen")
    private val facts = ContractFacts(emptyList(), emptyList(), emptyList(), emptySet(), emptyList())
    private val pot = Pot(id = "food", name = "Essen", kind = PotKind.RUNNING, amount = 40000, categoryIds = setOf(food.id))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun computesPotsAndAgainOnlyForANewInput() = withTestDataStore { dataStore ->
        runTest {
            val today = LocalDate.now()
            val transaction = LedgerTransaction(1, today, amount = -2500, accountId = giro, categoryId = food.id)
            val accounts = OwnAccounts(setOf(giro), emptyMap())
            val source = FakeLedgerSource(ledger = Ledger(listOf(transaction), accounts), catalog = Catalog(listOf(food)), today = today)
            val store = BudgetStore(dataStore)
            store.save(pot)
            val viewModel = BudgetViewModel(BudgetRepository(source, MutableStateFlow(facts), MutableStateFlow(TagFacts()), store, backgroundScope), savedState = SavedStateHandle())
            suspend fun spent() = (viewModel.overview.first {
                (it as? AnalysisState.Ready)?.value?.pots?.isNotEmpty() == true
            } as AnalysisState.Ready).value.pots.single().spent

            assertEquals(2500, spent())
            // Read again unchanged, e.g. after the app was in the background
            source.ledger.value = Ledger(listOf(transaction.copy()), accounts)
            assertEquals(2500, spent())
            assertEquals(1, viewModel.selectedMemo.computations)

            store.save(pot.copy(amount = 50000))
            viewModel.overview.first { (it as? AnalysisState.Ready)?.value?.pots?.single()?.planned == 50000L }
            assertEquals(2, viewModel.selectedMemo.computations)
        }
    }

    @Test
    fun switchesToEarlierPeriods() = withTestDataStore { dataStore ->
        runTest {
            val source = FakeLedgerSource(ledger = Ledger(emptyList(), OwnAccounts(setOf(giro), emptyMap())))
            val viewModel = BudgetViewModel(BudgetRepository(source, MutableStateFlow(facts), MutableStateFlow(TagFacts()), BudgetStore(dataStore), backgroundScope), savedState = SavedStateHandle())
            suspend fun offset() = (viewModel.overview.first { it is AnalysisState.Ready } as AnalysisState.Ready).value.offset
            assertEquals(0, offset())
            viewModel.showEarlier()
            viewModel.showEarlier()
            viewModel.showLater()
            viewModel.overview.first { (it as? AnalysisState.Ready)?.value?.offset == 1 }
            viewModel.showCurrent()
            viewModel.overview.first { (it as? AnalysisState.Ready)?.value?.offset == 0 }
        }
    }

    @Test
    fun keepsGrantedPotWhenCreatedAnew() = withTestDataStore { dataStore ->
        runTest {
            val savedState = SavedStateHandle()
            fun viewModel() = BudgetViewModel(
                BudgetRepository(FakeLedgerSource(), MutableStateFlow(facts), MutableStateFlow(TagFacts()), BudgetStore(dataStore), backgroundScope),
                savedState = savedState
            )
            viewModel().grantNewPot()
            val again = viewModel()
            assertEquals(true, again.newPotGranted.value)
            again.newPotOpened()
            assertEquals(false, viewModel().newPotGranted.value)
        }
    }
}
