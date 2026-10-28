package org.totschnig.myexpenses.next.data.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import java.time.LocalDate
import java.time.YearMonth

class SpendingTipsTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 29)
    private var nextId = 1L

    private fun debit(
        amount: Long,
        date: LocalDate,
        payee: String? = null,
        category: String? = null,
        comment: String? = null,
        targetAccountId: Long? = null,
    ) = ContractTransaction(
        id = nextId++, date = date, amount = -amount, accountId = 1, payeeName = payee, comment = comment,
        categoryPath = category, targetAccountId = targetAccountId
    )

    /** One debit every [everyDays] days within the last [days] days */
    private fun series(amount: Long, payee: String, days: Int = 360, everyDays: Int = 3, category: String? = null) =
        (1..days step everyDays).map { debit(amount, today.minusDays(it.toLong()), payee, category) }

    @Test
    fun spendingLeavesOutContractsMovementsCreditsAndFuture() {
        val contract = debit(999, today, "Netflix")
        val kept = debit(350, today, "Bäckerei")
        val spending = spending(
            listOf(
                contract, kept,
                debit(5000, today, "→ Depot", targetAccountId = 2),
                ContractTransaction(nextId++, today, 250000, 1, payeeName = "Arbeitgeber"),
                debit(350, today.plusDays(1), "Bäckerei")
            ),
            contractPayments = setOf(contract.id),
            today = today
        )
        assertEquals(listOf(kept), spending)
    }

    @Test
    fun smallPurchasesWithTopPayees() {
        val tip = smallPurchases(
            series(350, "Bäckerei Müller") + series(480, "Coffee Fellows", everyDays = 7) +
                    series(4500, "REWE", everyDays = 7) +
                    listOf(debit(300, today.minusMonths(13), "Bäckerei Müller")),
            today
        )!!
        assertEquals(listOf("Bäckerei Müller", "Coffee Fellows"), tip.payees.map { it.label })
        assertEquals(120 + 52, tip.count)
        assertEquals(120 * 350L + 52 * 480L, tip.yearlyAmount)
        assertEquals("SMALL_PURCHASES:Bäckerei Müller", tip.payees.first().key)
        assertEquals(tip.payees.first().transactions, tip.transactionGroups.getValue("SMALL_PURCHASES:Bäckerei Müller"))
    }

    @Test
    fun fewSmallPurchasesAreNoTip() {
        assertNull(smallPurchases(series(350, "Bäckerei", everyDays = 7), today))
    }

    @Test
    fun smallPurchasesComeBackWhenMuchMore() {
        val tip = smallPurchases(series(350, "Bäckerei"), today)!!
        val decisions = TipDecisions(dismissed = mapOf(tip.id to tip.facts))
        assertTrue(decisions.isDismissed(smallPurchases(series(360, "Bäckerei"), today)!!))
        assertFalse(decisions.isDismissed(smallPurchases(series(700, "Bäckerei") + series(700, "Kiosk"), today)!!))
    }

    @Test
    fun recognizesEatingOut() {
        assertTrue(isEatingOut(debit(2500, today, "Lieferando.de")))
        assertTrue(isEatingOut(debit(2500, today, "Pizzeria Da Mario")))
        assertTrue(isEatingOut(debit(2500, today, "Il Gusto", category = "Essen > Restaurant")))
        assertFalse(isEatingOut(debit(2500, today, "REWE", category = "Essen > Supermarkt")))
    }

    @Test
    fun eatingOutMoreThanUsual() {
        val usual = (40..390 step 10).map { debit(3000, today.minusDays(it.toLong()), "Lieferando") }
        val recent = (1..28 step 4).map { debit(3000, today.minusDays(it.toLong()), "Lieferando") }
        val tip = eatingOut(usual + recent, today)!!
        assertEquals(7 * 3000L, tip.recent)
        // Every 10 days during the year before
        assertEquals(9000.0, tip.usual.toDouble(), 300.0)
        assertEquals(recent.toSet(), tip.transactions.toSet())
        assertEquals(setOf("2026-09"), tip.facts)
    }

    @Test
    fun eatingOutAsUsualIsNoTip() {
        assertNull(eatingOut((1..390 step 10).map { debit(3000, today.minusDays(it.toLong()), "Lieferando") }, today))
    }

    @Test
    fun eatingOutWithoutHistoryIsNoTip() {
        assertNull(eatingOut((1..28 step 2).map { debit(3000, today.minusDays(it.toLong()), "Lieferando") }, today))
    }

    @Test
    fun averagePurchasePerSupermarket() {
        val tip = supermarkets(
            series(4300, "REWE Markt GmbH", everyDays = 7) + series(2600, "ALDI SUED", everyDays = 7) +
                    series(1500, "Penny", everyDays = 100) + series(350, "Bäckerei"),
            today
        )!!
        assertEquals(listOf("REWE", "ALDI"), tip.chains.map { it.label })
        assertEquals(4300L, tip.chains.first().average)
        assertEquals(setOf("REWE", "ALDI"), tip.facts)
    }

    @Test
    fun oneSupermarketIsNoTip() {
        assertNull(supermarkets(series(4300, "REWE", everyDays = 7), today))
    }

    @Test
    fun outlierInLastCompleteMonth() {
        val months = (1L..12L).map { YearMonth.from(today).minusMonths(it) }
        val leisure = months.map { debit(if (it == months.first()) 42000 else 18000, it.atDay(10), "Kino", "Freizeit > Kino") }
        val food = months.map { debit(40000, it.atDay(12), "REWE", "Essen > Supermarkt") }
        val tip = outliers(leisure + food + debit(9000, today, "Kino", "Freizeit"), today)!!
        assertEquals(YearMonth.of(2026, 8), tip.month)
        val category = tip.categories.single()
        assertEquals("Freizeit", category.label)
        assertEquals(42000L, category.amount)
        assertEquals(18000L, tip.averages.getValue(category.key))
        assertEquals(24000L, tip.yearlyAmount)
        assertEquals(setOf("2026-08:Freizeit"), tip.facts)
    }

    @Test
    fun excludedDebitsAreNoSpending() {
        val oneOff = debit(80000, today, "Möbelhaus")
        val kept = debit(350, today, "Bäckerei")
        assertEquals(listOf(kept), spending(listOf(oneOff, kept), emptySet(), today, excluded = setOf(oneOff.id)))
    }

    @Test
    fun oneOffExpenseIsNoOutlier() {
        val months = (1L..12L).map { YearMonth.from(today).minusMonths(it) }
        val leisure = months.map { debit(18000, it.atDay(10), "Kino", "Freizeit > Kino") }
        val concert = debit(24000, months.first().atDay(20), "Konzertkasse", "Freizeit > Konzert")
        val transactions = leisure + concert
        assertEquals("Freizeit", outliers(spending(transactions, emptySet(), today), today)!!.categories.single().label)
        assertNull(outliers(spending(transactions, emptySet(), today, excluded = setOf(concert.id)), today))
    }

    @Test
    fun shortHistoryGivesNoOutliers() {
        assertNull(outliers(listOf(debit(50000, today.minusMonths(1), "Kino", "Freizeit")), today))
    }
}
