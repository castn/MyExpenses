package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.LedgerPart
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction

class PotMatcherTest {

    private val food = CategoryInfo(10, "Essen", "Essen")
    private val restaurant = CategoryInfo(11, "Restaurant", "Essen > Restaurant", mainId = 10, parentId = 10)
    private val bar = CategoryInfo(12, "Bar", "Essen > Restaurant > Bar", mainId = 10, parentId = 11)
    private val leisure = CategoryInfo(20, "Freizeit", "Freizeit")
    private val categories = listOf(food, restaurant, bar, leisure).associateBy { it.id }

    private val day = LocalDate.of(2026, 10, 10)

    private fun transaction(
        id: Long = 1,
        categoryId: Long? = bar.id,
        payeeId: Long? = null,
        tagIds: Set<Long> = emptySet(),
        date: LocalDate = day,
        targetAccountId: Long? = null,
        parts: List<LedgerPart> = emptyList(),
    ) = LedgerTransaction(
        id = id, date = date, amount = -1000, accountId = 1, categoryId = categoryId, payeeId = payeeId,
        tagIds = tagIds, targetAccountId = targetAccountId, parts = parts
    )

    private fun matcher(vararg pots: Pot, contractRules: Map<Long, String> = emptyMap()) =
        PotMatcher(pots.toList(), categories, contractRules)

    private fun PotMatcher.potOf(transaction: LedgerTransaction) =
        potOf(transaction, transaction.categoryParts.single())

    @Test
    fun matchesSubcategoriesOfACategory() {
        val pot = Pot(name = "Essen", kind = PotKind.RUNNING, categoryIds = setOf(food.id))
        assertEquals(pot, matcher(pot).potOf(transaction()))
        assertNull(matcher(pot).potOf(transaction(categoryId = leisure.id)))
        assertNull(matcher(pot).potOf(transaction(categoryId = null)))
    }

    @Test
    fun deeperCategoryWins() {
        val food = Pot(name = "Essen", kind = PotKind.RUNNING, categoryIds = setOf(food.id))
        val going = Pot(name = "Ausgehen", kind = PotKind.RUNNING, categoryIds = setOf(restaurant.id))
        assertEquals(going, matcher(food, going).potOf(transaction()))
        assertEquals(going, matcher(going, food).potOf(transaction()))
    }

    @Test
    fun payeeBeforeTagBeforeCategory() {
        val byCategory = Pot(name = "Ausgehen", kind = PotKind.RUNNING, categoryIds = setOf(bar.id))
        val byTag = Pot(name = "Hund", kind = PotKind.YEARLY, tagIds = setOf(5))
        val byPayee = Pot(name = "Coffee to go", kind = PotKind.RUNNING, payeeIds = setOf(7))
        val matcher = matcher(byCategory, byTag, byPayee)
        assertEquals(byPayee, matcher.potOf(transaction(payeeId = 7, tagIds = setOf(5))))
        assertEquals(byTag, matcher.potOf(transaction(payeeId = 8, tagIds = setOf(5))))
        assertEquals(byCategory, matcher.potOf(transaction(payeeId = 8)))
    }

    @Test
    fun firstPotWinsOnEqualTerms() {
        val first = Pot(name = "A", kind = PotKind.RUNNING, categoryIds = setOf(food.id))
        val second = Pot(name = "B", kind = PotKind.RUNNING, categoryIds = setOf(food.id))
        assertEquals(first, matcher(first, second).potOf(transaction()))
    }

    @Test
    fun purposeComesBeforeEverydayPots() {
        val going = Pot(name = "Ausgehen", kind = PotKind.RUNNING, payeeIds = setOf(7))
        val trip = Pot(
            name = "Italien", kind = PotKind.PROJECT, tagIds = setOf(3),
            start = day.minusDays(3), end = day.plusDays(3)
        )
        val matcher = matcher(going, trip)
        assertEquals(trip, matcher.potOf(transaction(payeeId = 7, tagIds = setOf(3))))
        // After the trip, the tag alone no longer counts
        assertEquals(going, matcher.potOf(transaction(payeeId = 7, tagIds = setOf(3), date = day.plusDays(4))))
    }

    @Test
    fun contractPaymentsOnlyInPotsListingTheContract() {
        val leisurePot = Pot(name = "Freizeit", kind = PotKind.RUNNING, categoryIds = setOf(leisure.id))
        val fitness = Pot(name = "Fitness", kind = PotKind.RUNNING, contractRuleIds = setOf("gym"))
        val gym = transaction(id = 2, categoryId = leisure.id)
        val stream = transaction(id = 3, categoryId = leisure.id)
        val rules = mapOf(2L to "gym", 3L to "stream")
        assertEquals(fitness, matcher(leisurePot, fitness, contractRules = rules).potOf(gym))
        assertNull(matcher(leisurePot, fitness, contractRules = rules).potOf(stream))
    }

    @Test
    fun ownMovementsAndTransferPartsBelongToNoPot() {
        val pot = Pot(name = "Essen", kind = PotKind.RUNNING, categoryIds = setOf(food.id))
        assertNull(matcher(pot).potOf(transaction(targetAccountId = 4)))
        val split = transaction(
            categoryId = null,
            parts = listOf(LedgerPart(-600, categoryId = restaurant.id), LedgerPart(-400, transferAccountId = 4))
        )
        assertEquals(listOf(pot, null), matcher(pot).potsOf(split).map { it.second })
    }

    @Test
    fun potsWithoutCriteriaOrForItemGroupsHoldNothing() {
        val empty = Pot(name = "Leer", kind = PotKind.RUNNING)
        val sweets = Pot(name = "Süßes", kind = PotKind.ITEM_GROUP, categoryIds = setOf(food.id), itemGroups = setOf("sweets_snacks"))
        assertNull(matcher(empty, sweets).potOf(transaction()))
    }

    @Test
    fun yearlyPotCountsFromItsStart() {
        val gifts = Pot(name = "Geschenke", kind = PotKind.YEARLY, categoryIds = setOf(leisure.id), start = day)
        assertNull(matcher(gifts).potOf(transaction(categoryId = leisure.id, date = day.minusDays(1))))
        assertEquals(gifts, matcher(gifts).potOf(transaction(categoryId = leisure.id, date = day.plusYears(2))))
    }
}
