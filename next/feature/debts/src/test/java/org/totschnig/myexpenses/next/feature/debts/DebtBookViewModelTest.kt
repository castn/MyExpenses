package org.totschnig.myexpenses.next.feature.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
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
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.debts.Debt
import org.totschnig.myexpenses.next.data.debts.DebtOverviewStore
import org.totschnig.myexpenses.next.data.debts.DebtProblem
import org.totschnig.myexpenses.next.data.debts.DebtSort
import org.totschnig.myexpenses.next.data.debts.DebtTransaction

@OptIn(ExperimentalCoroutinesApi::class)
class DebtBookViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val database = FakeDebtDatabase()
    private val day = LocalDate.of(2025, 3, 1)
    private val loan = Debt(1, "Loan", 0, "Alice", CurrencyInfo.Preview, 10000, date = day)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withViewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        block: suspend (DebtBookViewModel) -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val store = DebtOverviewStore(dataStore)
            val viewModel = DebtBookViewModel(database, database, store, savedState, dispatcher)
            block(viewModel)
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
    }

    private fun payment(id: Long, amount: Long, date: LocalDate) =
        DebtTransaction(id, amount, "EUR", amount, payeeId = 0, debtId = 1, date = date)

    @Test
    fun `overview shows open debts and follows the settings`() = withViewModel { viewModel ->
        database.debts += loan
        database.debts += loan.copy(id = 2, label = "Closed", isSealed = true)
        database.transactions += payment(10, 3000, day)

        val overview = viewModel.overview.filterNotNull().first()
        assertEquals(listOf("Loan"), overview.debts.map { it.debt.label })
        assertEquals(7000L, overview.total)

        viewModel.setShowAll(true)
        viewModel.setSort(DebtSort.Label)
        val all = viewModel.overview.filterNotNull().first { it.debts.size == 2 }
        assertEquals(listOf("Closed", "Loan"), all.debts.map { it.debt.label })
    }

    @Test
    fun `detail shows the history with the running balance`() = withViewModel { viewModel ->
        database.debts += loan
        database.transactions += payment(10, 3000, day.plusDays(5))
        database.transactions += payment(11, -500, day.plusDays(2))

        val detail = viewModel.detail(1).first()!!

        assertEquals(listOf(10000L, 10500L, 7500L), detail.history.map { it.balance })
        assertEquals(7500L, detail.summary.balance.balance)
        assertNull(viewModel.detail(99).first())
    }

    @Test
    fun `a new debt is checked and saved with a new payee`() = withViewModel { viewModel ->
        viewModel.startForm()
        viewModel.saveDraft()
        assertEquals(
            setOf(DebtProblem.Payee, DebtProblem.Label, DebtProblem.Amount),
            viewModel.form.value.problems
        )

        val draft = viewModel.form.value.draft!!
        viewModel.changeDraft(
            draft.copy(payee = "Carol", label = "Bike", amountText = "120", isOwedToMe = false)
        )
        viewModel.saveDraft()

        val saved = database.debts.single()
        assertEquals("Carol", saved.payeeName)
        assertEquals(-12000L, saved.amount)
        assertEquals(saved.id, viewModel.form.value.savedId)
        assertNull(viewModel.form.value.draft)
    }

    @Test
    fun `a foreign debt needs its home value`() = withViewModel { viewModel ->
        viewModel.startForm(payee = "Alice")
        val draft = viewModel.form.value.draft!!
        viewModel.changeDraft(draft.copy(label = "Trip", amountText = "50", currency = "USD"))
        viewModel.saveDraft()
        assertEquals(setOf(DebtProblem.Equivalent), viewModel.form.value.problems)

        viewModel.changeDraft(viewModel.form.value.draft!!.copy(equivalentText = "46,5"))
        viewModel.saveDraft()
        assertEquals(4650L, database.debts.single().equivalentAmount)
    }

    @Test
    fun `the draft survives the end of the app`() {
        val savedState = SavedStateHandle()
        withViewModel(savedState) { viewModel ->
            viewModel.startForm(loan)
            viewModel.changeDraft(viewModel.form.value.draft!!.copy(label = "Changed"))
        }
        withViewModel(savedState) { viewModel ->
            assertEquals("Changed", viewModel.form.value.draft?.label)
            assertEquals("100.00", viewModel.form.value.draft?.amountText)
        }
    }

    @Test
    fun `closing, a refused change and deleting`() = withViewModel { viewModel ->
        database.debts += loan

        viewModel.setSealed(loan, true)
        assertEquals(true, database.debt(1)?.isSealed)

        viewModel.startForm(database.debt(1))
        viewModel.saveDraft()
        assertEquals(DebtMessage.Refused, viewModel.message.value)
        viewModel.messageShown()

        viewModel.delete(loan)
        assertEquals(DebtMessage.Deleted, viewModel.message.value)
        assertNull(database.debts.firstOrNull())
    }
}
