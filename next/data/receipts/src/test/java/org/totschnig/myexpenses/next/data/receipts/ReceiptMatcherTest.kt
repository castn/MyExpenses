package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptMatcherTest {

    private val day = LocalDate.of(2026, 10, 9)
    private val today = LocalDate.of(2026, 10, 11)

    private fun booked(id: Long, amount: Long, payee: String? = null, daysAfter: Long = 1, comment: String? = null) =
        BookedTransaction(id, day.plusDays(daysAfter), amount, payee, comment)

    private fun candidates(receipt: ReceiptHeader, vararg transactions: BookedTransaction) =
        ReceiptMatcher.candidates(receipt, transactions.toList(), today)

    private val rewe = ReceiptHeader("REWE", day, 1215)

    @Test
    fun amountAndMerchantMakeASureMatch() {
        val result = candidates(
            rewe,
            booked(1, -1215, "REWE Markt GmbH"),
            booked(2, -1215, "Tankstelle"),
            booked(3, -499, "REWE Markt GmbH")
        )
        assertEquals(listOf(1L, 2L, 3L), result.map { it.transaction.id })
        assertEquals(1L, ReceiptMatcher.sure(result)?.transaction?.id)
    }

    @Test
    fun twoEqualCandidatesAreNotSure() {
        val result = candidates(rewe, booked(1, -1215, "REWE"), booked(2, -1215, "REWE", daysAfter = 3))
        assertEquals(listOf(1L, 2L), result.map { it.transaction.id })
        assertNull(ReceiptMatcher.sure(result))
    }

    @Test
    fun amountAloneIsNotSure() {
        val result = candidates(rewe, booked(1, -1215, "Lidl"))
        assertEquals(1, result.size)
        assertNull(ReceiptMatcher.sure(result))
    }

    @Test
    fun cardAmountWithCashBackFits() {
        val result = candidates(rewe.copy(cardAmount = 3215), booked(1, -3215, "REWE"))
        assertTrue(result.single().amountFits)
    }

    @Test
    fun onlyExpensesWithoutReceiptWithinTheDays() {
        val result = candidates(
            rewe,
            booked(1, 1215, "REWE"),
            booked(2, -1215, "REWE").copy(isTransfer = true),
            booked(3, -1215, "REWE").copy(hasReceipt = true),
            booked(4, -1215, "REWE", daysAfter = -2),
            booked(5, -1215, "REWE", daysAfter = ReceiptMatcher.MAX_DAYS_AFTER + 1),
            booked(6, -1215, "REWE", daysAfter = -1),
            booked(7, -2000, "Aral")
        )
        assertEquals(listOf(6L), result.map { it.transaction.id })
    }

    @Test
    fun withoutDateTheLastDaysCount() {
        val result = candidates(
            rewe.copy(date = null),
            BookedTransaction(1, today.minusDays(3), -1215, "REWE"),
            BookedTransaction(2, today.minusDays(ReceiptMatcher.DAYS_WITHOUT_DATE + 1), -1215, "REWE")
        )
        assertEquals(listOf(1L), result.map { it.transaction.id })
        assertNull(result.single().daysAfter)
    }

    @Test
    fun merchantAsPrintedAndBooked() {
        fun fits(merchant: String, payee: String?, comment: String? = null) =
            ReceiptMatcher.merchantFits(merchant, BookedTransaction(1, day, -100, payee, comment))
        assertTrue(fits("RE WE", "REWE Markt GmbH sagt Danke 4711"))
        assertTrue(fits("EDEKA Kampmann", "EDEKA Center Kampmann oHG"))
        assertTrue(fits("dm Musterstraße 1", "dm-drogerie markt"))
        assertTrue(fits("Aldi Süd", "ALDI SE u. Co. KG"))
        assertTrue(fits("BAUHAUS", null, "BAUHAUS SAGT DANKE 2026-10-09"))
        assertTrue(fits("Zalando", "PayPal Europe", "Ihr Einkauf bei Zalando SE"))
        assertFalse(fits("Netto", "PayPal Europe", "Ihr Einkauf bei Zalando SE"))
        assertFalse(fits("REWE", "Lidl"))
        assertFalse(fits("Markt", "Marktkauf"))
    }
}
