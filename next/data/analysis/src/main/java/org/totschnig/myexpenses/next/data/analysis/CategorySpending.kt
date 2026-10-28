package org.totschnig.myexpenses.next.data.analysis

import java.time.LocalDate
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger

enum class PeriodKind {
    MONTH, YEAR,
    /** The twelve months up to today, e.g. from 14 October 2025 to 13 October 2026 */
    TWELVE_MONTHS
}

/**
 * A calendar month or year, or twelve months, from [start] to [end], both included. The current one
 * ends today.
 */
data class SpendingPeriod(val kind: PeriodKind, val start: LocalDate, val end: LocalDate) {

    /** The whole period before this one */
    fun previous(): SpendingPeriod = when (kind) {
        PeriodKind.MONTH -> start.minusMonths(1).let { SpendingPeriod(kind, it, it.plusMonths(1).minusDays(1)) }
        PeriodKind.YEAR -> start.minusYears(1).let { SpendingPeriod(kind, it, it.plusYears(1).minusDays(1)) }
        PeriodKind.TWELVE_MONTHS -> SpendingPeriod(kind, start.minusYears(1), start.minusDays(1))
    }

    /** Whether the whole period is in a ledger read [today], see [Ledger.HISTORY_MONTHS] */
    fun isInLedger(today: LocalDate) = !start.isBefore(today.minusMonths(Ledger.HISTORY_MONTHS))

    companion object {
        /**
         * @param offset 0 for the current month or year, 1 for the one before and so on
         */
        fun of(kind: PeriodKind, offset: Int, today: LocalDate): SpendingPeriod {
            val current = when (kind) {
                PeriodKind.MONTH -> SpendingPeriod(kind, today.withDayOfMonth(1), today)
                PeriodKind.YEAR -> SpendingPeriod(kind, today.withDayOfYear(1), today)
                PeriodKind.TWELVE_MONTHS -> SpendingPeriod(kind, today.minusYears(1).plusDays(1), today)
            }
            return (0 until offset).fold(current) { period, _ -> period.previous() }
        }
    }
}

/**
 * A subcategory of a [CategorySpending], or the main category itself for what was booked on it
 * directly
 *
 * @param label relative to the main category, e.g. "Restaurant" or "Restaurant > Lunch"
 */
data class SubcategorySpending(val categoryId: Long, val label: String, val amount: Long)

/**
 * What was spent on a main category in a period, in minor units of the home currency: debits
 * minus credits, e.g. a returned purchase
 *
 * @param mainCategoryId null for transactions without category
 * @param label of the main category, null without category
 * @param previous spent in the period before, null if it is not fully in the ledger
 * @param subcategories highest first, empty without category
 * @param transactionIds of the transactions with a part in this category
 */
data class CategorySpending(
    val mainCategoryId: Long?,
    val label: String?,
    val amount: Long,
    val previous: Long?,
    val subcategories: List<SubcategorySpending>,
    val transactionIds: List<Long>,
) {
    /** Tells the categories apart, also the one for transactions without category */
    val key: Long get() = mainCategoryId ?: NO_CATEGORY

    companion object {
        /** [key] of the transactions without category, no id of a category */
        const val NO_CATEGORY = -1L
    }
}

/**
 * Spending by category in [period]
 *
 * @param categories highest first, only those with spending
 * @param previousTotal spent in the period before, null if it is not fully in the ledger
 * @param hasEarlier whether the period before is fully in the ledger, so that it can be shown
 * @param offset of [period], see [SpendingPeriod.of]
 */
data class CategoryAnalysis(
    val period: SpendingPeriod,
    val offset: Int,
    val total: Long,
    val previousTotal: Long?,
    val categories: List<CategorySpending>,
    val hasEarlier: Boolean,
) {
    val isCurrent: Boolean get() = offset == 0
}

object CategoryAnalyzer {

    /**
     * What was spent on each main category in a month or year. Movements between own accounts and
     * transactions in [excluded] (e.g. reimbursed ones) do not count, nor do categories meant for
     * income only. Split transactions count with their parts. Credits without category count as
     * income, not as returns.
     *
     * @param excluded ids of transactions that are no spending, see
     * [org.totschnig.myexpenses.next.tags.TagFacts.excludedFromSpending]
     * @param categories of the user by id, see [org.totschnig.myexpenses.next.ledger.Catalog.categoriesById]
     */
    fun analyse(
        ledger: Ledger,
        excluded: Set<Long>,
        categories: Map<Long, CategoryInfo>,
        kind: PeriodKind,
        offset: Int,
        today: LocalDate,
    ): CategoryAnalysis {
        val period = SpendingPeriod.of(kind, offset, today)
        val previousPeriod = period.previous()
        val hasPrevious = previousPeriod.isInLedger(today)
        fun spending(period: SpendingPeriod) =
            countedParts(ledger, excluded, categories, period).filter { it.isSpending }
        val current = spending(period)
        val previous = if (hasPrevious) spending(previousPeriod) else null
        val result = current.groupBy { it.mainId }.mapNotNull { (mainId, parts) ->
            val amount = parts.spent()
            if (amount <= 0) return@mapNotNull null
            val main = mainId?.let { categories[it] }
            CategorySpending(
                mainCategoryId = mainId,
                label = main?.label,
                amount = amount,
                previous = previous?.filter { it.mainId == mainId }?.spent()?.coerceAtLeast(0),
                subcategories = if (main == null) emptyList() else subcategories(main, parts, categories),
                transactionIds = parts.map { it.transaction.id }.distinct()
            )
        }.sortedByDescending { it.amount }
        return CategoryAnalysis(
            period = period,
            offset = offset,
            total = result.sumOf { it.amount },
            previousTotal = previous?.let { parts ->
                parts.groupBy { it.mainId }.values.sumOf { it.spent().coerceAtLeast(0) }
            },
            categories = result,
            hasEarlier = hasPrevious
        )
    }

    private fun List<CountedPart>.spent() = -sumOf { it.part.homeAmount }

    private fun subcategories(
        main: CategoryInfo,
        parts: List<CountedPart>,
        categories: Map<Long, CategoryInfo>,
    ): List<SubcategorySpending> =
        parts.groupBy { it.category!!.id }.mapNotNull { (categoryId, group) ->
            val amount = group.spent()
            if (amount <= 0) return@mapNotNull null
            val category = categories.getValue(categoryId)
            val label = if (categoryId == main.id) main.label else category.path.removePrefix("${main.path} > ")
            SubcategorySpending(categoryId, label, amount)
        }.sortedByDescending { it.amount }
}
