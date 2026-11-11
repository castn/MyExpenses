package org.totschnig.myexpenses.next.feature.debts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.debts.Debt
import org.totschnig.myexpenses.next.data.debts.DebtChoice
import org.totschnig.myexpenses.next.data.debts.DebtDatabase
import org.totschnig.myexpenses.next.data.debts.DebtOutcome
import org.totschnig.myexpenses.next.data.debts.DebtTransaction

/** Keeps debts and transactions in memory; a closed debt refuses links like the database */
private class FakeDebtDatabase : DebtDatabase {
    override val homeCurrency = "EUR"
    val debts = mutableListOf<Debt>()
    val transactions = mutableListOf<DebtTransaction>()
    private val changes = MutableStateFlow(0)

    override fun changes(): Flow<Unit> = changes.map { }

    override fun transaction(transactionId: Long) = transactions.find { it.id == transactionId }

    override fun debt(debtId: Long) = debts.find { it.id == debtId }

    override fun debtsOf(payeeId: Long) = debts.filter { it.payeeId == payeeId }

    override fun paymentsOf(debtIds: Collection<Long>) =
        transactions.filter { it.debtId in debtIds }

    override fun link(transactionId: Long, debtId: Long?) {
        val transaction = transaction(transactionId)!!
        val touched = listOfNotNull(transaction.debtId, debtId).mapNotNull(::debt)
        check(touched.none { it.isSealed }) { "sealed" }
        transactions[transactions.indexOf(transaction)] = transaction.copy(debtId = debtId)
        changes.value++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DebtsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val database = FakeDebtDatabase()
    private val loan = Debt(1, "Loan", 7, "Alice", CurrencyInfo.Preview, 10000)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database.debts += loan
        database.transactions += DebtTransaction(10, 2000, "EUR", 2000, payeeId = 7, debtId = 1)
        database.transactions += DebtTransaction(11, 3000, "EUR", 3000, payeeId = 7)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DebtsViewModel(database, dispatcher)

    @Test
    fun `offers the open debt of the payee with the balance after the payment`() = runTest {
        assertEquals(
            DebtChoice.Offered(listOf(DebtChoice.Linked(loan, DebtOutcome(8000, 3000)))),
            viewModel().choice(11).first()
        )
    }

    @Test
    fun `linking shows the debt, unlinking offers it again`() = runTest {
        val viewModel = viewModel()

        viewModel.link(11, 1)
        assertEquals(DebtChoice.Linked(loan, DebtOutcome(8000, 3000)), viewModel.choice(11).first())

        viewModel.unlink(11)
        assertEquals(
            DebtChoice.Offered(listOf(DebtChoice.Linked(loan, DebtOutcome(8000, 3000)))),
            viewModel.choice(11).first()
        )
    }

    @Test
    fun `a refused change is shown for its transaction until dismissed`() = runTest {
        database.debts[0] = loan.copy(isSealed = true)
        val viewModel = viewModel()

        viewModel.unlink(10)

        assertEquals(10L, viewModel.failedFor.value)
        viewModel.dismissFailure()
        assertNull(viewModel.failedFor.value)
    }

    @Test
    fun `a transaction without payee or gone offers nothing`() = runTest {
        database.transactions += DebtTransaction(12, 100, "EUR", 100)
        assertEquals(DebtChoice.None, viewModel().choice(12).first())
        assertEquals(DebtChoice.None, viewModel().choice(99).first())
    }
}
