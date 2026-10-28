package org.totschnig.myexpenses.next.data.tags

import org.totschnig.myexpenses.next.core.model.ledger.Ledger

/**
 * Whether a tag with this meaning (null for a theme) can be learned from the counterpart: themes
 * and business expenses depend on who is paid, one-off, refunds and trips on the situation
 */
val TagMeaning?.isLearnable: Boolean
    get() = this == null || this == TagMeaning.BUSINESS

/**
 * What a tag says about its transactions, so that analyses can take it into account.
 * Tags without meaning are themes (Renovierung, Hochzeit), only for filtering and analysis.
 * Themes and trips are shown to the user as groups, the others as [isMarker].
 */
enum class TagMeaning {
    /** Does not recur, left out of averages and outliers */
    ONE_OFF,

    /** Paid back, not really spent */
    REIMBURSED,

    /** Spent on a trip, one tag per trip */
    TRIP,

    /** For work, possibly paid back or tax deductible */
    BUSINESS;

    /**
     * Shown as a fixed property of a transaction that is on or off, not as a group. The tag behind
     * it is the first one with this meaning, see [MARKERS].
     */
    val isMarker: Boolean
        get() = this != TRIP

    /** Left out of what is usually spent: averages, outliers and the tips about spending */
    val isExcludedFromSpending: Boolean
        get() = this == ONE_OFF || this == REIMBURSED

    companion object {
        /** In the order shown in the details */
        val MARKERS = listOf(ONE_OFF, REIMBURSED, BUSINESS)

        /** Ids of the transactions of [ledger] with a tag whose meaning matches [predicate] */
        fun taggedWith(ledger: Ledger, meanings: Map<Long, TagMeaning>, predicate: (TagMeaning) -> Boolean): Set<Long> =
            ledger.transactions
                .filter { transaction -> transaction.tagIds.any { tagId -> meanings[tagId]?.let(predicate) == true } }
                .mapTo(HashSet()) { it.id }

        /** Transactions with a tag whose meaning is [isExcludedFromSpending] */
        fun excludedFromSpending(ledger: Ledger, meanings: Map<Long, TagMeaning>): Set<Long> =
            taggedWith(ledger, meanings) { it.isExcludedFromSpending }

        /** "12=ONE_OFF,15=TRIP", unknown entries are dropped */
        fun decode(text: String?): Map<Long, TagMeaning> = text.orEmpty()
            .split(',')
            .mapNotNull { entry ->
                val (id, meaning) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val tagId = id.toLongOrNull() ?: return@mapNotNull null
                entries.find { it.name == meaning }?.let { tagId to it }
            }
            .toMap()

        fun encode(meanings: Map<Long, TagMeaning>) =
            meanings.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value.name}" }
    }
}
