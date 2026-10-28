package org.totschnig.myexpenses.next.data.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import java.time.LocalDate

class BankFeesTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 29)
    private var nextId = 1L

    private fun transaction(
        amount: Long,
        payee: String? = null,
        comment: String? = null,
        category: String? = null,
        date: LocalDate = today.minusDays(5),
        targetAccountId: Long? = null,
    ) = ContractTransaction(
        id = nextId++, date = date, amount = amount, accountId = 1, payeeName = payee, comment = comment,
        categoryPath = category, targetAccountId = targetAccountId
    )

    private fun monthlyAccountFee(months: Int = 12) = (0 until months).map {
        transaction(-299, payee = "Sparkasse", comment = "Entgeltabrechnung Kontoführung", date = today.minusMonths(it.toLong()).minusDays(1))
    }

    private fun kind(payee: String? = null, comment: String? = null, category: String? = null) =
        BankFee.kindOf(transaction(-100, payee, comment, category))

    @Test
    fun recognizesKinds() {
        assertEquals(BankFeeKind.FOREIGN, kind(comment = "Auslandseinsatzentgelt 1,75 %"))
        assertEquals(BankFeeKind.FOREIGN, kind(comment = "Foreign Transaction Fee"))
        assertEquals(BankFeeKind.ATM, kind(comment = "Entgelt Fremdabhebung"))
        assertEquals(BankFeeKind.CARD, kind(comment = "Jahres-Kartenentgelt Visa"))
        assertEquals(BankFeeKind.OVERDRAFT, kind(comment = "Abrechnung Dispozinsen"))
        assertEquals(BankFeeKind.ACCOUNT, kind(payee = "Sparkasse", comment = "Kontoführungsentgelt"))
        assertEquals(BankFeeKind.OTHER, kind(category = "Finanzielle Aufwendungen > Bankgebühren"))
        assertEquals(BankFeeKind.OTHER, kind(category = "Financial expenses > Bank charges"))
        assertEquals(BankFeeKind.ACCOUNT, kind(category = "Bankgebühren > Kontoführung"))
    }

    @Test
    fun otherFeesAreNoBankFees() {
        assertNull(kind(comment = "Rundfunkgebühr"))
        assertNull(kind(comment = "Parkgebühren"))
        assertNull(kind(category = "Finanzielle Aufwendungen > Gebühren"))
        assertNull(kind(payee = "Sparkasse", comment = "Kartenzahlung REWE"))
        assertNull(kind(payee = "ADAC", comment = "Jahresbeitrag"))
    }

    @Test
    fun sumsFeesOfLastYearByKind() {
        val tip = bankFees(
            monthlyAccountFee(14) + listOf(
                transaction(-175, comment = "Auslandseinsatzentgelt 1,75 %"),
                transaction(-4500, payee = "REWE"),
                transaction(-500, comment = "Auslandseinsatzentgelt", date = today.plusDays(3))
            ),
            today
        )!!
        assertEquals(listOf(BankFeeKind.ACCOUNT, BankFeeKind.FOREIGN), tip.groups.map { it.kind })
        assertEquals(12, tip.groups[0].transactions.size)
        assertEquals(12 * 299L + 175L, tip.yearlyAmount)
    }

    @Test
    fun refundsMovementsAndSmallAmountsAreLeftOut() {
        assertNull(bankFees(listOf(transaction(299, comment = "Erstattung Kontoführung")), today))
        assertNull(bankFees(listOf(transaction(-5000, comment = "Kontoführung", targetAccountId = 2)), today))
        assertNull(bankFees(listOf(transaction(-299, comment = "Kontoführung")), today))
    }

    @Test
    fun dismissedFeesComeBackForNewKindOrMuchHigherAmount() {
        val fees = monthlyAccountFee()
        val tip = bankFees(fees, today)!!
        val decisions = TipDecisions(dismissed = mapOf(tip.id to tip.facts))
        // One more month of the same fee is nothing new
        assertTrue(decisions.isDismissed(bankFees(fees.drop(1) + transaction(-299, comment = "Kontoführung"), today)!!))
        assertFalse(decisions.isDismissed(bankFees(fees + transaction(-175, comment = "Auslandseinsatzentgelt"), today)!!))
        val doubled = fees.map { it.copy(amount = it.amount * 2) }
        assertFalse(decisions.isDismissed(bankFees(doubled, today)!!))
    }
}
