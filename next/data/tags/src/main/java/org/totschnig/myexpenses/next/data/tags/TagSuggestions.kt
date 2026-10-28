package org.totschnig.myexpenses.next.data.tags

/**
 * A transaction as seen by [TagLearner]
 *
 * @param counterpart see [org.totschnig.myexpenses.next.categories.Counterpart.key], null for
 * payment services without merchant, whose purchases are too different to learn from
 * @param tags ids of the tags the transaction has
 */
data class TagInput(
    val id: Long,
    val counterpart: String?,
    val counterpartName: String?,
    val tags: Set<Long>,
)

/**
 * [tagId] fits [transactionId], since the other transactions of its counterpart mostly have it
 *
 * @param tagged transactions of the counterpart with this tag
 * @param others transactions of the counterpart without this tag, [transactionId] included
 */
data class TagSuggestion(
    val transactionId: Long,
    val tagId: Long,
    val counterpart: String,
    val counterpartName: String,
    val tagged: Int,
    val others: List<Long>,
) {
    /** Identifies the suggestion, e.g. to remember that the user said no */
    val key: String get() = "$transactionId:$tagId"
}

/**
 * The user decided: transactions with [counterpart] get [tagId] ("Immer so machen").
 * Only transactions newer than [afterId] are tagged automatically, older ones were offered when
 * the rule was made.
 */
data class TagRule(val counterpart: String, val tagId: Long, val afterId: Long) {
    companion object {
        /** "p:12|7|5003;m:zalando|9|5003", broken entries are dropped */
        fun decode(text: String?): List<TagRule> = text.orEmpty().split(';').mapNotNull { entry ->
            val parts = entry.split('|').takeIf { it.size == 3 } ?: return@mapNotNull null
            val tagId = parts[1].toLongOrNull() ?: return@mapNotNull null
            val afterId = parts[2].toLongOrNull() ?: return@mapNotNull null
            parts[0].takeIf { it.isNotEmpty() }?.let { TagRule(it, tagId, afterId) }
        }

        fun encode(rules: List<TagRule>) = rules.joinToString(";") { "${it.counterpart}|${it.tagId}|${it.afterId}" }
    }
}

/**
 * Learns which tags belong to a counterpart: a tag given to at least [MIN_COUNT] of its transactions
 * and to [MIN_SHARE] of those with any learnable tag is suggested for the others.
 */
object TagLearner {
    const val MIN_COUNT = 3
    const val MIN_SHARE = 0.8

    /**
     * @param learnable tags that can be learned: themes and [TagMeaning.BUSINESS], not those that
     * depend on the situation like trips
     * @param rejected [TagSuggestion.key]s the user said no to
     * @return one suggestion per transaction, the tag given most often
     */
    fun suggest(
        inputs: List<TagInput>,
        learnable: (Long) -> Boolean,
        rejected: Set<String> = emptySet(),
    ): Map<Long, TagSuggestion> = inputs
        .filter { it.counterpart != null }
        .groupBy { it.counterpart!! }
        .flatMap { (counterpart, transactions) ->
            val tagged = transactions.filter { transaction -> transaction.tags.any(learnable) }
            tagged.flatMap { it.tags.filter(learnable) }
                .groupingBy { it }.eachCount()
                .filter { (_, count) -> count >= MIN_COUNT && count >= tagged.size * MIN_SHARE }
                .flatMap { (tagId, count) ->
                    val others = transactions.filter { tagId !in it.tags }
                    others.map { transaction ->
                        TagSuggestion(
                            transaction.id, tagId, counterpart,
                            transactions.firstNotNullOfOrNull { it.counterpartName?.takeIf(String::isNotBlank) } ?: "",
                            count, others.map { it.id }
                        )
                    }
                }
        }
        .filter { it.key !in rejected }
        .groupBy { it.transactionId }
        .mapValues { (_, suggestions) -> suggestions.maxBy { it.tagged } }

    /** Tags to add by [rules], by transaction id */
    fun applyRules(inputs: List<TagInput>, rules: List<TagRule>): Map<Long, Set<Long>> = inputs
        .mapNotNull { input ->
            rules.filter { it.counterpart == input.counterpart && input.id > it.afterId && it.tagId !in input.tags }
                .takeIf { it.isNotEmpty() }
                ?.let { matching -> input.id to matching.mapTo(HashSet()) { it.tagId } }
        }
        .toMap()
}
