package org.totschnig.myexpenses.next.data.tags

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts

class TagRepositoryTest {

    private val today = LocalDate.of(2026, 10, 26)

    private val ledger = Ledger(
        listOf(1L, 2L).map { id ->
            LedgerTransaction(id, today, amount = -2000, accountId = 1, payeeId = 5, payeeName = "Hotel Roma")
        },
        OwnAccounts(emptySet(), emptyMap())
    )

    private class FakeTagDatabase : TagDatabase {
        val tags = HashMap<Long, List<Long>>()
        override fun tagIdsOf(transactionId: Long) = tags[transactionId].orEmpty()
        override fun setTags(transactionId: Long, tagIds: List<Long>) {
            tags[transactionId] = tagIds
        }

        override fun tagIdOf(label: String) = error("Not used")
    }

    @Test
    fun appliesRulesToTransactionsAfterTheRuleWhileCollected() = withTestDataStore { dataStore ->
        runTest {
            val database = FakeTagDatabase()
            val store = TagStore(dataStore)
            val counterpart = tagRows(ledger).first().toTagInput().counterpart!!
            store.addRule(TagRule(counterpart, tagId = 7, afterId = 1))
            val repository = TagRepository(FakeLedgerSource(ledger = ledger), store, backgroundScope, database)
            assertNull(database.tags[2])
            assertEquals(mapOf(2L to setOf(7L)), repository.appliedRules.first { it.isNotEmpty() })
            assertEquals(listOf(7L), database.tags[2])
            assertNull(database.tags[1])
        }
    }
}
