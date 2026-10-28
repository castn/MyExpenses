package org.totschnig.myexpenses.next.data.tips

import org.totschnig.myexpenses.next.data.budgets.BudgetFacts
import org.totschnig.myexpenses.next.data.budgets.PotKind

/**
 * A running pot that was overspent in most of its last periods
 *
 * @param overspent number of the last [PotsOverspent.PERIODS] periods in which more was spent than planned
 * @param averageOver what was spent on top in those periods, on average
 */
data class OverspentPot(
    val potId: String,
    val name: String,
    val overspent: Int,
    val averageOver: Long,
)

/**
 * Pots of the budget overspent again and again: either the amount does not fit, or the spending.
 * Only whole periods since the pot was made count.
 *
 * @param pots most overspent first
 */
data class PotsOverspent(val pots: List<OverspentPot>) : SavingTip {
    override val id: String get() = ID
    override val yearlyAmount: Long get() = pots.sumOf { it.averageOver * 12 }

    /** Comes back for another pot or when a pot was overspent once more */
    override val facts: Set<String> get() = pots.mapTo(HashSet()) { "${it.potId}:${it.overspent}" }

    companion object {
        const val ID = "POTS_OVERSPENT"

        /** The last periods looked at */
        const val PERIODS = 4

        /** Overspent in at least so many of them */
        const val MIN_OVERSPENT = 3
    }
}

internal fun potsOverspent(budget: BudgetFacts?): PotsOverspent? = budget?.pots
    ?.filter { it.pot.kind == PotKind.RUNNING && it.pot.amount > 0 && it.history.size >= PotsOverspent.PERIODS }
    ?.mapNotNull { status ->
        val over = status.history.takeLast(PotsOverspent.PERIODS).map { it - status.pot.amount }.filter { it > 0 }
        if (over.size < PotsOverspent.MIN_OVERSPENT) null
        else OverspentPot(status.pot.id, status.pot.name, over.size, over.sum() / over.size)
    }
    ?.sortedByDescending { it.averageOver }
    ?.takeIf { it.isNotEmpty() }
    ?.let(::PotsOverspent)
