package org.totschnig.myexpenses.next.data.tags

import kotlinx.coroutines.flow.first

/** Gives [tagIds] to the transaction, keeping its other tags */
fun TagDatabase.addTags(transactionId: Long, tagIds: Collection<Long>) {
    val current = tagIdsOf(transactionId)
    if (!current.containsAll(tagIds)) setTags(transactionId, (current + tagIds).distinct())
}

/** Gives [tagId] to each of [transactionIds], keeping their other tags */
fun TagDatabase.addTag(transactionIds: Collection<Long>, tagId: Long) {
    transactionIds.forEach { addTags(it, listOf(tagId)) }
}

/** Takes the tags that match [predicate] by id from the transaction */
fun TagDatabase.removeTags(transactionId: Long, predicate: (Long) -> Boolean) {
    val current = tagIdsOf(transactionId)
    if (current.any(predicate)) setTags(transactionId, current.filterNot(predicate))
}

/**
 * Adds to each of [transactionIds] the first tag with [meaning], or a new tag [label] that gets
 * this meaning
 *
 * @param existingTagIds the tags there are, a tag may have been deleted since it got its meaning
 */
suspend fun TagDatabase.addMeaningTag(
    store: TagStore,
    existingTagIds: Set<Long>,
    transactionIds: Collection<Long>,
    meaning: TagMeaning,
    label: String,
) {
    val tagId = store.meanings.first().filterValues { it == meaning }.keys.firstOrNull { it in existingTagIds }
        ?: tagIdOf(label).also { store.setMeaning(it, meaning) }
    addTag(transactionIds, tagId)
}
