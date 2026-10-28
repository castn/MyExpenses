package org.totschnig.myexpenses.next.data.categories

import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import java.time.LocalDate

class CategoryInputsTest {

    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun leavesOutTransfersSplitsAndSealedTransactions() {
        val ledger = Ledger(
            listOf(
                LedgerTransaction(1, today, amount = -1000, homeAmount = -1100, accountId = 1, payeeId = 7, categoryId = 3),
                LedgerTransaction(2, today, amount = -2000, accountId = 1, transferAccountId = 2),
                LedgerTransaction(3, today, amount = -3000, accountId = 1, isSplit = true),
                LedgerTransaction(4, today, amount = -4000, accountId = 1, isSealed = true),
                // Movements to own accounts imported from the bank can have a category
                LedgerTransaction(5, today, amount = -5000, accountId = 1, targetAccountId = 2),
            ),
            OwnAccounts(emptySet(), emptyMap())
        )
        assertEquals(
            listOf(CategoryInput(1, -1000, payeeId = 7, categoryId = 3), CategoryInput(5, -5000)),
            categoryInputs(ledger)
        )
    }
}
