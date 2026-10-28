package org.totschnig.myexpenses.next.data.budgets

/**
 * What other features know about the budget, e.g. saving tips
 *
 * @param pots where they stand in the current period, with their earlier periods
 */
data class BudgetFacts(val pots: List<PotStatus>)
