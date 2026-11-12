package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.data.categories.CategoryRule
import org.totschnig.myexpenses.next.data.categories.CategorySuggester
import org.totschnig.myexpenses.next.data.categories.categoryInput
import org.totschnig.myexpenses.next.data.categories.categoryInputs

/**
 * How many of the recent debits would get their category without asking if they came again, to
 * tell the user what the categories step brings
 *
 * @param automatic of the [total] debits of the last [RECENT_MONTHS] months
 */
data class AutomaticShare(val automatic: Int, val total: Int) {
    /** 0 without debits */
    val share: Double get() = if (total == 0) 0.0 else automatic.toDouble() / total

    companion object {
        /** The debits looked at, which stand for the transactions to come */
        const val RECENT_MONTHS = 3L
    }
}

/**
 * Counts the recent debits whose category [CategorySuggester] would apply without asking, from the
 * [rules] and the categories of the transactions of [ledger], see [CategorySuggester.isCertain]
 */
fun automaticShare(
    ledger: Ledger,
    rules: List<CategoryRule>,
    categories: List<CategoryInfo>,
    today: LocalDate
): AutomaticShare {
    val suggester = CategorySuggester(categoryInputs(ledger), rules, categories)
    val start = today.minusMonths(AutomaticShare.RECENT_MONTHS)
    val recent = ledger.transactions.filter {
        it.isCategorizableDebit() && it.date.isAfter(start) && !it.date.isAfter(today)
    }
    // As a new transaction, without the category it has now
    val automatic = recent.count { transaction ->
        suggester.suggest(transaction.categoryInput().copy(categoryId = null))?.isCertain == true
    }
    return AutomaticShare(automatic, recent.size)
}
