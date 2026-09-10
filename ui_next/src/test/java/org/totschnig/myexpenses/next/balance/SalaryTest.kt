package org.totschnig.myexpenses.next.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.contracts.Contract
import org.totschnig.myexpenses.next.contracts.ContractDirection
import org.totschnig.myexpenses.next.contracts.ContractInterval
import org.totschnig.myexpenses.next.contracts.ContractTransaction
import java.time.LocalDate

class SalaryTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 25)

    private fun income(
        signature: String,
        amount: Long,
        interval: ContractInterval = ContractInterval.MONTHLY,
        isActive: Boolean = true,
    ) = Contract(
        signature = signature,
        name = signature,
        interval = interval,
        transactions = listOf(ContractTransaction(id = 1, date = today.minusDays(5), amount = amount, accountId = 1)),
        nextExpectedDate = today.minusDays(5).plus(interval.step),
        isActive = isActive,
        direction = ContractDirection.INCOME
    )

    private val salary = income("salary", 300000)
    private val rent = income("rent", 400000)
    private val childBenefit = income("child", 25000)
    private val bonus = income("bonus", 500000, interval = ContractInterval.YEARLY)
    private val oldJob = income("oldJob", 600000, isActive = false)
    private val incomes = listOf(salary, rent, childBenefit, bonus, oldJob)

    @Test
    fun automaticChoosesHighestActiveMonthlyIncome() {
        assertEquals(rent, incomes.salary(SalaryChoice.Automatic))
    }

    @Test
    fun userCanChooseSalary() {
        assertEquals(salary, incomes.salary(SalaryChoice.Fixed("salary")))
    }

    @Test
    fun userCanChooseNoSalary() {
        assertNull(incomes.salary(SalaryChoice.None))
    }

    @Test
    fun chosenIncomeNoLongerReceivedFallsBackToAutomatic() {
        assertEquals(rent, incomes.salary(SalaryChoice.Fixed("oldJob")))
        assertEquals(rent, incomes.salary(SalaryChoice.Fixed("dismissed")))
    }
}
