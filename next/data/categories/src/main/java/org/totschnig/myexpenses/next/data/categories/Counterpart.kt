package org.totschnig.myexpenses.next.data.categories

import org.totschnig.myexpenses.next.core.model.words.Words

/**
 * Who a transaction is really with: the payee, or for a payment service like PayPal the merchant
 * named in the purpose. Transactions with the same counterpart are categorized alike.
 *
 * @param key identifies the counterpart, without the direction of the transaction
 * @param name to show, e.g. "Zalando SE"
 * @param merchant normalized name of the merchant, for a transaction through a payment service
 */
data class Counterpart(val key: String, val name: String, val payeeId: Long? = null, val merchant: String? = null)

object PaymentService {
    /** Matched at the start of a word of the payee */
    private val SERVICES = listOf(
        "paypal", "klarna", "amazon payments", "amazon eu", "amzn", "sofort", "stripe", "adyen", "mollie", "unzer",
        "ratepay", "afterpay", "billpay", "payone", "computop", "google payment"
    )

    /** What comes before the merchant in the purpose, e.g. "Ihr Einkauf bei Zalando SE" */
    private val MERCHANT = Regex(
        "(?:ihr einkauf bei|ihre bestellung bei|einkauf bei|bestellung bei|your purchase at|purchase at|payment to|zahlung an)" +
                "\\s+(.+?)(?=\\s*[,/;]|\\s+\\d|\\s+ref\\b|\\s+end-to-end|\\s*$)",
        RegexOption.IGNORE_CASE
    )

    /** Legal forms, left out when comparing names of merchants */
    private val LEGAL_FORMS = setOf("se", "gmbh", "ag", "co", "kg", "ohg", "ug", "mbh", "ltd", "inc", "llc", "sarl", "sa", "bv", "nv")

    fun isPaymentService(payeeName: String?): Boolean {
        val text = Words.textOf(payeeName)
        return SERVICES.any { Words.startsWord(text, it) }
    }

    /** The merchant named in the purpose, as written there, null if none is found */
    fun merchantOf(comment: String?): String? =
        comment?.let(MERCHANT::find)?.groupValues?.get(1)?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() }

    /** Merchant names compared without case, punctuation and legal form: "Zalando SE" is "zalando" */
    fun normalizeMerchant(name: String) =
        Words.normalize(name).split(" ").filter { it !in LEGAL_FORMS }.joinToString(" ")
}

/**
 * The payee, or for a payment service the merchant of the purpose. Null for a payment service
 * without merchant, e.g. Amazon, whose purchases are too different to learn from, and for a
 * transaction without payee.
 */
fun CategoryInput.counterpart(): Counterpart? {
    if (PaymentService.isPaymentService(payeeName)) {
        val merchant = PaymentService.merchantOf(comment) ?: return null
        val normalized = PaymentService.normalizeMerchant(merchant).takeIf { it.isNotEmpty() } ?: return null
        return Counterpart("m:$normalized", merchant, merchant = normalized)
    }
    val name = payeeName?.takeIf { it.isNotBlank() }
    return when {
        payeeId != null -> Counterpart("p:$payeeId", name ?: "", payeeId = payeeId)
        name != null -> Counterpart("n:${Words.normalize(name)}", name)
        else -> null
    }
}
