package org.totschnig.myexpenses.next.data.budgets

import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.LedgerPart
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction

/**
 * Finds the pot a transaction, or a part of a split transaction, is paid from. Each part belongs to
 * at most one pot, so that nothing counts twice:
 *
 * - A pot for a purpose ([PotKind.isPurpose], e.g. a trip) comes before the everyday pots, since
 *   what is spent on the trip is paid from the trip, not from Ausgehen.
 * - Otherwise the most specific match wins: the contract, then the payee, then a tag, then the
 *   deepest category. On equal terms the pot listed first wins.
 * - A payment of a contract only belongs to a pot that lists the contract, e.g. the gym in Fitness.
 *   Other contracts are fixed costs and belong to no pot, even if their category matches.
 * - Movements between own accounts and transfers belong to no pot.
 *
 * Neither the sign nor the date decide, apart from the days of a pot ([Pot.isOpenOn]); which
 * transactions count as spending is up to the analysis.
 *
 * @param pots in the order the user sees them
 * @param categories of the user by id, see [org.totschnig.myexpenses.next.ledger.Catalog.categoriesById]
 * @param contractRules id of the [org.totschnig.myexpenses.next.contracts.ContractRule] by the id
 * of each payment of a confirmed contract
 */
class PotMatcher(
    pots: List<Pot>,
    private val categories: Map<Long, CategoryInfo>,
    private val contractRules: Map<Long, String>,
) {
    private val pots = pots.filter { it.holdsTransactions }

    /** The pot [part] of [transaction] is paid from, null for none */
    fun potOf(transaction: LedgerTransaction, part: LedgerPart): Pot? {
        if (transaction.targetAccountId != null || part.transferAccountId != null) return null
        val contractRule = contractRules[transaction.id]
        val ancestors = part.categoryId?.let(::withAncestors).orEmpty()
        return pots
            .filter { it.isOpenOn(transaction.date) }
            .mapNotNull { pot ->
                specificity(pot, transaction, contractRule, ancestors)?.let { pot to it }
            }
            // maxBy keeps the first of equal ones
            .maxByOrNull { (pot, specificity) -> (if (pot.kind.isPurpose) PURPOSE else 0) + specificity }
            ?.first
    }

    /** The pot each part of [transaction] is paid from, null for a part in none */
    fun potsOf(transaction: LedgerTransaction): List<Pair<LedgerPart, Pot?>> =
        transaction.categoryParts.map { it to potOf(transaction, it) }

    /**
     * How closely [pot] matches, higher is more specific, null if it does not match
     *
     * @param ancestors the category of the part and those above it, the category itself first
     */
    private fun specificity(
        pot: Pot,
        transaction: LedgerTransaction,
        contractRule: String?,
        ancestors: List<Long>,
    ): Int? = when {
        contractRule != null -> if (contractRule in pot.contractRuleIds) CONTRACT else null
        transaction.payeeId != null && transaction.payeeId in pot.payeeIds -> PAYEE
        transaction.tagIds.any { it in pot.tagIds } -> TAG
        else -> ancestors.indexOfFirst { it in pot.categoryIds }.takeIf { it >= 0 }?.let { index ->
            // The deeper the category of the pot, the more specific
            CATEGORY + (ancestors.size - 1 - index)
        }
    }

    private fun withAncestors(categoryId: Long): List<Long> =
        generateSequence(categoryId) { categories[it]?.parentId }.take(MAX_DEPTH).toList()

    private companion object {
        const val CATEGORY = 0
        const val TAG = 100
        const val PAYEE = 200
        const val CONTRACT = 300
        const val PURPOSE = 1000

        /** Guards against a cycle in broken data */
        const val MAX_DEPTH = 20
    }
}
