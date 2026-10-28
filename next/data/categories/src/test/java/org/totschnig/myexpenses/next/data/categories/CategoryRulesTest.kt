package org.totschnig.myexpenses.next.data.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRulesTest {

    private val rewe = Counterpart("p:1", "REWE", payeeId = 1)
    private val amazonShop = Counterpart("m:shop", "Shop", merchant = "shop")

    @Test
    fun addsRuleForNewCategory() {
        val decisions = CategoryDecisions().withRule(rewe, 10) { "new" }
        assertEquals(listOf(CategoryRule("new", setOf(1), 10)), decisions.rules)
    }

    @Test
    fun joinsCounterpartsOfTheSameCategory() {
        val decisions = CategoryDecisions(rules = listOf(CategoryRule("r", setOf(2), 10)))
            .withRule(amazonShop, 10) { "new" }
        assertEquals(listOf(CategoryRule("r", setOf(2), 10, setOf("shop"))), decisions.rules)
    }

    @Test
    fun movesCounterpartToNewCategoryAndDropsEmptyRule() {
        val decisions = CategoryDecisions(rules = listOf(CategoryRule("r", setOf(1), 10)))
            .withRule(rewe, 11) { "new" }
        assertEquals(listOf(CategoryRule("new", setOf(1), 11)), decisions.rules)
    }
}
