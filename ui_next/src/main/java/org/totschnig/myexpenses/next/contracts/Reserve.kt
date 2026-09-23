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

/**
 * IBANs are compared without spaces and case
 */
fun normalizeIban(iban: String) = iban.filterNot { it.isWhitespace() }.uppercase()

/**
 * Of the movements between own accounts, only money going from a daily account to another kind of
 * account (e.g. a savings account) can form a contract, a reserve. Moving money between daily
 * accounts, e.g. paying the credit card, and the receiving side of a movement are no contracts.
 * Other transactions are kept.
 */
fun List<ContractTransaction>.withoutMovementsOtherThanReserves(dailyAccountIds: Set<Long>) = filter { transaction ->
    val target = transaction.targetAccountId ?: return@filter true
    transaction.amount < 0 && transaction.accountId in dailyAccountIds && target !in dailyAccountIds
}

/**
 * The account a payment goes to, if it is a movement between own accounts: a transfer in the app,
 * or a payment whose counterpart has the IBAN of an own account, e.g. imported from the bank
 *
 * @param ownIbans normalized IBANs of own accounts to their id, see [normalizeIban]
 */
fun ownTransferTarget(accountId: Long, transferAccountId: Long?, counterpartIban: String?, ownIbans: Map<String, Long>) =
    transferAccountId ?: counterpartIban?.let { ownIbans[normalizeIban(it)] }?.takeIf { it != accountId }

