package org.totschnig.myexpenses.next.feature.tags

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.totschnig.myexpenses.next.data.tags.TagDatabase

/**
 * Tags by transaction in memory; new tags get ids from 100 on. Writes may come from the thread of
 * the data store, a test waits for them by [writes].
 */
class FakeTagDatabase : TagDatabase {
    val tags = ConcurrentHashMap<Long, List<Long>>()
    val labels = ConcurrentHashMap<String, Long>()

    private val writeCount = MutableStateFlow(0)

    /** Number of calls of [setTags] */
    val writes: StateFlow<Int> = writeCount

    override fun tagIdsOf(transactionId: Long) = tags[transactionId].orEmpty()

    override fun setTags(transactionId: Long, tagIds: List<Long>) {
        tags[transactionId] = tagIds
        writeCount.update { it + 1 }
    }

    override fun tagIdOf(label: String) = labels.getOrPut(label) { 100L + labels.size }
}
