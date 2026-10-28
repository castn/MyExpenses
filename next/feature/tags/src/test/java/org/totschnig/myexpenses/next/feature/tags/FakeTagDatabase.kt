package org.totschnig.myexpenses.next.feature.tags

import org.totschnig.myexpenses.next.data.tags.TagDatabase

/** Tags by transaction in memory; new tags get ids from 100 on */
class FakeTagDatabase : TagDatabase {
    val tags = HashMap<Long, List<Long>>()
    val labels = HashMap<String, Long>()

    override fun tagIdsOf(transactionId: Long) = tags[transactionId].orEmpty()

    override fun setTags(transactionId: Long, tagIds: List<Long>) {
        tags[transactionId] = tagIds
    }

    override fun tagIdOf(label: String) = labels.getOrPut(label) { 100L + labels.size }
}
