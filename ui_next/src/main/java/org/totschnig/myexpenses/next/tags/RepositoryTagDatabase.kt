package org.totschnig.myexpenses.next.tags

import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.extractTagId
import org.totschnig.myexpenses.db2.loadTagsForTransaction
import org.totschnig.myexpenses.db2.saveTagsForTransaction
import org.totschnig.myexpenses.next.data.tags.TagDatabase
import org.totschnig.myexpenses.next.feature.tags.label

/** [TagDatabase] through the repository of the app */
class RepositoryTagDatabase(private val repository: Repository) : TagDatabase {
    override fun tagIdsOf(transactionId: Long) = repository.loadTagsForTransaction(transactionId).map { it.id }

    override fun setTags(transactionId: Long, tagIds: List<Long>) = repository.saveTagsForTransaction(tagIds, transactionId)

    override fun tagIdOf(label: String) = repository.extractTagId(label)
}
