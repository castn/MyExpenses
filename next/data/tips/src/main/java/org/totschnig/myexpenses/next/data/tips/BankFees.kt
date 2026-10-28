package org.totschnig.myexpenses.next.data.tips

import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import java.time.LocalDate
import kotlin.math.absoluteValue

/**
 * What a bank charges for, in the order the fees are shown
 */
enum class BankFeeKind { ACCOUNT, CARD, FOREIGN, ATM, OVERDRAFT, OTHER }

/**
 * @param transactions debits of this kind, oldest first
 */
data class BankFeeGroup(val kind: BankFeeKind, val transactions: List<ContractTransaction>) {
    /** Absolute, in minor units of the home currency */
    val amount: Long get() = transactions.sumOf { it.amount.absoluteValue }
}

/**
 * Recognizes bank fees by words in the payee, purpose and category of a debit.
 * Words are matched anywhere in the text, so that e.g. "Kontoführungsentgelt" is found by "kontoführung".
 */
object BankFee {
    /** Checked in this order, the more specific kinds first */
    private val PATTERNS: List<Pair<BankFeeKind, List<String>>> = listOf(
        BankFeeKind.FOREIGN to listOf(
            "auslandseinsatz", "auslandsentgelt", "fremdwährungsentgelt", "fremdwährungsgebühr",
            "währungsumrechnungsentgelt", "foreign transaction fee", "foreign exchange fee", "currency conversion fee"
        ),
        BankFeeKind.ATM to listOf(
            "geldautomatengebühr", "geldautomatenentgelt", "automatenentgelt", "automatengebühr", "fremdabhebung",
            "abhebegebühr", "abhebungsentgelt", "bargeldentgelt", "atm fee", "cash withdrawal fee"
        ),
        BankFeeKind.CARD to listOf("kartengebühr", "kartenentgelt", "card fee"),
        BankFeeKind.OVERDRAFT to listOf("dispozins", "überziehungszins", "überziehungsentgelt", "sollzins", "overdraft"),
        BankFeeKind.ACCOUNT to listOf(
            "kontoführung", "kontofuehrung", "kontoentgelt", "kontogebühr", "kontopreis", "entgeltabrechnung",
            "rechnungsabschluss", "account fee", "account maintenance"
        ),
        // Categories of the default setup, "Bankgebühren" / "Bank charges"
        BankFeeKind.OTHER to listOf("bankgebühr", "bankentgelt", "bank charge", "bank fee", "depotgebühr", "depotentgelt")
    )

    fun kindOf(transaction: ContractTransaction): BankFeeKind? {
        val text = listOfNotNull(transaction.payeeName, transaction.comment, transaction.categoryPath)
            .joinToString(" ") { it.lowercase() }
            // Whitespace as in the regex \s, without one, which takes long on Android
            .split(' ', '\t', '\n', '\u000B', '\u000C', '\r')
            .filter(String::isNotEmpty)
            .joinToString(" ")
        return PATTERNS.firstOrNull { (_, words) -> words.any { it in text } }?.first
    }
}

/**
 * Bank fees debited within the last [SavingTip.BankFees.WINDOW_MONTHS] months. Movements between
 * own accounts are no fees.
 *
 * @param transactions all transactions of the analysis
 */
internal fun bankFees(transactions: List<ContractTransaction>, today: LocalDate): SavingTip.BankFees? {
    val start = today.minusMonths(SavingTip.BankFees.WINDOW_MONTHS)
    val groups = transactions
        .filter { it.amount < 0 && it.targetAccountId == null && it.date.isAfter(start) && !it.date.isAfter(today) }
        .groupBy { BankFee.kindOf(it) }
        .mapNotNull { (kind, fees) -> kind?.let { BankFeeGroup(it, fees.sortedBy(ContractTransaction::date)) } }
        .sortedBy { it.kind }
    return SavingTip.BankFees(groups).takeIf { it.yearlyAmount >= SavingTip.BankFees.MIN_AMOUNT }
}
