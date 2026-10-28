package org.totschnig.myexpenses.next.data.analysis

import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerPart
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction

/**
 * A part of a transaction as the analyses by category count it, see [countedParts]
 *
 * @param category of the part, null without category
 */
internal class CountedPart(
    val transaction: LedgerTransaction,
    val part: LedgerPart,
    val category: CategoryInfo?,
) {
    /** The main category of the part, null without category */
    val mainId: Long? get() = category?.mainId

    /**
     * Whether the part counts as spending: its category is meant for expenses, so that a credit
     * there is a return, or it has no category and is a debit. All other parts are income.
     */
    val isSpending: Boolean get() = category?.isExpense ?: (part.homeAmount < 0)
}

/**
 * The parts of the transactions in [period] that count for analyses by category. Movements between
 * own accounts and transactions in [excluded] (e.g. reimbursed ones) do not count, nor do transfer
 * parts of split transactions. Split transactions count with their parts.
 *
 * @param excluded ids of transactions that are no spending, see
 * [org.totschnig.myexpenses.next.tags.TagFacts.excludedFromSpending]
 * @param categories of the user by id, see [org.totschnig.myexpenses.next.ledger.Catalog.categoriesById]
 */
internal fun countedParts(
    ledger: Ledger,
    excluded: Set<Long>,
    categories: Map<Long, CategoryInfo>,
    period: SpendingPeriod,
): List<CountedPart> = ledger.transactions
    .filter { it.id !in excluded && it.targetAccountId == null && it.date in period.start..period.end }
    .flatMap { transaction ->
        transaction.categoryParts
            .filter { it.transferAccountId == null }
            .map { part -> CountedPart(transaction, part, part.categoryId?.let { categories[it] }) }
    }
