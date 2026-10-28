package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo

/** The period of a budget of the classic screens */
enum class LegacyPeriod { NONE, DAY, WEEK, MONTH, YEAR }

/**
 * A budget of the classic screens, as far as it can become pots
 *
 * @param total allocated for the whole budget in its current period, in minor units of [currency]
 * @param categoryAllocations allocated per category in the current period
 * @param categoryIds / [tagIds] / [payeeIds] of its filter
 */
data class LegacyBudget(
    val title: String,
    val currency: String,
    val grouping: LegacyPeriod,
    val start: LocalDate?,
    val end: LocalDate?,
    val total: Long,
    val categoryAllocations: Map<Long, Long> = emptyMap(),
    val categoryIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val payeeIds: Set<Long> = emptySet(),
)

object LegacyBudgets {

    /**
     * Pots for the [budgets] in [homeCurrency]: one per category with an amount of its own, or one
     * for the whole budget if it has none. Monthly and weekly budgets become running pots, yearly
     * ones yearly pots, budgets with fixed dates projects. Budgets per day are taken per month.
     * Which accounts a budget was for is not taken over, pots count all accounts.
     *
     * @param categories of the user by id, a budget without category filter holds all main categories
     * for expenses
     */
    fun pots(budgets: List<LegacyBudget>, homeCurrency: String, categories: Map<Long, CategoryInfo>, today: LocalDate): List<Pot> =
        budgets.filter { it.currency == homeCurrency }.flatMap { budget ->
            val kind = when (budget.grouping) {
                LegacyPeriod.YEAR -> PotKind.YEARLY
                LegacyPeriod.NONE -> PotKind.PROJECT
                else -> PotKind.RUNNING
            }
            fun amount(value: Long) = when (budget.grouping) {
                LegacyPeriod.WEEK -> value * 52 / 12
                LegacyPeriod.DAY -> value * 365 / 12
                else -> value
            }
            fun pot(name: String, amount: Long, categoryIds: Set<Long>) = Pot(
                name = name,
                kind = kind,
                amount = amount(amount),
                categoryIds = categoryIds,
                tagIds = budget.tagIds,
                payeeIds = budget.payeeIds,
                start = if (kind == PotKind.PROJECT) budget.start else today,
                end = if (kind == PotKind.PROJECT) budget.end else null
            )
            val allocations = budget.categoryAllocations.filter { (id, amount) -> amount > 0 && id in categories }
            if (allocations.isNotEmpty()) {
                allocations.map { (id, amount) -> pot(categories.getValue(id).label, amount, setOf(id)) }
            } else if (budget.total > 0) {
                val holdsAll = budget.categoryIds.isEmpty() && budget.tagIds.isEmpty() && budget.payeeIds.isEmpty()
                val categoryIds = if (holdsAll) {
                    categories.values.filter { it.isExpense && it.parentId == null }.mapTo(LinkedHashSet()) { it.id }
                } else budget.categoryIds
                listOf(pot(budget.title, budget.total, categoryIds))
            } else emptyList()
        }
}
