package org.totschnig.myexpenses.next.data.budgets

import org.totschnig.myexpenses.next.data.contracts.Contract

/**
 * What is suggested for a pot while the user creates or edits it
 *
 * @param amount what was spent on it in the periods before: per period for a running pot (the
 * median of the last [BASIS_PERIODS] periods with spending), per year for a yearly pot (the last
 * 12 periods); rounded up, null if nothing was spent
 * @param periods number of periods [amount] is based on
 * @param contracts confirmed contracts whose category the pot holds and that no other pot lists,
 * e.g. the gym for Fitness
 */
data class PotSuggestion(
    val amount: Long?,
    val periods: Int,
    val contracts: List<Contract>,
) {
    companion object {
        const val BASIS_PERIODS = 3
    }
}

object PotSuggestions {

    /**
     * @param input the budget with the pots as they are; [pot] replaces the one with its id or is
     * added, so that what other pots hold more closely is not counted
     */
    fun suggest(input: BudgetInput, pot: Pot): PotSuggestion {
        // As running pot without start, so that the history covers all periods
        val candidate = pot.copy(kind = PotKind.RUNNING, start = null, rollover = false, contractRuleIds = emptySet())
        val others = input.pots.filter { it.id != pot.id }
        val status = BudgetAnalyzer.analyse(input.copy(pots = others + candidate, offset = 0))
            .pots.single { it.pot.id == pot.id }
        val (amount, periods) = when (pot.kind) {
            PotKind.YEARLY -> {
                val year = status.history.takeLast(12)
                year.sum().takeIf { it > 0 } to year.size
            }

            else -> {
                val spent = status.history.filter { it > 0 }.takeLast(PotSuggestion.BASIS_PERIODS)
                spent.sorted().getOrNull(spent.size / 2) to spent.size
            }
        }
        val listed = others.flatMapTo(HashSet()) { it.contractRuleIds }
        val categoryIds = input.categories.values.associateBy({ it.path }, { it.id })
        val contracts = input.contracts.filter { contract ->
            val ruleId = contract.rule?.id
            ruleId != null && ruleId !in listed && !contract.isIncome && !contract.isReserve &&
                    contract.isActive && !contract.isCancelled &&
                    contract.categoryPath?.let(categoryIds::get)?.let { holds(input, pot, it) } == true
        }
        return PotSuggestion(amount?.let(::roundUp), periods, contracts)
    }

    private fun holds(input: BudgetInput, pot: Pot, categoryId: Long) =
        generateSequence(categoryId) { input.categories[it]?.parentId }.take(20).any { it in pot.categoryIds }

    /** To full 5 below 100, else to full 10, in minor units of a currency with 2 fraction digits */
    internal fun roundUp(amount: Long): Long {
        val step = if (amount < 10000) 500 else 1000
        return (amount + step - 1) / step * step
    }
}
