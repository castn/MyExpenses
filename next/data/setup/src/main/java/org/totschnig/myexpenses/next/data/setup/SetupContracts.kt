package org.totschnig.myexpenses.next.data.setup

/**
 * What the setup knows about contracts and regular incomes, from the contracts area, which may be
 * left out (`feat-optional-area`)
 */
sealed interface SetupContracts {

    /** Without the contracts area or without the analysis: there is no contracts or income step */
    data object None : SetupContracts

    /**
     * @param suggestions contracts detected and not confirmed yet, one decision each
     * @param incomeSuggestions regular incomes detected and not confirmed yet, one decision each
     * @param incomes regular incomes confirmed, one of which may be the salary
     */
    data class Available(
        val suggestions: Int,
        val incomeSuggestions: Int = 0,
        val incomes: Int = 0
    ) : SetupContracts {
        /** There are regular incomes, confirmed or not, so the income step has something to ask */
        val hasIncomes: Boolean get() = incomeSuggestions + incomes > 0

        /** The incomes to confirm, and which of them is the salary */
        val incomeDecisions: Int get() = if (hasIncomes) incomeSuggestions + 1 else 0
    }
}
