package org.totschnig.myexpenses.next.contracts

/**
 * Recognizes contracts that put money aside ("Rücklagen"), e.g. a building savings contract,
 * a savings plan or a standing order to a savings account. They are fixed outflows like other
 * contracts, but no costs: the money stays with the user.
 */
object Reserve {
    /**
     * Matched at the start of a word. Not just "spar", since "Sparkasse" and "Sparda-Bank" are banks.
     */
    private val PREFIXES = listOf(
        "bauspar", "sparplan", "sparkonto", "sparbuch", "sparvertrag", "sparrate", "sparbrief", "sparen",
        "tagesgeld", "festgeld", "depot", "fonds", "wertpapier", "vermögenswirksam", "rücklage", "altersvorsorge",
        "saving", "invest", "brokerage", "etf"
    )

    /** Matched as whole word only */
    private val WORDS = listOf("vl")

    /**
     * Suggests from the category, payee and purpose of the last payment whether a contract is a reserve.
     * Only debits can be reserves.
     */
    fun suggests(contract: Contract): Boolean {
        if (contract.isIncome) return false
        val last = contract.lastTransaction
        return listOfNotNull(last.categoryPath, last.payeeName, last.comment, contract.customName)
            .any { text ->
                text.lowercase().split(Regex("[^\\p{L}]+")).any { word ->
                    word.isNotEmpty() && (PREFIXES.any { word.startsWith(it) } || word in WORDS)
                }
            }
    }
}
