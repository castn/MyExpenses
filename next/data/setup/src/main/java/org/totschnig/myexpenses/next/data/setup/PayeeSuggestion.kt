package org.totschnig.myexpenses.next.data.setup

import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.data.categories.CategoryPlan
import org.totschnig.myexpenses.next.data.categories.CategorySuggestion

/**
 * The category suggested for most of the debits of [payee], certain or not, to help deciding;
 * null if none of them has a suggestion
 *
 * @param suggestions by transaction, see [CategoryPlan.suggestions]
 */
fun suggestionFor(payee: SetupPayee, suggestions: Map<Long, CategorySuggestion>): Long? =
    mostSuggested(payee.transactionIds, suggestions)

/** Labels of categories for cash, matched at the start of a word, e.g. "Bargeldabhebung" */
private val CASH_CATEGORY = Regex("\\b(bargeld|cash)", RegexOption.IGNORE_CASE)

/**
 * The category suggested for most of the withdrawals of [cash], else an expense category named for
 * cash, e.g. "Sonstiges > Bargeldabhebung"; null if there is none
 *
 * @param suggestions by transaction, see [CategoryPlan.suggestions]
 */
fun suggestionFor(
    cash: SetupCash,
    suggestions: Map<Long, CategorySuggestion>,
    categories: List<CategoryInfo>
): Long? = mostSuggested(cash.transactionIds, suggestions)
    ?: categories.filter { it.isExpense && CASH_CATEGORY.containsMatchIn(it.label) }
        .minByOrNull { it.path.length }?.id

private fun mostSuggested(ids: List<Long>, suggestions: Map<Long, CategorySuggestion>): Long? =
    ids.mapNotNull { suggestions[it]?.categoryId }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
