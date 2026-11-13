package org.totschnig.myexpenses.next.data.setup

import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.data.categories.CategoryPlan
import org.totschnig.myexpenses.next.data.categories.CategorySuggestion

/**
 * A category suggested in the setup
 *
 * @param isCertain every debit it is suggested for gets it for sure, see
 * [CategorySuggestion.isCertain], e.g. by a rule or many earlier debits of the payee: the setup
 * fills it in, while an uncertain one is only offered
 */
data class SetupSuggestion(val categoryId: Long, val isCertain: Boolean)

/**
 * The category suggested for most of the debits of [payee], certain or not, to help deciding;
 * null if none of them has a suggestion
 *
 * @param suggestions by transaction, see [CategoryPlan.suggestions]
 */
fun suggestionFor(payee: SetupPayee, suggestions: Map<Long, CategorySuggestion>): SetupSuggestion? =
    mostSuggested(payee.transactionIds, suggestions)

/** Labels of categories for cash, matched at the start of a word, e.g. "Bargeldabhebung" */
private val CASH_CATEGORY = Regex("\\b(bargeld|cash)", RegexOption.IGNORE_CASE)

/**
 * The category suggested for most of the withdrawals of [cash], else an expense category named for
 * cash, e.g. "Sonstiges > Bargeldabhebung", which is not certain; null if there is none
 *
 * @param suggestions by transaction, see [CategoryPlan.suggestions]
 */
fun suggestionFor(
    cash: SetupCash,
    suggestions: Map<Long, CategorySuggestion>,
    categories: List<CategoryInfo>
): SetupSuggestion? = mostSuggested(cash.transactionIds, suggestions)
    ?: categories.filter { it.isExpense && CASH_CATEGORY.containsMatchIn(it.label) }
        .minByOrNull { it.path.length }
        ?.let { SetupSuggestion(it.id, isCertain = false) }

private fun mostSuggested(
    ids: List<Long>,
    suggestions: Map<Long, CategorySuggestion>
): SetupSuggestion? {
    val byCategory = ids.mapNotNull { suggestions[it] }.groupBy { it.categoryId }
    val (categoryId, most) = byCategory.maxByOrNull { it.value.size } ?: return null
    return SetupSuggestion(categoryId, isCertain = most.all { it.isCertain })
}
