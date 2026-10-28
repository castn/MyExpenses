package org.totschnig.myexpenses.next.data.receipts

import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.categories.PaymentService
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** What of a scanned receipt is compared with the transactions */
data class ReceiptHeader(
    val merchant: String?,
    val date: LocalDate?,
    val total: Long?,
    /** Amount paid by card, differs from [total] by cash back or coupons */
    val cardAmount: Long? = null,
) {
    companion object {
        fun of(receipt: ParsedReceipt) = ReceiptHeader(receipt.merchant, receipt.date, receipt.total, receipt.cardAmount)
    }
}

/** A transaction a scanned receipt may belong to */
data class BookedTransaction(
    val id: Long,
    val date: LocalDate,
    /** In minor units, negative for an expense */
    val amount: Long,
    val payeeName: String? = null,
    val comment: String? = null,
    val isTransfer: Boolean = false,
    val payeeId: Long? = null,
    val hasReceipt: Boolean = false,
    /** For showing the transaction */
    val currency: CurrencyInfo? = null,
    val accountLabel: String? = null,
)

/**
 * A transaction the receipt may belong to and why: its amount is the total or the card amount,
 * its payee is the merchant, [daysAfter] the receipt it was booked
 */
data class ReceiptMatch(
    val transaction: BookedTransaction,
    val amountFits: Boolean,
    val merchantFits: Boolean,
    val daysAfter: Long?,
) {
    internal val score: Int
        get() = (if (amountFits) 100 else 0) + (if (merchantFits) 50 else 0) - 5 * abs(daysAfter ?: 0).toInt()
}

/**
 * Suggests the transactions a scanned receipt belongs to. Banks book a card payment up to a few
 * days after the purchase, so a transaction is looked for from the day before the date of the
 * receipt to [MAX_DAYS_AFTER] days after it, or in the last [DAYS_WITHOUT_DATE] days if the receipt
 * has no readable date. Only expenses without a receipt that are no transfers are candidates, and
 * only if the amount or the merchant fits.
 */
object ReceiptMatcher {

    const val MAX_DAYS_AFTER = 7L
    const val DAYS_WITHOUT_DATE = 30L

    /** Parts of names that say nothing about the merchant */
    private val FILLERS = setOf("markt", "filiale", "sagt", "danke", "und", "der", "die", "das", "bei", "fuer", "für", "ihr")

    fun candidates(receipt: ReceiptHeader, transactions: List<BookedTransaction>, today: LocalDate): List<ReceiptMatch> {
        val amounts = setOfNotNull(receipt.total, receipt.cardAmount).filter { it > 0 }
        val from = receipt.date?.minusDays(1) ?: today.minusDays(DAYS_WITHOUT_DATE)
        val to = receipt.date?.plusDays(MAX_DAYS_AFTER) ?: today
        return transactions.asSequence()
            .filter { it.amount < 0 && !it.isTransfer && !it.hasReceipt && it.date in from..to }
            .map { transaction ->
                ReceiptMatch(
                    transaction,
                    amountFits = -transaction.amount in amounts,
                    merchantFits = receipt.merchant?.let { merchantFits(it, transaction) } == true,
                    daysAfter = receipt.date?.let { ChronoUnit.DAYS.between(it, transaction.date) }
                )
            }
            .filter { it.amountFits || it.merchantFits }
            .sortedWith(compareByDescending<ReceiptMatch> { it.score }.thenByDescending { it.transaction.date })
            .toList()
    }

    /**
     * The candidate to preselect: amount and merchant fit, and no other candidate fits as well.
     * The user still confirms it.
     */
    fun sure(candidates: List<ReceiptMatch>): ReceiptMatch? =
        candidates.filter { it.amountFits && it.merchantFits }.singleOrNull()

    /**
     * The merchant of the receipt is the payee, or the merchant in the purpose of a payment through
     * a payment service, or the purpose of a transaction without payee ("REWE SAGT DANKE 4711")
     */
    fun merchantFits(merchant: String, transaction: BookedTransaction): Boolean {
        val names = when {
            PaymentService.isPaymentService(transaction.payeeName) -> listOfNotNull(PaymentService.merchantOf(transaction.comment))
            transaction.payeeName.isNullOrBlank() -> listOfNotNull(transaction.comment)
            else -> listOf(transaction.payeeName)
        }
        val receiptWords = wordsOf(merchant)
        return names.any { name ->
            val words = wordsOf(name)
            receiptWords.any { r -> words.any { w -> r == w || (r.length >= 3 && w.startsWith(r)) || (w.length >= 3 && r.startsWith(w)) } }
        }
    }

    /**
     * Words of a name that may tell the merchant, and all words joined, as recognition splits
     * names ("RE WE")
     */
    private fun wordsOf(name: String): Set<String> {
        val words = PaymentService.normalizeMerchant(name).split(" ")
            .filter { it.length >= 2 && it.any(Char::isLetter) && it !in FILLERS }
        return (words + words.joinToString("")).filter { it.isNotEmpty() }.toSet()
    }
}
