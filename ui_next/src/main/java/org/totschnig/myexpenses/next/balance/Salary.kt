package org.totschnig.myexpenses.next.balance

import org.totschnig.myexpenses.next.contracts.Contract
import org.totschnig.myexpenses.next.contracts.ContractInterval

/**
 * Which regular income is the salary, it determines the period of the monthly balance
 */
sealed interface SalaryChoice {
    /** The highest active monthly income */
    data object Automatic : SalaryChoice

    /** No salary, the balance uses the calendar month */
    data object None : SalaryChoice

    data class Fixed(val signature: String) : SalaryChoice
}

/**
 * The salary suggested among these contracts: the highest active monthly income
 */
fun List<Contract>.automaticSalary(): Contract? =
    filter { it.isIncome && it.isActive && it.interval == ContractInterval.MONTHLY }
        .maxByOrNull { it.lastAmount }

/**
 * The salary according to [choice]. A chosen income that is no longer received falls back
 * to the automatic choice.
 */
fun List<Contract>.salary(choice: SalaryChoice): Contract? = when (choice) {
    SalaryChoice.Automatic -> automaticSalary()
    SalaryChoice.None -> null
    is SalaryChoice.Fixed -> find { it.signature == choice.signature && it.isIncome && it.isActive }
        ?: automaticSalary()
}
