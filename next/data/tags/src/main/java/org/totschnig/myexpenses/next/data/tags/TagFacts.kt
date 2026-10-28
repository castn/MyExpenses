package org.totschnig.myexpenses.next.data.tags

/**
 * What other features know about tags, e.g. which debits are not usual spending
 *
 * @param excludedFromSpending debits tagged as one-off or reimbursed, see [TagMeaning.isExcludedFromSpending]
 * @param meanings of the tags by id, tags without entry are themes
 */
data class TagFacts(
    val excludedFromSpending: Set<Long> = emptySet(),
    val meanings: Map<Long, TagMeaning> = emptyMap(),
)
