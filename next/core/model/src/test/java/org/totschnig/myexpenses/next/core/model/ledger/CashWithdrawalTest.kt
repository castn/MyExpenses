package org.totschnig.myexpenses.next.core.model.ledger

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashWithdrawalTest {

    private fun debit(method: String? = null, purpose: String? = null, amount: Long = -20000) =
        LedgerTransaction(
            1,
            LocalDate.of(2026, 11, 13),
            amount,
            accountId = 1,
            payeeName = "Sparkasse",
            comment = purpose,
            methodLabel = method
        )

    @Test
    fun recognizesTheBookingTextsOfBanks() {
        listOf("BARGELDAUSZAHLUNG", "GAA-AUSZAHLUNG", "Auszahlung Geldautomat", "Barauszahlung")
            .forEach { assertTrue(it, debit(method = it).isCashWithdrawal) }
        listOf("LASTSCHRIFT", "UMBUCHUNG", "Kartenzahlung", "GUTSCHRIFT")
            .forEach { assertFalse(it, debit(method = it).isCashWithdrawal) }
    }

    @Test
    fun recognizesThePurposeWithoutBookingText() {
        assertTrue(debit(purpose = "GAA 4711 Sparkasse Hauptstr.").isCashWithdrawal)
        assertTrue(debit(purpose = "Bargeldauszahlung 12.11.").isCashWithdrawal)
        // A purpose saying "Auszahlung" is not enough
        assertFalse(debit(purpose = "Auszahlung Darlehen").isCashWithdrawal)
        assertFalse(debit(purpose = "Garage Miete").isCashWithdrawal)
    }

    @Test
    fun takesOnlyDebitsThatAreNoTransfers() {
        assertFalse(debit(method = "BARGELDEINZAHLUNG", amount = 20000).isCashWithdrawal)
        assertFalse(
            debit(method = "BARGELDAUSZAHLUNG").copy(transferAccountId = 2).isCashWithdrawal
        )
        assertFalse(debit(method = "BARGELDAUSZAHLUNG").copy(targetAccountId = 2).isCashWithdrawal)
    }
}
