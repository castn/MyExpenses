package org.totschnig.myexpenses.next.data.categories

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts

class CategoryRepositoryTest {

    private val today = LocalDate.of(2026, 10, 26)

    private fun ledger(vararg ids: Long) = Ledger(
        ids.map { LedgerTransaction(it, today, amount = -2000, accountId = 1, payeeId = 5, payeeName = "REWE") },
        OwnAccounts(emptySet(), emptyMap())
    )

    private class FakeWriting : CategoryWriting {
        val set = mutableListOf<Pair<List<Long>, Long>>()
        override suspend fun changeCategory(transactionId: Long, fromCategoryId: Long?, categoryId: Long) = Unit
        override suspend fun removeCategory(transactionId: Long, categoryId: Long) = Unit
        override suspend fun setMissingCategory(transactionIds: List<Long>, categoryId: Long) {
            set += transactionIds to categoryId
        }
    }

    @Test
    fun appliesCertainCategoriesOnlyToNewTransactionsWhileCollected() = withTestDataStore { dataStore ->
        runTest {
            val source = FakeLedgerSource(ledger = ledger(1), catalog = Catalog(listOf(CategoryInfo(10, "Food", "Food"))))
            val store = CategoryStore(dataStore)
            store.update { it.copy(rules = listOf(CategoryRule("r", setOf(5), 10))) }
            val writing = FakeWriting()
            val repository = CategoryRepository(source, store, writing, backgroundScope)
            repository.appliedAutomatically.launchIn(backgroundScope)
            // The transactions so far become the backlog
            assertEquals(2L, store.decisions.first { it.firstNewId != null }.firstNewId)
            source.ledger.value = ledger(1, 2)
            assertEquals(10L, store.decisions.first { 2L in it.automatic }.automatic[2])
            assertEquals(listOf(listOf(2L) to 10L), writing.set)
        }
    }
}
