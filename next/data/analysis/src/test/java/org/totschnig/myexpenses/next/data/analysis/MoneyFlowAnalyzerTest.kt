package org.totschnig.myexpenses.next.data.analysis

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerPart
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts

class MoneyFlowAnalyzerTest {

    private val today = LocalDate.of(2026, 10, 13)
    private val food = CategoryInfo(10, "Essen", "Essen", isIncome = false)
    private val restaurant = CategoryInfo(11, "Restaurant", "Essen > Restaurant", isIncome = false, mainId = 10)
    private val clothes = CategoryInfo(20, "Kleidung", "Kleidung", isIncome = false)
    private val salary = CategoryInfo(30, "Gehalt", "Gehalt", isExpense = false)
    private val categories = listOf(food, restaurant, clothes, salary).associateBy { it.id }
    private var nextId = 1L

    private fun transaction(
        amount: Long,
        category: CategoryInfo? = null,
        payee: String? = null,
        date: LocalDate = today,
        parts: List<LedgerPart> = emptyList(),
        transferAccountId: Long? = null,
    ) = LedgerTransaction(
        nextId++, date, amount = amount, homeAmount = amount, accountId = 1, payeeName = payee,
        categoryId = category?.id, transferAccountId = transferAccountId,
        targetAccountId = transferAccountId, parts = parts
    )

    private fun analyse(
        vararg transactions: LedgerTransaction,
        kind: PeriodKind = PeriodKind.TWELVE_MONTHS,
        offset: Int = 0,
        excluded: Set<Long> = emptySet(),
        categories: Map<Long, CategoryInfo> = this.categories,
    ) = MoneyFlowAnalyzer.analyse(
        Ledger(transactions.toList(), OwnAccounts(emptySet(), emptyMap())),
        excluded, categories, kind, offset, today
    )

    @Test
    fun flowsFromIncomesToCategoriesAndActors() {
        val flow = analyse(
            transaction(300000, salary, "Arbeitgeber GmbH"),
            transaction(-1200, restaurant, "MCDONALDS 1234 DARMSTADT"),
            transaction(-800, restaurant, "McDonalds Frankfurt"),
            transaction(-5000, food, "REWE Markt GmbH Fil. 4711"),
            transaction(-3000, clothes),
        )
        assertEquals(300000, flow.earned)
        assertEquals(10000, flow.spent)
        assertEquals(290000, flow.left)
        assertEquals(listOf(FlowGroup(30, "Gehalt", 300000, 1, listOf(1))), flow.incomes)
        assertEquals(listOf(10L, 20L), flow.expenses.map { it.key })
        val essen = flow.expenses.first()
        assertEquals(7000, essen.amount)
        assertEquals(3, essen.count)
        assertEquals(
            listOf(FlowActor("REWE", 5000, 1, listOf(4)), FlowActor("McDonald's", 2000, 2, listOf(2, 3))),
            essen.actors
        )
        assertEquals(listOf(FlowActor(null, 3000, 1, listOf(5))), flow.expenses[1].actors)
    }

    @Test
    fun countsLikeTheSpendingByCategory() {
        val flow = analyse(
            transaction(-5000, food),
            transaction(-2000, transferAccountId = 2),
            transaction(-4000, food),
            transaction(-3000, parts = listOf(LedgerPart(-1000, categoryId = 10), LedgerPart(-2000, transferAccountId = 2))),
            transaction(-9000, food, date = today.minusYears(1)),
            excluded = setOf(3),
        )
        assertEquals(listOf(6000L), flow.expenses.map { it.amount })
        assertTrue(flow.incomes.isEmpty())
    }

    @Test
    fun theNetAmountTellsTheSide() {
        val flow = analyse(
            transaction(-2000, clothes),
            transaction(5000, clothes),
            transaction(-1000, salary),
            transaction(700),
            transaction(-400),
        )
        assertEquals(listOf(20L to 3000L, CategorySpending.NO_CATEGORY to 700L), flow.incomes.map { it.key to it.amount })
        assertEquals(listOf(30L to 1000L, CategorySpending.NO_CATEGORY to 400L), flow.expenses.map { it.key to it.amount })
        assertEquals(3700 - 1400L, flow.left)
    }

    @Test
    fun sumsUpSmallCategoriesIntoARest() {
        val many = (1..10L).map { CategoryInfo(100 + it, "K$it", "K$it", isIncome = false) }
        val flow = analyse(
            *many.map { transaction(-100 * it.id, it, payee = "P${it.id}") }.toTypedArray(),
            categories = many.associateBy { it.id },
        )
        assertEquals(MoneyFlow.MAX_EXPENSES, flow.expenses.size)
        val rest = flow.expenses.last()
        assertTrue(rest.isRest)
        assertEquals(10100 + 10200 + 10300, rest.amount)
        assertEquals(3, rest.actors.size)
        assertEquals(many.sumOf { 100 * it.id }, flow.spent)
    }

    @Test
    fun sumsUpSmallActorsIntoARest() {
        val flow = analyse(*(1..8L).map { transaction(-100 * it, food, payee = "P$it") }.toTypedArray())
        val actors = flow.expenses.single().actors
        assertEquals(MoneyFlow.MAX_ACTORS, actors.size)
        assertEquals(listOf("P8", "P7", "P6", "P5", "P4"), actors.dropLast(1).map { it.name })
        assertEquals(FlowActor(null, 600, 3, listOf(3, 2, 1), others = 3), actors.last())
    }

    @Test
    fun actorsWithMoreReturnsArePartOfTheRest() {
        val flow = analyse(
            transaction(-5000, clothes, "Zalando"),
            transaction(-1000, clothes, "H&M"),
            transaction(-2000, clothes, "Otto"),
            transaction(3000, clothes, "Otto"),
        )
        val actors = flow.expenses.single().actors
        assertEquals(5000, flow.spent)
        assertEquals(listOf("Zalando"), actors.filterNot { it.isRest }.map { it.name })
        assertEquals(0, actors.sumOf { it.amount } - 5000)
    }

    @Test
    fun ordersExpensesOverTheWholeLedgerForTheirColors() {
        val flow = analyse(
            transaction(-1000, food),
            transaction(-900, clothes),
            transaction(-5000, clothes, date = today.minusMonths(20)),
            transaction(-100, date = today.minusMonths(30)),
        )
        assertEquals(listOf(10L, 20L), flow.expenses.map { it.key })
        assertEquals(listOf(20L, 10L), flow.expenseOrder)
    }

    @Test
    fun twelveMonthsEndToday() {
        val period = SpendingPeriod.of(PeriodKind.TWELVE_MONTHS, 0, today)
        assertEquals(LocalDate.of(2025, 10, 14), period.start)
        assertEquals(today, period.end)
        val previous = SpendingPeriod.of(PeriodKind.TWELVE_MONTHS, 1, today)
        assertEquals(LocalDate.of(2024, 10, 14), previous.start)
        assertEquals(LocalDate.of(2025, 10, 13), previous.end)
        assertTrue(analyse(kind = PeriodKind.TWELVE_MONTHS, offset = 0).hasEarlier)
        assertFalse(analyse(kind = PeriodKind.TWELVE_MONTHS, offset = 1).hasEarlier)
    }
}
