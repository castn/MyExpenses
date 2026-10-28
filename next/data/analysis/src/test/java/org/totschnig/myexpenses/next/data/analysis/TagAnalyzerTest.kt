package org.totschnig.myexpenses.next.data.analysis

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo

class TagAnalyzerTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val italy = TagInfo(1, "Urlaub Italien")
    private val dog = TagInfo(2, "Hund")
    private val work = TagInfo(3, "Beruflich")
    private val refunded = TagInfo(4, "Erstattet")
    private val meanings = mapOf(
        italy.id to TagMeaning.TRIP, work.id to TagMeaning.BUSINESS, refunded.id to TagMeaning.REIMBURSED
    )
    private val food = CategoryInfo(10, "Essen", "Essen")
    private val restaurant = CategoryInfo(11, "Restaurant", "Essen > Restaurant", mainId = 10)
    private val travel = CategoryInfo(12, "Reisen", "Reisen")
    private val categories = listOf(food, restaurant, travel).associateBy { it.id }
    private var nextId = 1L

    private fun transaction(date: LocalDate, amount: Long, vararg tags: TagInfo, category: CategoryInfo? = null) =
        LedgerTransaction(
            nextId++, date, amount = amount, homeAmount = amount, accountId = 1,
            categoryId = category?.id, tagIds = tags.mapTo(HashSet()) { it.id }
        )

    private fun analyse(vararg transactions: LedgerTransaction, year: Int? = null) = TagAnalyzer.analyse(
        Ledger(transactions.toList(), OwnAccounts(emptySet(), emptyMap())),
        listOf(italy, dog, work, refunded), meanings, categories, today, year
    )

    @Test
    fun evaluatesTripAsWholeWithoutRefundsAndWithCredits() {
        val analysis = analyse(
            transaction(LocalDate.of(2026, 8, 1), -20000, italy, category = travel),
            transaction(LocalDate.of(2026, 8, 3), -3000, italy, category = restaurant),
            transaction(LocalDate.of(2026, 8, 4), -2000, italy, category = food),
            // Paid back, not really spent
            transaction(LocalDate.of(2026, 8, 5), -5000, italy, refunded),
            // Deposit returned
            transaction(LocalDate.of(2026, 8, 10), 1000, italy, category = travel),
        )
        val trip = analysis.trips.single()
        assertEquals(LocalDate.of(2026, 8, 1), trip.start)
        assertEquals(LocalDate.of(2026, 8, 10), trip.end)
        assertEquals(24000, trip.total)
        assertEquals(4, trip.count)
        assertEquals(10, trip.days)
        assertEquals(2400, trip.perDay)
        // Restaurant counts for its main category
        assertEquals(
            listOf(CategoryAmount(12, "Reisen", 19000), CategoryAmount(10, "Essen", 5000)),
            trip.byCategory
        )
    }

    @Test
    fun evaluatesThemesOverTheLastTwelveMonths() {
        val analysis = analyse(
            transaction(LocalDate.of(2025, 10, 31), -9999, dog),
            transaction(LocalDate.of(2025, 11, 1), -1200, dog),
            transaction(LocalDate.of(2026, 10, 2), -2400, dog),
        )
        val theme = analysis.themes.single()
        assertEquals(LocalDate.of(2025, 11, 1), theme.start)
        assertEquals(3600, theme.total)
        assertEquals(12, theme.months.size)
        assertEquals(MonthAmount(YearMonth.of(2025, 11), 1200), theme.months.first())
        assertEquals(MonthAmount(YearMonth.of(2026, 10), 2400), theme.months.last())
        assertEquals(300, theme.monthlyAverage)
    }

    @Test
    fun evaluatesBusinessPerYear() {
        val transactions = arrayOf(
            transaction(LocalDate.of(2025, 3, 1), -10000, work),
            transaction(LocalDate.of(2026, 2, 1), -4000, work),
        )
        assertEquals(listOf(2026, 2025), analyse(*transactions).businessYears)
        assertEquals(4000, analyse(*transactions).business.single().total)
        assertEquals(10000, analyse(*transactions, year = 2025).business.single().total)
    }

    @Test
    fun leavesOutTagsWithoutTransactionsAndTransfers() {
        val transfer = LedgerTransaction(
            99, today, amount = -500, accountId = 1, transferAccountId = 2, tagIds = setOf(dog.id)
        )
        assertTrue(analyse(transfer).isEmpty)
    }
}
