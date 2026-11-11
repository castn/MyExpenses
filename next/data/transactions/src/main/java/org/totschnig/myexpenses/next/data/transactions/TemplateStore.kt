package org.totschnig.myexpenses.next.data.transactions

import kotlinx.coroutines.flow.Flow

/** Loads and saves the templates of transactions, implemented by the app */
interface TemplateStore {
    /** All templates, the ones used most first, updated when they change */
    fun templates(): Flow<List<TemplateSummary>>

    /** Null if it is gone or cannot be put into the form, a split or a transfer */
    suspend fun load(id: Long): EditableTemplate?

    /** @return its id */
    suspend fun create(values: TemplateValues): Long

    suspend fun save(id: Long, values: TemplateValues)

    suspend fun delete(id: Long)

    /** Saves a transaction as the template holds it, dated today; also a split or a transfer */
    suspend fun apply(id: Long): TemplateApplied

    /** A transaction filled from template [id] was saved in the form */
    suspend fun used(id: Long)

    /**
     * Saves transaction [transactionId] as it is as a new template titled after its payee or
     * category, also a split or a transfer the form cannot show. Fails if its account is closed.
     *
     * @return the id of the template
     */
    suspend fun createFrom(transactionId: Long): Long
}
