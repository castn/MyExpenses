package org.totschnig.myexpenses.next.data.cash

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts

class CashRepositoryTest {

    private val today = LocalDate.of(2026, 11, 13)

    private class FakeWriting : CashWriting {
        val created = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        val moved = mutableListOf<Pair<List<Long>, Long>>()
        override suspend fun createAccount(balance: Long): Long {
            created += balance
            return 7
        }
        override suspend fun deleteIfEmpty(accountId: Long): Boolean {
            deleted += accountId
            return true
        }
        override suspend fun moveToAccount(transactionIds: List<Long>, accountId: Long) {
            moved += transactionIds to accountId
        }
    }

    private fun withdrawal(
        id: Long,
        date: LocalDate,
        accountId: Long = 1,
        categoryId: Long? = null
    ) = LedgerTransaction(
        id,
        date,
        -5000,
        accountId = accountId,
        payeeName = "Sparkasse",
        methodLabel = "BARGELDAUSZAHLUNG",
        categoryId = categoryId
    )

    private fun ledger(vararg transactions: LedgerTransaction) =
        Ledger(transactions.toList(), OwnAccounts(emptySet(), emptyMap()))

    @Test
    fun opensTheCashAccountAndTakesItBack() = withTestDataStore { dataStore ->
        runTest {
            val writing = FakeWriting()
            val repository =
                CashRepository(CashStore(dataStore), writing, FakeLedgerSource(), backgroundScope)
            assertEquals(null, repository.account.first())
            val account = repository.open(4500, today)
            assertEquals(CashAccount(7, today), account)
            assertEquals(listOf(4500L), writing.created)
            assertEquals(account, repository.account.first())
            repository.takeBack(account)
            assertEquals(listOf(7L), writing.deleted)
            assertEquals(null, repository.account.first())
        }
    }

    @Test
    fun movesNewWithdrawalsToTheCashAccountOnce() = withTestDataStore { dataStore ->
        runTest {
            val writing = FakeWriting()
            val source = FakeLedgerSource(ledger = ledger(withdrawal(1, today)))
            val repository = CashRepository(CashStore(dataStore), writing, source, backgroundScope)
            // Without a cash account nothing moves
            assertEquals(emptyList<Long>(), repository.movedToAccount.first())
            repository.open(0, today)
            assertEquals(listOf(1L), repository.movedToAccount.first { it.isNotEmpty() })
            source.ledger.value = ledger(withdrawal(1, today), withdrawal(2, today))
            assertEquals(listOf(2L), repository.movedToAccount.first { it == listOf(2L) })
            assertEquals(listOf(listOf(1L) to 7L, listOf(2L) to 7L), writing.moved)
        }
    }

    @Test
    fun movesOnlyWithdrawalsSinceTheAccountWithoutCategory() {
        val account = CashAccount(7, today)
        val ledger = ledger(
            withdrawal(1, today.minusDays(1)),
            withdrawal(2, today),
            withdrawal(3, today, categoryId = 10),
            withdrawal(4, today, accountId = 7),
            LedgerTransaction(5, today, -5000, accountId = 1, methodLabel = "LASTSCHRIFT")
        )
        assertEquals(listOf(2L), withdrawalsToMove(ledger, account))
    }
}
