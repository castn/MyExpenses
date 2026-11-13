package org.totschnig.myexpenses.next.core.model.ledger

/**
 * Words of booking texts of banks for cash taken from the account, matched at the start of a word,
 * e.g. "BARGELDAUSZAHLUNG", "GAA-AUSZAHLUNG", "Auszahlung Geldautomat"
 */
private val CASH_METHOD = Regex(
    "\\b(bargeld|barauszahlung|auszahlung|geldautomat|gaa\\b|atm\\b)",
    RegexOption.IGNORE_CASE
)

/**
 * The same in the purpose, for imports without booking text; "Auszahlung" alone is left out, since
 * purposes use it for other things, e.g. the payout of a loan
 */
private val CASH_PURPOSE = Regex(
    "\\b(bargeld|barauszahlung|geldautomat|gaa\\b|atm\\b)",
    RegexOption.IGNORE_CASE
)

/**
 * Whether this debit took cash from the account, e.g. at a cash machine: by the booking text of the
 * bank ([LedgerTransaction.methodLabel]), else by the purpose. Transfers in the app are not, also
 * not those to a cash account.
 */
val LedgerTransaction.isCashWithdrawal: Boolean
    get() = amount < 0 && transferAccountId == null && targetAccountId == null &&
        (hasWords(methodLabel, CASH_METHOD) || hasWords(comment, CASH_PURPOSE))

private fun hasWords(text: String?, words: Regex) = text?.let { words.containsMatchIn(it) } == true
