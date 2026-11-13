package org.totschnig.myexpenses.next.data.cash

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore

class CashRepositoryTest {

    private class FakeWriting : CashWriting {
        val created = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        override suspend fun createAccount(balance: Long): Long {
            created += balance
            return 7
        }
        override suspend fun deleteIfEmpty(accountId: Long): Boolean {
            deleted += accountId
            return true
        }
    }

    @Test
    fun opensTheCashAccountAndTakesItBack() = withTestDataStore { dataStore ->
        runTest {
            val writing = FakeWriting()
            val repository = CashRepository(CashStore(dataStore), writing)
            val today = LocalDate.of(2026, 11, 13)
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
}
