package org.totschnig.myexpenses.next.data.tags

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts

class TagRowsTest {

    private val today = LocalDate.of(2026, 10, 6)

    @Test
    fun leavesOutTransfersAndKeepsTagsAndCurrency() {
        val ledger = Ledger(
            listOf(
                LedgerTransaction(
                    1, today, amount = -2000, accountId = 1, payeeId = 5, payeeName = "Hotel Roma",
                    currency = "EUR", originalCurrency = "CHF", tagIds = setOf(7)
                ),
                LedgerTransaction(2, today, amount = -5000, accountId = 1, transferAccountId = 2),
            ),
            OwnAccounts(emptySet(), emptyMap())
        )
        val row = tagRows(ledger).single()
        assertEquals(1L, row.refund.id)
        assertTrue(row.isForeign)
        assertEquals(setOf(7L), row.toTagInput().tags)
        assertEquals(-2000L, row.toTripInput().amount)
    }
}
