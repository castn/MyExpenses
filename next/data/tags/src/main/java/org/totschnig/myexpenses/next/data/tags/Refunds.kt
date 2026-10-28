package org.totschnig.myexpenses.next.data.tags

import org.totschnig.myexpenses.next.data.categories.CategoryInput
import org.totschnig.myexpenses.next.data.categories.counterpart
import org.totschnig.myexpenses.next.core.model.words.Words
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A transaction as seen by [RefundDetector]
 *
 * @param counterpart who the money went to or came from, see [counterpartKey]
 */
data class RefundInput(
    val id: Long,
    val date: LocalDate,
    val amount: Long,
    val accountId: Long,
    val payeeName: String?,
    val comment: String?,
    val counterpart: String?,
)

/** A debit that was paid back by [credit] */
data class Refund(val debit: RefundInput, val credit: RefundInput) {
    /** Identifies the pair, e.g. to remember that the user said no */
    val key: String get() = "${debit.id}:${credit.id}"

    fun other(transactionId: Long) = if (transactionId == debit.id) credit else debit
}

/**
 * Who a refund comes from. Unlike categories, payment services count without merchant, since
 * Amazon or PayPal pay back to the same payee they were paid to.
 */
fun counterpartKey(payeeId: Long?, payeeName: String?, comment: String?): String? =
    CategoryInput(0, 0, payeeId, payeeName, comment).counterpart()?.key
        ?: payeeName?.takeIf { it.isNotBlank() }?.let { "n:${Words.normalize(it)}" }

/**
 * Finds credits that pay back a debit of the same amount on the same account shortly before:
 * from the same counterpart, or with a word like "Erstattung" in the purpose. Partial refunds
 * are not found, they would leave out the whole debit.
 */
object RefundDetector {
    /** How long after the debit a refund may come */
    const val MAX_DAYS = 60L

    private val WORDS = listOf(
        "erstattung", "rückerstattung", "rueckerstattung", "retoure", "rücksendung", "gutschrift",
        "rückzahlung", "rueckzahlung", "storno", "stornierung", "refund", "reversal"
    )

    fun isRefundText(vararg texts: String?): Boolean {
        val text = Words.textOf(*texts)
        return WORDS.any { Words.contains(text, it) }
    }

    /**
     * @param ignored transactions that are no candidates, e.g. those already marked as reimbursed
     * @param rejected [Refund.key]s the user said no to
     */
    fun find(
        transactions: List<RefundInput>,
        ignored: Set<Long> = emptySet(),
        rejected: Set<String> = emptySet(),
    ): List<Refund> {
        val debits = transactions.filter { it.amount < 0 && it.id !in ignored }
        val used = HashSet<Long>()
        return transactions
            .filter { it.amount > 0 && it.id !in ignored }
            .sortedBy { it.date }
            .mapNotNull { credit ->
                val hasRefundText = isRefundText(credit.payeeName, credit.comment)
                debits
                    .filter { debit ->
                        debit.id !in used &&
                                debit.amount == -credit.amount &&
                                debit.accountId == credit.accountId &&
                                !debit.date.isAfter(credit.date) &&
                                ChronoUnit.DAYS.between(debit.date, credit.date) <= MAX_DAYS &&
                                (hasRefundText || (credit.counterpart != null && debit.counterpart == credit.counterpart)) &&
                                "${debit.id}:${credit.id}" !in rejected
                    }
                    // The closest debit, from the same counterpart if there is one
                    .maxWithOrNull(compareBy({ it.counterpart == credit.counterpart }, { it.date }))
                    ?.let { debit ->
                        used += debit.id
                        Refund(debit, credit)
                    }
            }
    }
}
