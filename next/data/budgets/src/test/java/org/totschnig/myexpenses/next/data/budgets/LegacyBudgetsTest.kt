package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo

class LegacyBudgetsTest {

    private val today = LocalDate.of(2026, 10, 13)
    private val food = CategoryInfo(1, "Lebensmittel", "Lebensmittel")
    private val supermarket = CategoryInfo(2, "Supermarkt", "Lebensmittel > Supermarkt", mainId = 1, parentId = 1)
    private val leisure = CategoryInfo(3, "Freizeit", "Freizeit")
    private val salary = CategoryInfo(4, "Gehalt", "Gehalt", isExpense = false)
    private val categories = listOf(food, supermarket, leisure, salary).associateBy { it.id }

    private fun pots(vararg budgets: LegacyBudget) = LegacyBudgets.pots(budgets.toList(), "EUR", categories, today)

    @Test
    fun categoryAllocationsBecomePotsOfTheirOwn() {
        val pots = pots(
            LegacyBudget(
                "Monat", "EUR", LegacyPeriod.MONTH, null, null, total = 100000,
                categoryAllocations = mapOf(supermarket.id to 40000L, leisure.id to 15000L, 99L to 5000L, food.id to 0L)
            )
        )
        assertEquals(listOf("Supermarkt" to 40000L, "Freizeit" to 15000L), pots.map { it.name to it.amount })
        assertTrue(pots.all { it.kind == PotKind.RUNNING && it.start == today })
        assertEquals(setOf(supermarket.id), pots.first().categoryIds)
    }

    @Test
    fun budgetWithoutCategoryAmountsBecomesOnePot() {
        val all = pots(LegacyBudget("Alles", "EUR", LegacyPeriod.WEEK, null, null, total = 12000)).single()
        // A week's amount per month
        assertEquals(52000L, all.amount)
        // Without filter it holds all main categories for expenses
        assertEquals(setOf(food.id, leisure.id), all.categoryIds)
        val filtered = pots(LegacyBudget("Hund", "EUR", LegacyPeriod.YEAR, null, null, total = 60000, tagIds = setOf(7))).single()
        assertEquals(PotKind.YEARLY, filtered.kind)
        assertEquals(emptySet<Long>(), filtered.categoryIds)
        assertEquals(setOf(7L), filtered.tagIds)
    }

    @Test
    fun budgetWithDatesBecomesAProjectAndOtherCurrenciesAreLeftOut() {
        val start = LocalDate.of(2026, 8, 1)
        val end = LocalDate.of(2026, 8, 14)
        val pots = pots(
            LegacyBudget("Italien", "EUR", LegacyPeriod.NONE, start, end, total = 150000, categoryIds = setOf(leisure.id)),
            LegacyBudget("USA", "USD", LegacyPeriod.MONTH, null, null, total = 50000),
            LegacyBudget("Leer", "EUR", LegacyPeriod.MONTH, null, null, total = 0),
        )
        val trip = pots.single()
        assertEquals(PotKind.PROJECT, trip.kind)
        assertEquals(start, trip.start)
        assertEquals(end, trip.end)
    }
}
