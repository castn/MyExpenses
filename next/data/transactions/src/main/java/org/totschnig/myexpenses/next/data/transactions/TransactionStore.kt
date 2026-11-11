package org.totschnig.myexpenses.next.data.transactions

/** Loads and saves transactions entered or changed by hand, implemented by the app */
interface TransactionStore {
    /**
     * Null if it cannot be changed here: a split, a transfer, a part of a split, cancelled, an
     * archive, or in a closed account. These are left to the classic form.
     */
    suspend fun load(id: Long): EditableTransaction?

    /** The payment methods of accounts of [accountType], for income or for expenses */
    suspend fun methods(accountType: Long, isIncome: Boolean): List<MethodOption>

    /** The open accounts a transaction can be entered in, the one used last first */
    suspend fun accounts(): List<AccountOption>

    /** The tags [accountId] gives new transactions */
    suspend fun defaultTags(accountId: Long): List<Long>

    /**
     * Writes what differs between [from] and [to], so that a change made meanwhile to another
     * value, e.g. the category from its chip, stays. A payee without a payee of that name creates
     * one.
     */
    suspend fun save(id: Long, from: EditValues, to: EditValues)

    /**
     * Creates a transaction with [values], a payee without a payee of that name creates one. Fails
     * if the account is closed.
     *
     * @return its id
     */
    suspend fun create(values: EditValues): Long
}

/** A category suggested for a transaction that is entered */
data class SuggestedCategory(
    val id: Long,
    val path: String,
    /** Applied without asking, e.g. by a rule of the user */
    val isCertain: Boolean
)

/** Suggests a category for what is typed, implemented with the suggestions of the categories */
fun interface CategorySuggestions {
    /** Null if there is none, or the user did not agree to analyse transactions */
    suspend fun suggest(payee: String?, comment: String?, isIncome: Boolean): SuggestedCategory?

    companion object {
        /** Suggests nothing, e.g. where the categories are left out */
        val None = CategorySuggestions { _, _, _ -> null }
    }
}
