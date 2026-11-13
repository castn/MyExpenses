package org.totschnig.myexpenses.next.data.categories

import java.io.Serializable

/**
 * What [CategoryRepository.categorize] changed, so that [CategoryRepository.takeBack] can undo it.
 * [Serializable] for the saved state.
 *
 * @param transactionIds that were to get [categoryId], those that had one keep theirs
 * @param hasSetRule whether the counterpart got a rule for [categoryId]
 * @param ruleBefore the category of the rule the counterpart had before, if [hasSetRule]
 */
data class Categorization(
    val counterpart: Counterpart,
    val transactionIds: List<Long>,
    val categoryId: Long,
    val hasSetRule: Boolean,
    val ruleBefore: Long? = null
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}
