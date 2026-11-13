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

    @Test
    fun findsAndDropsTheRuleOfACounterpart() {
        val rule = CategoryRule("r", setOf(1, 2), 10, setOf("shop"))
        val decisions = CategoryDecisions(rules = listOf(rule))
        assertEquals(10L, decisions.ruleCategory(rewe))
        assertEquals(10L, decisions.ruleCategory(amazonShop))
        assertEquals(null, decisions.ruleCategory(Counterpart("p:3", "Aldi", payeeId = 3)))
        val withoutRewe = decisions.withoutRule(rewe).rules
        assertEquals(listOf(CategoryRule("r", setOf(2), 10, setOf("shop"))), withoutRewe)
    }

    @Test
    fun keepsOneRuleForCashApartFromThePayees() {
        val decisions = CategoryDecisions()
            .withCashRule(10) { "cash" }
            .withRule(rewe, 10) { "rewe" }
            .withCashRule(11) { "cash2" }
        // The payee does not join the rule for cash, which is replaced
        assertEquals(
            listOf(
                CategoryRule("rewe", setOf(1), 10),
                CategoryRule("cash2", emptySet(), 11, isCash = true)
            ),
            decisions.rules
        )
        assertEquals(11L, decisions.cashRuleCategory)
        // Taking a payee out keeps the rule for cash without payees
        assertEquals(11L, decisions.withoutRule(rewe).cashRuleCategory)
        assertEquals(null, decisions.withoutCashRule().cashRuleCategory)
    }
}
