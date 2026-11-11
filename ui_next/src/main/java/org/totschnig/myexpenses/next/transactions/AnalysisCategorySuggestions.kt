package org.totschnig.myexpenses.next.transactions

import kotlinx.coroutines.flow.first
import org.totschnig.myexpenses.next.data.categories.CategoryInput
import org.totschnig.myexpenses.next.data.categories.CategoryRepository
import org.totschnig.myexpenses.next.data.transactions.CategorySuggestions
import org.totschnig.myexpenses.next.data.transactions.SuggestedCategory

/**
 * [CategorySuggestions] with the suggestions of the categories, learned from the transactions the
 * user categorized. Waits for the analysis when nothing else has started it, e.g. on the screen
 * for a new transaction.
 */
class AnalysisCategorySuggestions(private val repository: CategoryRepository) :
    CategorySuggestions {
    override suspend fun suggest(
        payee: String?,
        comment: String?,
        isIncome: Boolean
    ): SuggestedCategory? {
        if (payee == null && comment == null) return null
        val analysis = repository.analysis.first() ?: return null
        // The earlier transactions are learned by the id of their payee, a typed name is looked up
        val payeeId = payee?.let { name ->
            analysis.inputs.firstOrNull {
                it.payeeId != null &&
                    it.payeeName.equals(name, ignoreCase = true)
            }?.payeeId
        }
        // A transaction not saved yet: no id, and only the sign of its amount counts
        val input = CategoryInput(
            id = 0,
            amount = if (isIncome) 1 else -1,
            payeeId = payeeId,
            payeeName = payee,
            comment = comment
        )
        val suggestion = analysis.suggestFor(input) ?: return null
        val path = analysis.paths[suggestion.categoryId] ?: return null
        return SuggestedCategory(suggestion.categoryId, path, suggestion.isCertain)
    }
}
