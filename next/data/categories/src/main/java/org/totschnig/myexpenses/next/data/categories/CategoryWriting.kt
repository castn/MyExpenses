package org.totschnig.myexpenses.next.data.categories

/**
 * Changes of the category of transactions. Each change only applies while the transaction still has
 * the category it was decided on, so that a change by the user in between is not overwritten.
 * Transactions in sealed accounts or of sealed debts are left as they are.
 */
interface CategoryWriting {
    /** Gives the transaction [categoryId] if it has none or still has [fromCategoryId] */
    suspend fun changeCategory(transactionId: Long, fromCategoryId: Long?, categoryId: Long)

    /** Takes [categoryId] from the transaction, if it still has it */
    suspend fun removeCategory(transactionId: Long, categoryId: Long)

    /** Gives [categoryId] to those of [transactionIds] that have no category yet */
    suspend fun setMissingCategory(transactionIds: List<Long>, categoryId: Long)
}
