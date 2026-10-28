package org.totschnig.myexpenses.next.data.analysis

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo

/** How the transactions of a tag are evaluated, from its [TagMeaning] */
enum class TagGroup {
    /** As a whole, from the first to the last day */
    TRIP,

    /** Tags without meaning, over the last 12 months */
    THEME,

    /** Per calendar year */
    BUSINESS;

    companion object {
        /** Null for markers that are not evaluated, e.g. one-off */
        fun of(meaning: TagMeaning?) = when (meaning) {
            null -> THEME
            TagMeaning.TRIP -> TRIP
            TagMeaning.BUSINESS -> BUSINESS
            else -> null
        }
    }
}

data class MonthAmount(val month: YearMonth, val amount: Long)

/**
 * @param mainCategoryId null for transactions without category
 * @param label of the main category, null without category
 */
data class CategoryAmount(val mainCategoryId: Long?, val label: String?, val amount: Long)

/**
 * What was spent with a tag from [start] to [end]. Amounts are in minor units of the home currency,
 * positive when spent: debits minus credits with the same tag. Transactions also tagged as
 * reimbursed do not count, one-off ones do.
 *
 * @param months each month of the period, oldest first
 * @param byCategory by main category, highest first
 * @param transactionIds of the transactions counted
 */
data class TagEvaluation(
    val tagId: Long,
    val label: String,
    val color: Int?,
    val group: TagGroup,
    val start: LocalDate,
    val end: LocalDate,
    val total: Long,
    val months: List<MonthAmount>,
    val byCategory: List<CategoryAmount>,
    val transactionIds: List<Long>,
) {
    val count: Int get() = transactionIds.size

    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    val perDay: Long get() = total / days

    val monthlyAverage: Long get() = if (months.isEmpty()) total else total / months.size
}

/**
 * The tags of the analysis tab
 *
 * @param trips the latest first
 * @param themes of the last 12 months, highest first
 * @param business of [year], highest first
 * @param businessYears with business transactions, the latest first
 */
data class TagAnalysis(
    val trips: List<TagEvaluation>,
    val themes: List<TagEvaluation>,
    val business: List<TagEvaluation>,
    val businessYears: List<Int>,
    val year: Int,
) {
    val isEmpty: Boolean get() = trips.isEmpty() && themes.isEmpty() && business.isEmpty()
}

object TagAnalyzer {
    /** Themes are evaluated over this many months, the current one included */
    const val THEME_MONTHS = 12L

    /**
     * @param categories of the user by id, see [org.totschnig.myexpenses.next.ledger.Catalog.categoriesById]
     * @param year of the business expenses, null for the latest year with some
     */
    fun analyse(
        ledger: Ledger,
        tags: List<TagInfo>,
        meanings: Map<Long, TagMeaning>,
        categories: Map<Long, CategoryInfo>,
        today: LocalDate,
        year: Int? = null,
    ): TagAnalysis {
        val reimbursed = meanings.filterValues { it == TagMeaning.REIMBURSED }.keys
        // Transfers are no spending, and what was paid back was not really spent
        val counted = ledger.transactions.filter { transaction ->
            transaction.transferAccountId == null && transaction.tagIds.none { it in reimbursed }
        }
        val byTag = counted.flatMap { transaction -> transaction.tagIds.map { it to transaction } }
            .groupBy({ it.first }, { it.second })
        fun evaluations(group: TagGroup, period: (List<LedgerTransaction>) -> ClosedRange<LocalDate>?) =
            tags.filter { TagGroup.of(meanings[it.id]) == group }.mapNotNull { tag ->
                val transactions = byTag[tag.id] ?: return@mapNotNull null
                val range = period(transactions) ?: return@mapNotNull null
                evaluate(tag, group, transactions, range.start, range.endInclusive, categories)
            }

        val themeStart = today.withDayOfMonth(1).minusMonths(THEME_MONTHS - 1)
        val businessTags = tags.filter { meanings[it.id] == TagMeaning.BUSINESS }.mapTo(HashSet()) { it.id }
        val businessYears = counted.filter { transaction -> transaction.tagIds.any { it in businessTags } }
            .map { it.date.year }.distinct().sortedDescending()
        val businessYear = year ?: businessYears.firstOrNull() ?: today.year
        return TagAnalysis(
            trips = evaluations(TagGroup.TRIP) { it.minOf { t -> t.date }..it.maxOf { t -> t.date } }
                .sortedByDescending { it.start },
            themes = evaluations(TagGroup.THEME) { themeStart..today }
                .filter { it.count > 0 }
                .sortedByDescending { it.total },
            business = evaluations(TagGroup.BUSINESS) {
                LocalDate.of(businessYear, 1, 1)..minOf(LocalDate.of(businessYear, 12, 31), today)
            }
                .filter { it.count > 0 }
                .sortedByDescending { it.total },
            businessYears = businessYears,
            year = businessYear
        )
    }

    /** The transactions of [tag] from [start] to [end] */
    fun evaluate(
        tag: TagInfo,
        group: TagGroup,
        transactions: List<LedgerTransaction>,
        start: LocalDate,
        end: LocalDate,
        categories: Map<Long, CategoryInfo>,
    ): TagEvaluation {
        val inPeriod = transactions.filter { it.date in start..end }.sortedBy { it.date }
        fun List<LedgerTransaction>.spent() = -sumOf { it.homeAmount }
        val months = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(end)) }
            .map { month -> MonthAmount(month, inPeriod.filter { YearMonth.from(it.date) == month }.spent()) }
            .toList()
        val byCategory = inPeriod
            .groupBy { transaction -> transaction.categoryId?.let { categories[it]?.mainId ?: it } }
            .map { (mainId, group) -> CategoryAmount(mainId, mainId?.let { categories[it]?.label }, group.spent()) }
            .sortedByDescending { it.amount }
        return TagEvaluation(
            tagId = tag.id,
            label = tag.label,
            color = tag.color,
            group = group,
            start = start,
            end = end,
            total = inPeriod.spent(),
            months = months,
            byCategory = byCategory,
            transactionIds = inPeriod.map { it.id }
        )
    }
}
