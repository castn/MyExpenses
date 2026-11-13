package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts

class CategoryQueueTest {

    private val today = LocalDate.of(2026, 11, 12)
    private var nextId = 1L

    private fun debit(payeeId: Long?, payee: String?, amount: Long, categoryId: Long? = null) =
        LedgerTransaction(
            nextId++,
            today,
            -amount,
            accountId = 1,
            payeeId = payeeId,
            payeeName = payee,
            categoryId = categoryId
        )

    private fun ledger(vararg transactions: LedgerTransaction) =
        Ledger(transactions.toList(), OwnAccounts(emptySet(), emptyMap()))

    @Test
    fun ordersPayeesByMoneyAndStopsAtTheTarget() {
        val ledger = ledger(
            debit(1, "Vermieter", 800),
            debit(2, "REWE", 50),
            debit(2, "REWE", 70),
            debit(3, "Kiosk", 10),
            debit(4, "Bäcker", 20, categoryId = 5),
            debit(5, "Kino", 50)
        )
        val queue = categoryQueue(ledger, unclear = emptySet())
        assertEquals(CategoryCoverage(spent = 1000, categorized = 20), queue.coverage)
        // 20 + 800 = 820 of 1000 reach 80 %
        assertEquals(listOf("Vermieter"), queue.required.map { it.counterpart.name })
        assertEquals(listOf("REWE", "Kino", "Kiosk"), queue.optional.map { it.counterpart.name })
        assertEquals(120L, queue.optional.first().amount)
        assertEquals(2, queue.optional.first().transactionIds.size)
    }

    @Test
    fun takesPayeesUntilTheTargetIsReached() {
        val ledger =
            ledger(debit(1, "A", 400), debit(2, "B", 300), debit(3, "C", 200), debit(4, "D", 100))
        val queue = categoryQueue(ledger, unclear = emptySet())
        // 400 + 300 = 700 is not yet 80 %
        assertEquals(listOf("A", "B", "C"), queue.required.map { it.counterpart.name })
    }

    @Test
    fun unclearPayeesDoNotCount() {
        val ledger = ledger(debit(1, "Unklar", 500), debit(2, "REWE", 400), debit(3, "Kino", 100))
        val queue = categoryQueue(ledger, unclear = setOf("p:1"))
        assertEquals(500L, queue.coverage.unclear)
        assertEquals(500L, queue.coverage.countable)
        assertEquals(listOf("REWE"), queue.required.map { it.counterpart.name })
        assertTrue(queue.all.none { it.counterpart.name == "Unklar" })
    }

    @Test
    fun leavesOutWhatCannotHaveACategoryOrPayee() {
        val credit = debit(1, "Arbeitgeber", -2000)
        val transfer = debit(2, "Sparkonto", 300).copy(transferAccountId = 9)
        val split = debit(3, "Markt", 300).copy(isSplit = true)
        val withoutPayee = debit(null, null, 100)
        val ledger = ledger(credit, transfer, split, withoutPayee)
        val queue = categoryQueue(ledger, unclear = emptySet())
        // The debit without payee counts, but cannot be decided on by payee
        assertEquals(CategoryCoverage(spent = 100, categorized = 0), queue.coverage)
        assertTrue(queue.all.isEmpty())
    }

    @Test
    fun tellsSinceWhenAndTheLastTransactions() {
        val first = debit(1, "Stadtwerke", 30)
            .copy(id = 1, date = today.minusDays(40), comment = "Abschlag Juli")
        val second = debit(1, "Stadtwerke", 25).copy(id = 2, date = today.minusDays(10))
        val last = debit(1, "Stadtwerke", 20)
            .copy(id = 3, date = today.minusDays(5), comment = "Abschlag November")
        val withoutPurpose = debit(1, "Stadtwerke", 10)
            .copy(id = 4, date = today.minusDays(1), comment = " ")
        val ledger = ledger(first, second, last, withoutPurpose)
        val payee = categoryQueue(ledger, unclear = emptySet()).all.single()
        assertEquals(today.minusDays(40), payee.since)
        val expected = listOf(
            SetupTransaction(4, today.minusDays(1), 10),
            SetupTransaction(3, today.minusDays(5), 20, "Abschlag November"),
            SetupTransaction(2, today.minusDays(10), 25)
        )
        assertEquals(expected, payee.recent)
    }

    @Test
    fun withEverythingCategorizedNothingIsLeft() {
        val categorized = ledger(debit(1, "REWE", 100, categoryId = 5))
        val queue = categoryQueue(categorized, unclear = emptySet())
        assertTrue(queue.coverage.reaches(1.0))
        assertTrue(queue.all.isEmpty())
        assertEquals(1.0, categoryQueue(ledger(), unclear = emptySet()).coverage.share, 0.0)
    }
}
