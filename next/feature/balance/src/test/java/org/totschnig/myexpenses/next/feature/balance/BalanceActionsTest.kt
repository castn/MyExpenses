package org.totschnig.myexpenses.next.feature.balance

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.balance.BalanceOverview
import org.totschnig.myexpenses.next.data.balance.BalancePeriod
import org.totschnig.myexpenses.next.data.balance.BalanceRepository
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractDirection
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction

/** What the balance shows after each input of the ledger, the contracts and the day */
@OptIn(ExperimentalCoroutinesApi::class)
class BalanceActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val today = LocalDate.of(2026, 10, 26)
    private val giro = 1L
    private val savingsAccount = 3L
    private val accounts = OwnAccounts(setOf(giro), emptyMap())

    private val salaryDay = LocalDate.of(2026, 10, 1)

    /** Salary of 3000.00, paid on the first of September and October */
    private val salary = contract(
        id = 10,
        amount = 300000,
        dates = listOf(salaryDay.minusMonths(1), salaryDay),
        direction = ContractDirection.INCOME
    )

    /** Rent of 800.00, debited on the 28th, the next one still to come this period */
    private val rent = contract(
        id = 20,
        amount = -80000,
        dates = listOf(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 28)),
        direction = ContractDirection.EXPENSE
    )

    /** A purchase of 25.00 that belongs to no contract */
    private val purchase = onGiro(30, today.minusDays(2), -2500)

    private val ledger = Ledger(listOf(onGiro(11, salaryDay, 300000), purchase), accounts)

    private val noContracts =
        ContractFacts(emptyList(), emptyList(), emptyList(), emptySet(), emptyList())

    private val salaryAndRent = ContractFacts(
        active = listOf(salary, rent),
        confirmed = listOf(rent),
        salaries = listOf(salary),
        paymentIds = setOf(10L, 11L, 20L, 21L),
        transactions = salary.transactions + rent.transactions
    )

    private fun onGiro(id: Long, date: LocalDate, amount: Long) =
        LedgerTransaction(id, date, amount = amount, accountId = giro)

    private fun contract(
        id: Long,
        amount: Long,
        dates: List<LocalDate>,
        direction: ContractDirection
    ) = Contract(
        signature = "s$id",
        name = "c$id",
        interval = ContractInterval.MONTHLY,
        transactions = dates.mapIndexed { i, date ->
            ContractTransaction(id = id + i, date = date, amount = amount, accountId = giro)
        },
        nextExpectedDate = dates.last().plusMonths(1),
        isActive = true,
        direction = direction,
        isConfirmed = true,
        reserveChoice = false
    )

    private class Fixture(
        val viewModel: BalanceViewModel,
        val source: FakeLedgerSource,
        val contracts: MutableStateFlow<ContractFacts?>
    ) {
        suspend fun ready(predicate: (BalanceOverview) -> Boolean = { true }): BalanceOverview {
            val state = awaitValue(viewModel.balance) {
                it is AnalysisState.Ready && predicate(it.value)
            }
            return (state as AnalysisState.Ready).value
        }

        /** Lets the flows run, if they emit at all */
        suspend fun settle() = withContext(Dispatchers.Default) { delay(200) }
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
        ledger: Ledger? = this.ledger,
        contracts: ContractFacts? = salaryAndRent,
        block: suspend Fixture.() -> Unit
    ) = runTest(dispatcher) {
        val source = FakeLedgerSource(consent = consent, ledger = ledger, today = today)
        val contractFlow = MutableStateFlow(contracts)
        val repository = BalanceRepository(source, contractFlow, backgroundScope)
        val viewModel = BalanceViewModel(repository)
        try {
            Fixture(viewModel, source, contractFlow).block()
        } finally {
            // Its coroutines must not outlive the test dispatcher set as Main
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
    }

    @Test
    fun loadsBeforeAnyoneLooks() = withViewModel {
        assertEquals(AnalysisState.Loading, viewModel.balance.value)
    }

    @Test
    fun showsTheBalanceOnceTheAnalysisIsTurnedOn() = withViewModel(consent = false) {
        assertEquals(
            AnalysisState.Declined,
            awaitValue(viewModel.balance) { it == AnalysisState.Declined }
        )
        source.setConsent(true)
        assertEquals(today, ready().today)
    }

    @Test
    fun showsDeclinedWhenConsentIsWithdrawn() = withViewModel {
        ready()
        source.setConsent(false)
        assertEquals(
            AnalysisState.Declined,
            awaitValue(viewModel.balance) { it == AnalysisState.Declined }
        )
    }

    @Test
    fun loadsUntilTheContractsAreKnown() = withViewModel(contracts = null) {
        settle()
        assertEquals(AnalysisState.Loading, viewModel.balance.value)
        contracts.value = salaryAndRent
        assertTrue(ready().balance.period.isSalaryCycle)
    }

    @Test
    fun loadsUntilTheLedgerIsKnown() = withViewModel(ledger = null) {
        settle()
        assertEquals(AnalysisState.Loading, viewModel.balance.value)
        source.ledger.value = ledger
        assertEquals(listOf(30L), ready().balance.otherTransactionIds)
    }

    @Test
    fun runsFromSalaryToSalaryWithTheRentStillToCome() = withViewModel {
        val overview = ready()
        val balance = overview.balance
        assertEquals(today, overview.today)
        assertEquals(BalancePeriod(salaryDay, salaryDay.plusMonths(1), true), balance.period)
        assertEquals(300000L, balance.incomeBooked)
        assertEquals(listOf(11L), balance.incomeTransactionIds)
        assertEquals(0L, balance.contractsBooked)
        assertEquals(-80000L, balance.contractsUpcoming)
        assertEquals(-2500L, balance.other)
        assertEquals(listOf(30L), balance.otherTransactionIds)
        // 3000.00 - 800.00 - 25.00
        assertEquals(217500L, balance.available)
    }

    @Test
    fun runsOverTheCalendarMonthWithoutSalary() = withViewModel(contracts = noContracts) {
        val balance = ready().balance
        assertEquals(
            BalancePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), false),
            balance.period
        )
        assertEquals(0L, balance.contractsUpcoming)
    }

    @Test
    fun followsNewContracts() = withViewModel(contracts = noContracts) {
        assertFalse(ready().balance.period.isSalaryCycle)
        contracts.value = salaryAndRent
        val balance = ready { it.balance.period.isSalaryCycle }.balance
        assertEquals(-80000L, balance.contractsUpcoming)
    }

    @Test
    fun countsTheRentAsBookedOnceItIsDebited() = withViewModel {
        ready()
        val debit = onGiro(22, LocalDate.of(2026, 10, 28), -80000)
        val debited = rent.copy(
            transactions = rent.transactions + ContractTransaction(
                id = 22,
                date = debit.date,
                amount = -80000,
                accountId = giro
            ),
            nextExpectedDate = LocalDate.of(2026, 11, 28)
        )
        source.today.value = debit.date
        source.ledger.value = Ledger(ledger.transactions + debit, accounts)
        contracts.value = salaryAndRent.copy(
            active = listOf(salary, debited),
            confirmed = listOf(debited)
        )
        val balance = ready { it.balance.contractsBooked != 0L }.balance
        assertEquals(-80000L, balance.contractsBooked)
        assertEquals(0L, balance.contractsUpcoming)
        assertEquals(listOf(30L), balance.otherTransactionIds)
    }

    @Test
    fun movesToTheNextPeriodWhenTheSalaryArrives() = withViewModel {
        ready()
        val nextSalaryDay = salaryDay.plusMonths(1)
        val paid = salary.copy(
            transactions = salary.transactions + ContractTransaction(
                id = 12,
                date = nextSalaryDay,
                amount = 300000,
                accountId = giro
            ),
            nextExpectedDate = nextSalaryDay.plusMonths(1)
        )
        source.today.value = nextSalaryDay
        source.ledger.value =
            Ledger(ledger.transactions + onGiro(12, nextSalaryDay, 300000), accounts)
        contracts.value = salaryAndRent.copy(salaries = listOf(paid))
        val overview = ready { it.balance.period.start == nextSalaryDay }
        assertEquals(nextSalaryDay, overview.today)
        assertEquals(nextSalaryDay.plusMonths(1), overview.balance.period.end)
        // The purchase of the last period no longer counts
        assertEquals(emptyList<Long>(), overview.balance.otherTransactionIds)
        assertEquals(listOf(12L), overview.balance.incomeTransactionIds)
    }

    @Test
    fun expectsALateSalaryTomorrow() = withViewModel {
        ready()
        val late = LocalDate.of(2026, 11, 3)
        source.today.value = late
        val overview = ready { it.today == late }
        assertEquals(BalancePeriod(salaryDay, late.plusDays(1), true), overview.balance.period)
    }

    @Test
    fun countsMoneyMovedToTheSavingsAccountAsReserve() = withViewModel {
        ready()
        val saved = LedgerTransaction(
            40,
            today.minusDays(1),
            amount = -10000,
            accountId = giro,
            targetAccountId = savingsAccount
        )
        source.ledger.value = Ledger(ledger.transactions + saved, accounts)
        val balance = ready { it.balance.savings != 0L }.balance
        assertEquals(-10000L, balance.savings)
        assertEquals(listOf(40L), balance.savingsTransactionIds)
        assertEquals(listOf(30L), balance.otherTransactionIds)
    }

    private companion object {
        suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
            withContext(Dispatchers.Default) { withTimeout(5_000) { flow.first(predicate) } }
    }
}
