package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import org.totschnig.myexpenses.next.data.balance.BalancePeriod
import org.totschnig.myexpenses.next.data.contracts.Contract

/**
 * The periods of the budget from [historyStart] until today, oldest first, the current one last.
 * Like the monthly balance, a period runs from salary to salary, or is the calendar month without
 * salary, see [BalancePeriod.of]. Before the first salary in the history, there are no periods.
 */
fun budgetPeriods(salary: Contract?, today: LocalDate, historyStart: LocalDate): List<BalancePeriod> {
    val current = BalancePeriod.of(salary, today)
    val earlier = if (current.isSalaryCycle) {
        val paydays = salary!!.transactions.map { it.date }
            .filter { !it.isBefore(historyStart) && it.isBefore(current.start) }
            .distinct()
            .sorted()
        (paydays + current.start).zipWithNext { start, end -> BalancePeriod(start, end, true) }
    } else {
        generateSequence(current.start.minusMonths(1)) { it.minusMonths(1) }
            .takeWhile { !it.isBefore(historyStart) }
            .map { BalancePeriod(it, it.plusMonths(1), false) }
            .toList()
            .asReversed()
    }
    return earlier + current
}
