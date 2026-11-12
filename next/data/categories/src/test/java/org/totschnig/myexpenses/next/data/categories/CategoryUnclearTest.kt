package org.totschnig.myexpenses.next.data.categories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryUnclearTest {

    private val rewe = Counterpart("p:1", "REWE", payeeId = 1)
    private val shop = Counterpart("m:shop", "Shop", merchant = "shop")

    private fun certain(transactionId: Long) =
        CategorySuggestion(transactionId, 10, SuggestionSource.PAYEE, isCertain = true)

    private fun uncertain(transactionId: Long) =
        CategorySuggestion(transactionId, 10, SuggestionSource.WORDS, isCertain = false)

    @Test
    fun marksAndUnmarksCounterpart() {
        val decisions = CategoryDecisions().withUnclear(rewe).withUnclear(shop)
        assertEquals(setOf("p:1", "m:shop"), decisions.unclear)
        assertEquals(setOf("m:shop"), decisions.withoutUnclear(rewe).unclear)
    }

    @Test
    fun unclearCounterpartLosesItsRule() {
        val rule = CategoryRule("r", setOf(1, 2), 10, setOf("shop"))
        val decisions = CategoryDecisions(rules = listOf(rule))
            .withUnclear(rewe)
            .withUnclear(shop)
        assertEquals(listOf(CategoryRule("r", setOf(2), 10)), decisions.rules)
    }

    @Test
    fun ruleMakesCounterpartClearAgain() {
        val decisions = CategoryDecisions().withUnclear(rewe).withRule(rewe, 10) { "new" }
        assertTrue(decisions.unclear.isEmpty())
    }

    @Test
    fun findsTransactionsWithoutCategoryOfUnclearCounterparts() {
        val inputs = listOf(
            CategoryInput(1, -100, payeeId = 1, payeeName = "REWE"),
            CategoryInput(2, -100, payeeId = 1, payeeName = "REWE", categoryId = 10),
            CategoryInput(3, -100, payeeName = "PayPal", comment = "Ihr Einkauf bei Shop GmbH"),
            CategoryInput(4, -100, payeeId = 2, payeeName = "Aldi")
        )
        assertEquals(setOf(1L, 3L), unclearIds(inputs, setOf("p:1", "m:shop")))
        assertTrue(unclearIds(inputs, emptySet()).isEmpty())
    }

    @Test
    fun unclearTransactionsAreNeitherSuggestedNorApplied() {
        val suggestions = listOf(certain(99), certain(100), uncertain(101), uncertain(102))
        val plan = CategoryDecisions(firstNewId = 100).plan(suggestions, setOf(99, 100, 101))
        assertTrue(plan.apply.isEmpty())
        assertTrue(plan.backlog.isEmpty())
        assertEquals(setOf(102L), plan.suggestions.keys)
    }
}
