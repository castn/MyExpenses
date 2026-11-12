package org.totschnig.myexpenses.next.data.setup

import org.totschnig.myexpenses.next.data.categories.CategoryPlan
import org.totschnig.myexpenses.next.data.categories.CategorySuggestion

/**
 * The category suggested for most of the debits of [payee], certain or not, to help deciding;
 * null if none of them has a suggestion
 *
 * @param suggestions by transaction, see [CategoryPlan.suggestions]
 */
fun suggestionFor(payee: SetupPayee, suggestions: Map<Long, CategorySuggestion>): Long? =
    payee.transactionIds
        .mapNotNull { suggestions[it]?.categoryId }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
