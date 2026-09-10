package org.totschnig.myexpenses.next.balance

import org.totschnig.myexpenses.next.contracts.Contract
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Period the balance is computed for.
 *
 * @param start first day
 * @param end first day after the period, the day the next salary is expected
 * @param isSalaryCycle true, if the period runs from salary to salary, false for the calendar month
 */
data class BalancePeriod(
    val start: LocalDate,
    val end: LocalDate,
    val isSalaryCycle: Boolean,
) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && date.isBefore(end)

    /** Days from [today] until the end, at least 1 */
    fun daysLeft(today: LocalDate) = ChronoUnit.DAYS.between(today, end).coerceAtLeast(1)

    companion object {
        /**
         * From the last payment of [salary] to the next one. Without salary, the calendar month.
         */
        fun of(salary: Contract?, today: LocalDate): BalancePeriod {
            // Salaries booked in advance do not start a new period before they arrive
            val lastSalary = salary?.transactions?.map { it.date }?.filter { !it.isAfter(today) }?.maxOrNull()
            if (salary != null && lastSalary != null) {
                val next = lastSalary.plus(salary.interval.step)
                // A late salary is expected any day now
                return BalancePeriod(lastSalary, if (next.isAfter(today)) next else today.plusDays(1), true)
            }
            val firstOfMonth = today.withDayOfMonth(1)
            return BalancePeriod(firstOfMonth, firstOfMonth.plusMonths(1), false)
        }
    }
}

/**
 * Transaction of a daily account as input for [MonthlyBalance.compute]. Amount in minor units of
 * the home currency.
 *
 * @param transferAccountId the other account, if this is a transfer
 */
data class BalanceTransaction(
    val id: Long,
    val date: LocalDate,
    val amount: Long,
    val accountId: Long,
    val transferAccountId: Long? = null,
)

/**
 * What came in and went out of the daily accounts in a [period]. Amounts in minor units of the
 * home currency; money going out is negative.
 *
 * @param contractsUpcoming contract debits expected until the end of the period, not booked yet
 * @param savings transfers between daily accounts and other accounts, negative if money was saved
 */
data class MonthlyBalance(
    val period: BalancePeriod,
    val income: Long,
    val contractsBooked: Long,
    val contractsUpcoming: Long,
    val savings: Long,
    val other: Long,
    val incomeTransactionIds: List<Long>,
    val savingsTransactionIds: List<Long>,
    val otherTransactionIds: List<Long>,
) {
    val contracts: Long get() = contractsBooked + contractsUpcoming

    /** Everything going out, as positive amount */
    val expenses: Long get() = -(contracts + savings + other)

    val available: Long get() = income + contracts + savings + other

    companion object {
        /**
         * @param transactions of all accounts, only those of [dailyAccountIds] within [period] count
         * @param contracts detected contracts, without those the user dismissed
         */
        fun compute(
            period: BalancePeriod,
            transactions: List<BalanceTransaction>,
            dailyAccountIds: Set<Long>,
            contracts: List<Contract>,
        ): MonthlyBalance {
            val expenseContracts = contracts.filter { !it.isIncome }
            val contractTransactionIds = expenseContracts.flatMapTo(HashSet()) { contract -> contract.transactions.map { it.id } }
            val income = mutableListOf<BalanceTransaction>()
            val contractDebits = mutableListOf<BalanceTransaction>()
            val savings = mutableListOf<BalanceTransaction>()
            val other = mutableListOf<BalanceTransaction>()
            transactions
                .filter { it.accountId in dailyAccountIds && it.date in period }
                .forEach {
                    when {
                        // Moving money between daily accounts, e.g. paying the credit card, changes nothing
                        it.transferAccountId != null ->
                            if (it.transferAccountId !in dailyAccountIds) savings += it

                        it.amount > 0 -> income += it
                        it.id in contractTransactionIds -> contractDebits += it
                        else -> other += it
                    }
                }
            return MonthlyBalance(
                period = period,
                income = income.sumOf { it.amount },
                contractsBooked = contractDebits.sumOf { it.amount },
                contractsUpcoming = -expenseContracts
                    .filter { it.isActive && it.lastTransaction.accountId in dailyAccountIds }
                    .sumOf { it.lastAmount * it.upcomingDebits(period) },
                savings = savings.sumOf { it.amount },
                other = other.sumOf { it.amount },
                incomeTransactionIds = income.map { it.id },
                savingsTransactionIds = savings.map { it.id },
                otherTransactionIds = other.map { it.id }
            )
        }

        /**
         * Number of debits still expected in [period]. Includes debits of the period that are
         * a few days late, since they are still going to come.
         */
        private fun Contract.upcomingDebits(period: BalancePeriod): Int {
            var count = 0
            var date = nextExpectedDate
            while (date.isBefore(period.end)) {
                if (date in period) count++
                date = date.plus(interval.step)
            }
            return count
        }
    }
}
