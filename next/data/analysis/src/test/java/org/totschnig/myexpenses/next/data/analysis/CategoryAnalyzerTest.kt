package org.totschnig.myexpenses.next.data.analysis

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerPart
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts

class CategoryAnalyzerTest {

    private val today = LocalDate.of(2026, 10, 12)
    private val food = CategoryInfo(10, "Essen", "Essen", isIncome = false)
    private val restaurant = CategoryInfo(11, "Restaurant", "Essen > Restaurant", isIncome = false, mainId = 10)
    private val lunch = CategoryInfo(12, "Mittag", "Essen > Restaurant > Mittag", isIncome = false, mainId = 10)
    private val home = CategoryInfo(20, "Wohnen", "Wohnen", isIncome = false)
    private val salary = CategoryInfo(30, "Gehalt", "Gehalt", isExpense = false)
    private val categories = listOf(food, restaurant, lunch, home, salary).associateBy { it.id }
    private var nextId = 1L

    private fun transaction(
        date: LocalDate,
        amount: Long,
        category: CategoryInfo? = null,
        parts: List<LedgerPart> = emptyList(),
        transferAccountId: Long? = null,
    ) = LedgerTransaction(
        nextId++, date, amount = amount, homeAmount = amount, accountId = 1,
        categoryId = category?.id, transferAccountId = transferAccountId,
        targetAccountId = transferAccountId, parts = parts
    )

    private fun analyse(
        vararg transactions: LedgerTransaction,
        kind: PeriodKind = PeriodKind.MONTH,
        offset: Int = 0,
        excluded: Set<Long> = emptySet(),
    ) = CategoryAnalyzer.analyse(
        Ledger(transactions.toList(), OwnAccounts(emptySet(), emptyMap())),
        excluded, categories, kind, offset, today
    )

    @Test
    fun sumsUpByMainCategoryWithSubcategories() {
        val analysis = analyse(
            transaction(today, -3000, restaurant),
            transaction(today.minusDays(1), -1000, lunch),
            transaction(today.minusDays(2), -2000, food),
            transaction(today.minusDays(3), -50000, home),
        )
        assertEquals(56000, analysis.total)
        assertEquals(listOf(20L, 10L), analysis.categories.map { it.mainCategoryId })
        val essen = analysis.categories[1]
        assertEquals(6000, essen.amount)
        assertEquals(
            listOf(
                SubcategorySpending(11, "Restaurant", 3000),
                SubcategorySpending(10, "Essen", 2000),
                SubcategorySpending(12, "Restaurant > Mittag", 1000),
            ),
            essen.subcategories
        )
    }

    @Test
    fun creditsInACategoryReduceItsSpending() {
        val analysis = analyse(
            transaction(today, -5000, food),
            transaction(today, 1500, food),
        )
        assertEquals(3500, analysis.categories.single().amount)
    }

    @Test
    fun leavesOutIncomeTransfersAndExcluded() {
        val refunded = transaction(today, -9000, food)
        val analysis = analyse(
            transaction(today, 300000, salary),
            transaction(today, -100000, transferAccountId = 2),
            refunded,
            transaction(today, -1000, food),
            excluded = setOf(refunded.id)
        )
        assertEquals(1000, analysis.total)
        assertEquals(listOf(10L), analysis.categories.map { it.mainCategoryId })
    }

    @Test
    fun withoutCategoryOnlyDebitsCount() {
        val analysis = analyse(
            transaction(today, -2000),
            transaction(today, 250000),
        )
        val none = analysis.categories.single()
        assertNull(none.mainCategoryId)
        assertEquals(CategorySpending.NO_CATEGORY, none.key)
        assertEquals(2000, none.amount)
        assertTrue(none.subcategories.isEmpty())
    }

    @Test
    fun splitTransactionsCountWithTheirParts() {
        val split = transaction(
            today, -10000, parts = listOf(
                LedgerPart(-6000, categoryId = food.id),
                LedgerPart(-3000, categoryId = home.id),
                LedgerPart(-1000, transferAccountId = 2),
            )
        )
        val analysis = analyse(split)
        assertEquals(9000, analysis.total)
        assertEquals(listOf(6000L, 3000L), analysis.categories.map { it.amount })
        assertEquals(listOf(split.id), analysis.categories.first().transactionIds)
    }

    @Test
    fun comparesWithTheWholePreviousMonth() {
        val analysis = analyse(
            transaction(today, -1000, food),
            transaction(LocalDate.of(2026, 9, 30), -4000, food),
            transaction(LocalDate.of(2026, 9, 1), -500, home),
            transaction(LocalDate.of(2026, 8, 31), -9999, food),
        )
        assertEquals(LocalDate.of(2026, 10, 1), analysis.period.start)
        assertEquals(today, analysis.period.end)
        assertEquals(4000L, analysis.categories.single().previous)
        assertEquals(4500L, analysis.previousTotal)
        assertTrue(analysis.isCurrent)
    }

    @Test
    fun earlierMonthsAndYears() {
        val analysis = analyse(transaction(LocalDate.of(2026, 8, 15), -1000, food), offset = 2)
        assertEquals(LocalDate.of(2026, 8, 1), analysis.period.start)
        assertEquals(LocalDate.of(2026, 8, 31), analysis.period.end)
        assertEquals(1000, analysis.total)
        assertFalse(analysis.isCurrent)

        val year = analyse(transaction(LocalDate.of(2025, 3, 1), -1000, food), kind = PeriodKind.YEAR, offset = 1)
        assertEquals(LocalDate.of(2025, 1, 1), year.period.start)
        assertEquals(LocalDate.of(2025, 12, 31), year.period.end)
        assertEquals(1000, year.total)
    }

    @Test
    fun noComparisonBeforeTheLedger() {
        // The ledger starts 26 months back, in August 2024
        val first = analyse(kind = PeriodKind.YEAR, offset = 1)
        assertNull(first.previousTotal)
        assertFalse(first.hasEarlier)
        assertTrue(analyse(kind = PeriodKind.YEAR).hasEarlier)
        assertTrue(analyse(offset = 24).hasEarlier)
        assertFalse(analyse(offset = 25).hasEarlier)
    }
}
