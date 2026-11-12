package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.categories.CategoryRule

class AutomaticShareTest {

    private val today = LocalDate.of(2026, 11, 12)
    private val categories = listOf(CategoryInfo(10, "Lebensmittel", "Lebensmittel"))
    private var nextId = 1L

    private fun debit(daysAgo: Long, payeeId: Long, payee: String, categoryId: Long? = null) =
        LedgerTransaction(
            nextId++,
            today.minusDays(daysAgo),
            -1000,
            accountId = 1,
            payeeId = payeeId,
            payeeName = payee,
            categoryId = categoryId
        )

    private fun ledger(transactions: List<LedgerTransaction>) =
        Ledger(transactions, OwnAccounts(emptySet(), emptyMap()))

    @Test
    fun countsRecentDebitsCategorizedWithoutAsking() {
        // Five purchases at REWE with the same category make it certain, the kiosk once is not
        val rewe = (1L..5).map { debit(it * 10, 1, "REWE", categoryId = 10) }
        val kiosk = debit(3, 2, "Kiosk", categoryId = 10)
        // Older than three months, not looked at
        val old = debit(200, 2, "Kiosk", categoryId = 10)
        val share = automaticShare(ledger(rewe + kiosk + old), emptyList(), categories, today)
        assertEquals(AutomaticShare(automatic = 5, total = 6), share)
    }

    @Test
    fun aRuleMakesItCertain() {
        val kiosk = debit(3, 2, "Kiosk")
        val rule = CategoryRule("r", setOf(2), 10)
        val ledger = ledger(listOf(kiosk))
        assertEquals(AutomaticShare(0, 1), automaticShare(ledger, emptyList(), categories, today))
        assertEquals(AutomaticShare(1, 1), automaticShare(ledger, listOf(rule), categories, today))
        assertEquals(0.0, AutomaticShare(0, 0).share, 0.0)
    }
}
