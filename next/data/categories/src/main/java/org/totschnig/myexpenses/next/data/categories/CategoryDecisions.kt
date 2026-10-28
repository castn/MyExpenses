package org.totschnig.myexpenses.next.data.categories

/**
 * What the user decided about categories, stored in the data store
 *
 * @param rules see [CategoryRule]
 * @param firstNewId transactions with a higher id were added after suggestions were started and get
 * certain suggestions applied without asking. Null before the first analysis.
 * @param backlogAnswered the user was asked about the transactions before [firstNewId], see [CategoryPlan.backlog]
 * @param rejected by transaction: the category the user said no to, which is not suggested again
 * @param automatic by transaction: the category applied without asking, so that the details can say so
 */
data class CategoryDecisions(
    val rules: List<CategoryRule> = emptyList(),
    val firstNewId: Long? = null,
    val backlogAnswered: Boolean = false,
    val rejected: Map<Long, Long> = emptyMap(),
    val automatic: Map<Long, Long> = emptyMap(),
) {
    companion object {
        /** At most this many automatic assignments are remembered, the most recent ones */
        const val MAX_AUTOMATIC = 500
    }
}

/**
 * What to do with the suggestions of an analysis
 *
 * @param apply certain suggestions for new transactions, to be applied without asking
 * @param backlog certain suggestions for transactions from before, applied only if the user agrees
 * @param suggestions the remaining ones, shown in the details of their transactions
 */
data class CategoryPlan(
    val apply: List<CategorySuggestion>,
    val backlog: List<CategorySuggestion>,
    val suggestions: Map<Long, CategorySuggestion>,
)

/**
 * Sorts [suggestions] by what happens to them. Suggestions the user said no to are dropped.
 * Before the first analysis ([CategoryDecisions.firstNewId] null) all transactions are from before.
 */
fun CategoryDecisions.plan(suggestions: List<CategorySuggestion>): CategoryPlan {
    val open = suggestions.filter { rejected[it.transactionId] != it.categoryId }
    val (certain, uncertain) = open.partition { it.isCertain }
    val (new, before) = certain.partition { firstNewId != null && it.transactionId >= firstNewId }
    return CategoryPlan(
        apply = new,
        backlog = if (backlogAnswered) emptyList() else before,
        // Certain ones from before are shown as suggestions once the user answered, or until then
        suggestions = (uncertain + before).associateBy { it.transactionId }
    )
}

/**
 * Transactions with [counterpart] always get [categoryId] from now on. The counterpart is taken out
 * of the rule it had; a rule left without counterparts is dropped.
 *
 * @param newId id of a new rule, if there is none for [categoryId] yet
 */
fun CategoryDecisions.withRule(counterpart: Counterpart, categoryId: Long, newId: () -> String): CategoryDecisions {
    val payeeIds = setOfNotNull(counterpart.payeeId)
    val merchants = setOfNotNull(counterpart.merchant)
    val others = rules
        .map { it.copy(payeeIds = it.payeeIds - payeeIds, merchants = it.merchants - merchants) }
        .filter { it.payeeIds.isNotEmpty() || it.merchants.isNotEmpty() }
    val existing = others.find { it.categoryId == categoryId }
    return copy(
        rules = if (existing != null) others.map {
            if (it === existing) it.copy(payeeIds = it.payeeIds + payeeIds, merchants = it.merchants + merchants) else it
        }
        else others + CategoryRule(newId(), payeeIds, categoryId, merchants)
    )
}

/** The user took back [categoryId] of a transaction, which is not suggested or applied again */
fun CategoryDecisions.rejecting(transactionId: Long, categoryId: Long) = copy(
    rejected = rejected + (transactionId to categoryId),
    automatic = automatic - transactionId
)

/** Remembers [applied] as automatic assignments, keeping the most recent [CategoryDecisions.MAX_AUTOMATIC] */
fun CategoryDecisions.withAutomatic(applied: List<CategorySuggestion>) = copy(
    automatic = (automatic + applied.associate { it.transactionId to it.categoryId })
        .entries.sortedByDescending { it.key }.take(CategoryDecisions.MAX_AUTOMATIC).associate { it.key to it.value }
)
