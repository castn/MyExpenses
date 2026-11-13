package org.totschnig.myexpenses.next.data.categories

/** Cash withdrawals always get [categoryId] from now on, see [CategoryRule.isCash] */
fun CategoryDecisions.withCashRule(categoryId: Long, newId: () -> String) = copy(
    rules = rules.filterNot { it.isCash } +
        CategoryRule(newId(), emptySet(), categoryId, isCash = true)
)

/** Cash withdrawals no longer get a category by rule */
fun CategoryDecisions.withoutCashRule() = copy(rules = rules.filterNot { it.isCash })

/** The category of the rule for cash withdrawals, null if there is none */
val CategoryDecisions.cashRuleCategory: Long? get() = rules.find { it.isCash }?.categoryId
