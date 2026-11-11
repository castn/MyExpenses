package org.totschnig.myexpenses.next.data.transactions

/** Loads and saves what the details of a transaction can change */
interface TransactionStore {
    /**
     * Null if the details cannot change it: a split, a transfer, a part of a split, cancelled, an archive,
     * or in a closed account. These are left to the classic form.
     */
    suspend fun load(id: Long): EditableTransaction?

    /** The payment methods of accounts of [accountType], for income or for expenses */
    suspend fun methods(accountType: Long, isIncome: Boolean): List<MethodOption>

    suspend fun save(id: Long, values: EditValues)
}
