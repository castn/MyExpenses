package org.totschnig.myexpenses.next.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RefundDetectorTest {

    private val day: LocalDate = LocalDate.of(2026, 9, 1)
    private var nextId = 1L

    private fun transaction(
        amount: Long,
        daysLater: Long,
        payee: String?,
        comment: String? = null,
        accountId: Long = 1,
    ) = RefundInput(
        nextId++, day.plusDays(daysLater), amount, accountId, payee, comment,
        counterpartKey(null, payee, comment)
    )

    @Test
    fun creditOfSameAmountFromSamePayee() {
        val debit = transaction(-4999, 0, "Zalando SE")
        val credit = transaction(4999, 12, "Zalando SE")
        val refunds = RefundDetector.find(listOf(debit, transaction(-1299, 2, "REWE"), credit))
        assertEquals(listOf(Refund(debit, credit)), refunds)
        assertEquals("${debit.id}:${credit.id}", refunds.single().key)
        assertEquals(credit, refunds.single().other(debit.id))
    }

    @Test
    fun refundWordsMatchAnotherPayee() {
        val debit = transaction(-2599, 0, "AMAZON EU S.A R.L.")
        val credit = transaction(2599, 20, "Amazon Payments Europe", "Erstattung Bestellung 302-123")
        assertEquals(listOf(Refund(debit, credit)), RefundDetector.find(listOf(debit, credit)))
    }

    @Test
    fun noRefundWithoutSamePayeeOrWords() {
        val debit = transaction(-5000, 0, "Max Mustermann")
        val credit = transaction(5000, 3, "Erika Musterfrau", "Geburtstag")
        assertTrue(RefundDetector.find(listOf(debit, credit)).isEmpty())
    }

    @Test
    fun noRefundTooLateBeforeOrOnOtherAccountOrPartial() {
        val debit = transaction(-4999, 10, "Zalando SE")
        val candidates = listOf(
            transaction(4999, 10 + RefundDetector.MAX_DAYS + 1, "Zalando SE"),
            transaction(4999, 5, "Zalando SE"),
            transaction(4999, 15, "Zalando SE", accountId = 2),
            transaction(2000, 15, "Zalando SE", "Erstattung")
        )
        candidates.forEach { assertTrue(RefundDetector.find(listOf(debit, it)).isEmpty()) }
    }

    @Test
    fun eachDebitIsPaidBackOnceByTheClosest() {
        val first = transaction(-1999, 0, "Zalando SE")
        val second = transaction(-1999, 5, "Zalando SE")
        val refund = transaction(1999, 8, "Zalando SE")
        val secondRefund = transaction(1999, 9, "Zalando SE")
        assertEquals(
            listOf(Refund(second, refund), Refund(first, secondRefund)),
            RefundDetector.find(listOf(first, second, refund, secondRefund))
        )
    }

    @Test
    fun ignoredAndRejectedAreNotSuggested() {
        val debit = transaction(-4999, 0, "Zalando SE")
        val credit = transaction(4999, 12, "Zalando SE")
        assertTrue(RefundDetector.find(listOf(debit, credit), ignored = setOf(debit.id)).isEmpty())
        assertTrue(RefundDetector.find(listOf(debit, credit), rejected = setOf("${debit.id}:${credit.id}")).isEmpty())
    }

    @Test
    fun paymentServiceWithoutMerchantStillHasCounterpart() {
        assertEquals("n:paypal europe", counterpartKey(null, "PayPal Europe", "Rückzahlung"))
    }
}
