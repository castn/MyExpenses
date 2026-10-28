package org.totschnig.myexpenses.next.data.tags

import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import java.time.LocalDate

class TagMeaningTest {

    @Test
    fun roundTrip() {
        val meanings = mapOf(15L to TagMeaning.TRIP, 12L to TagMeaning.ONE_OFF)
        assertEquals("12=ONE_OFF,15=TRIP", TagMeaning.encode(meanings))
        assertEquals(meanings, TagMeaning.decode(TagMeaning.encode(meanings)))
    }

    @Test
    fun brokenEntriesAreDropped() {
        assertEquals(
            mapOf(3L to TagMeaning.REIMBURSED),
            TagMeaning.decode("3=REIMBURSED,x=TRIP,4=UNKNOWN,5,")
        )
        assertEquals(emptyMap<Long, TagMeaning>(), TagMeaning.decode(null))
        assertEquals(emptyMap<Long, TagMeaning>(), TagMeaning.decode(""))
    }

    @Test
    fun oneOffAndReimbursedAreExcludedFromSpending() {
        val meanings = mapOf(1L to TagMeaning.ONE_OFF, 2L to TagMeaning.REIMBURSED, 3L to TagMeaning.TRIP)
        fun transaction(id: Long, vararg tagIds: Long) =
            LedgerTransaction(id, LocalDate.of(2026, 10, 5), amount = -100, accountId = 1, tagIds = tagIds.toSet())
        val ledger = Ledger(
            listOf(transaction(10, 1, 3), transaction(11, 2), transaction(12, 3), transaction(13, 4), transaction(14)),
            OwnAccounts(emptySet(), emptyMap())
        )
        assertEquals(setOf(10L, 11L), TagMeaning.excludedFromSpending(ledger, meanings))
        assertEquals(setOf(10L, 12L), TagMeaning.taggedWith(ledger, meanings) { it == TagMeaning.TRIP })
    }
}
