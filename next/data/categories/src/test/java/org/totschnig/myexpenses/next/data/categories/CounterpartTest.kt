package org.totschnig.myexpenses.next.data.categories

import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterpartTest {

    private val clothing = CategoryInfo(1, "Kleidung", "Pflege > Kleidung")
    private val technology = CategoryInfo(2, "Technik", "Freizeit > Technik")
    private val categories = listOf(clothing, technology)
    private var nextId = 1L

    private fun paypal(merchant: String?, categoryId: Long? = null, payeeId: Long = 50) = CategoryInput(
        id = nextId++,
        amount = -4999,
        payeeId = payeeId,
        payeeName = "PayPal Europe S.a.r.l. et Cie S.C.A",
        comment = merchant?.let { "PP.1234.PP . $it, Ihr Einkauf bei $it" } ?: "PP.1234.PP . Ref 998877",
        categoryId = categoryId
    )

    @Test
    fun findsMerchantInPurpose() {
        assertEquals("Zalando SE", PaymentService.merchantOf("PP.1234.PP . Zalando SE, Ihr Einkauf bei Zalando SE"))
        assertEquals("MediaMarkt", PaymentService.merchantOf("Ihr Einkauf bei MediaMarkt 4711 End-to-End-Ref.: 123"))
        assertEquals("Steam", PaymentService.merchantOf("Your purchase at Steam"))
        assertNull(PaymentService.merchantOf("AMZN Mktp DE 302-1234567-1234567"))
        assertEquals("zalando", PaymentService.normalizeMerchant("Zalando SE"))
    }

    @Test
    fun paymentServiceIsReplacedByMerchant() {
        val counterpart = paypal("Zalando SE").counterpart()!!
        assertEquals("Zalando SE", counterpart.name)
        assertEquals("zalando", counterpart.merchant)
        assertNull(counterpart.payeeId)
        assertTrue(PaymentService.isPaymentService("KLARNA BANK AB"))
        assertFalse(PaymentService.isPaymentService("REWE Markt"))
    }

    @Test
    fun paymentServiceWithoutMerchantHasNoCounterpart() {
        assertNull(paypal(null).counterpart())
        assertNull(CategoryInput(1, -2999, 60, "AMZN Mktp DE", "AMZN Mktp DE 302-1234567").counterpart())
    }

    @Test
    fun historyIsPerMerchantNotPerPaymentService() {
        val history = List(5) { paypal("Zalando SE", clothing.id) } + List(5) { paypal("MediaMarkt", technology.id) }
        val suggester = CategorySuggester(history, emptyList(), categories)
        val zalando = suggester.suggest(paypal("Zalando SE"))!!
        assertEquals(clothing.id, zalando.categoryId)
        assertEquals(SuggestionSource.PAYEE, zalando.source)
        assertTrue(zalando.isCertain)
        assertEquals(technology.id, suggester.suggest(paypal("MediaMarkt"))!!.categoryId)
    }

    @Test
    fun paymentServiceAloneGivesNoCertainSuggestion() {
        val history = List(9) { paypal("Zalando SE", clothing.id) }
        val suggestion = CategorySuggester(history, emptyList(), categories).suggest(paypal(null))
        assertTrue(suggestion == null || !suggestion.isCertain)
    }

    @Test
    fun ruleForMerchant() {
        val rule = CategoryRule("r1", emptySet(), technology.id, merchants = setOf("mediamarkt"))
        val suggester = CategorySuggester(emptyList(), listOf(rule), categories)
        val suggestion = suggester.suggest(paypal("MediaMarkt"))!!
        assertEquals(SuggestionSource.RULE, suggestion.source)
        assertEquals(technology.id, suggestion.categoryId)
        assertNull(suggester.suggest(paypal("Zalando SE"))?.takeIf { it.source == SuggestionSource.RULE })
    }

    @Test
    fun ruleForPaymentServiceItselfIsNotApplied() {
        val rule = CategoryRule("r1", setOf(50), technology.id)
        val suggestion = CategorySuggester(emptyList(), listOf(rule), categories).suggest(paypal("Zalando SE"))
        assertTrue(suggestion?.source != SuggestionSource.RULE)
    }
}
