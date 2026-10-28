package org.totschnig.myexpenses.next.data.tags


/** The tags of transactions as stored by the app */
interface TagDatabase {
    /** The ids of the tags of the transaction */
    fun tagIdsOf(transactionId: Long): List<Long>

    /** Gives the transaction exactly [tagIds] */
    fun setTags(transactionId: Long, tagIds: List<Long>)

    /** The id of the tag [label], created if there is none */
    fun tagIdOf(label: String): Long
}
