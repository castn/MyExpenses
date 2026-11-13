package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.totschnig.myexpenses.next.data.cash.CashAccount
import org.totschnig.myexpenses.next.data.cash.CashRepository
import org.totschnig.myexpenses.next.data.cash.CashStore
import org.totschnig.myexpenses.next.data.cash.CashWriting
import org.totschnig.myexpenses.next.data.categories.CategoryRepository
import org.totschnig.myexpenses.next.data.categories.CategoryRule
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.data.categories.CategoryWriting
import org.totschnig.myexpenses.next.data.categories.cashRuleCategory

class SetupRepositoryTest {

    private val today = LocalDate.of(2026, 11, 12)

    private val ledger = Ledger(
        listOf(
            LedgerTransaction(1, today, -800, accountId = 1, payeeId = 1, payeeName = "Vermieter"),
            LedgerTransaction(2, today, -200, accountId = 1, payeeId = 2, payeeName = "REWE")
        ),
        OwnAccounts(emptySet(), emptyMap())
    )

    private class FakeWriting : CategoryWriting {
        val set = mutableListOf<Pair<List<Long>, Long>>()
        override suspend fun changeCategory(
            transactionId: Long,
            fromCategoryId: Long?,
            categoryId: Long
        ) = Unit
        val removed = mutableListOf<Pair<Long, Long>>()
        override suspend fun removeCategory(transactionId: Long, categoryId: Long) {
            removed += transactionId to categoryId
        }
        override suspend fun setMissingCategory(transactionIds: List<Long>, categoryId: Long) {
            set += transactionIds to categoryId
        }
    }

    @Test
    fun queuesPayeesAndWritesTheDecisions() = withTestDataStore { dataStore ->
        runTest {
            val catalog = Catalog(listOf(CategoryInfo(10, "Wohnen", "Wohnen")))
            val source = FakeLedgerSource(ledger = ledger, catalog = catalog, today = today)
            val writing = FakeWriting()
            val categories =
                CategoryRepository(source, CategoryStore(dataStore), writing, backgroundScope)
            val store = SetupStore(dataStore)
            val repository = SetupRepository(source, categories, store, backgroundScope)
            val queue = repository.categoryQueue.first { it != null }!!
            assertEquals(listOf("Vermieter"), queue.required.map { it.counterpart.name })
            repository.categorize(queue.required.single(), 10, always = true)
            assertEquals(listOf(listOf(1L) to 10L), writing.set)
            // The rule makes the next rent certain
            assertEquals(1, repository.automaticShare.first { it?.automatic == 1 }!!.automatic)
            repository.markUnclear(queue.optional.single())
            val unclear = repository.categoryQueue.first { it?.coverage?.unclear == 200L }!!
            assertEquals(emptyList<SetupPayee>(), unclear.optional)
        }
    }

    @Test
    fun takesBackDecisions() = withTestDataStore { dataStore ->
        runTest {
            val source = FakeLedgerSource(ledger = ledger, today = today)
            val categoryStore = CategoryStore(dataStore)
            val writing = FakeWriting()
            val categories = CategoryRepository(source, categoryStore, writing, backgroundScope)
            val store = SetupStore(dataStore)
            val repository = SetupRepository(source, categories, store, backgroundScope)
            val queue = repository.categoryQueue.first { it != null }!!
            val rent = repository.categorize(queue.required.single(), 10, always = true)
            assertEquals("p:1", rent.key)
            repository.takeBack(rent)
            assertEquals(listOf(1L to 10L), writing.removed)
            assertEquals(emptyList<CategoryRule>(), categoryStore.decisions.first().rules)
            val unclear = repository.markUnclear(queue.optional.single())
            repository.takeBack(unclear)
            assertEquals(emptySet<String>(), categoryStore.decisions.first().unclear)
        }
    }

    @Test
    fun assignsSomeDebitsWithoutRule() = withTestDataStore { dataStore ->
        runTest {
            val source = FakeLedgerSource(ledger = ledger, today = today)
            val categoryStore = CategoryStore(dataStore)
            val writing = FakeWriting()
            val categories = CategoryRepository(source, categoryStore, writing, backgroundScope)
            val store = SetupStore(dataStore)
            val repository = SetupRepository(source, categories, store, backgroundScope)
            val queue = repository.categoryQueue.first { it != null }!!
            val some = repository.assign(queue.required.single(), listOf(1L), 10)
            assertEquals(listOf(listOf(1L) to 10L), writing.set)
            assertEquals(emptyList<CategoryRule>(), categoryStore.decisions.first().rules)
            repository.takeBack(some)
            assertEquals(listOf(1L to 10L), writing.removed)
        }
    }

    private class FakeCashWriting : CashWriting {
        val deleted = mutableListOf<Long>()
        override suspend fun createAccount(balance: Long) = 42L
        override suspend fun moveToAccount(transactionIds: List<Long>, accountId: Long) = Unit
        override suspend fun deleteIfEmpty(accountId: Long): Boolean {
            deleted += accountId
            return true
        }
    }

    @Test
    fun keepsCashAsSpendingOrInACashAccount() = withTestDataStore { dataStore ->
        runTest {
            val source = FakeLedgerSource(ledger = ledger, today = today)
            val categoryStore = CategoryStore(dataStore)
            val writing = FakeWriting()
            val categories = CategoryRepository(source, categoryStore, writing, backgroundScope)
            val cashWriting = FakeCashWriting()
            val cashAccounts =
                CashRepository(CashStore(dataStore), cashWriting, source, backgroundScope)
            val repository = SetupRepository(
                source,
                categories,
                SetupStore(dataStore),
                backgroundScope,
                cash = cashAccounts
            )
            val cash = SetupCash(300, listOf(5, 6), today, today)
            assertEquals(true, repository.canOpenCashAccount.first())
            // As spending: later withdrawals get the category by rule
            val spending = repository.keepCash(cash, 30, balance = null)
            assertEquals(null, spending.account)
            assertEquals(30L, categoryStore.decisions.first().cashRuleCategory)
            repository.takeBack(spending)
            assertEquals(null, categoryStore.decisions.first().cashRuleCategory)
            // In a cash account: no rule, the account is created and deleted again
            val account = repository.keepCash(cash, 30, balance = 4500)
            assertEquals(CashAccount(42, today), account.account)
            assertEquals(null, categoryStore.decisions.first().cashRuleCategory)
            assertEquals(false, repository.canOpenCashAccount.first())
            // With the account, withdrawals from before get no rule, later ones go to the account
            repository.takeBack(repository.keepCash(cash, 31, balance = null))
            assertEquals(null, categoryStore.decisions.first().cashRuleCategory)
            repository.takeBack(account)
            assertEquals(listOf(42L), cashWriting.deleted)
            assertEquals(true, repository.canOpenCashAccount.first())
            assertEquals(
                listOf(listOf(5L, 6L) to 30L, listOf(5L, 6L) to 30L, listOf(5L, 6L) to 31L),
                writing.set
            )
        }
    }

    @Test
    fun appliesTheBacklogOnlyIfTheUserAgrees() = withTestDataStore { dataStore ->
        runTest {
            val catalog = Catalog(listOf(CategoryInfo(10, "Wohnen", "Wohnen")))
            val source = FakeLedgerSource(ledger = ledger, catalog = catalog, today = today)
            val categoryStore = CategoryStore(dataStore)
            // The rent was there before the suggestions started, and a rule is certain
            categoryStore.update {
                it.copy(firstNewId = 100, rules = listOf(CategoryRule("r", setOf(1), 10)))
            }
            val writing = FakeWriting()
            val categories = CategoryRepository(source, categoryStore, writing, backgroundScope)
            val store = SetupStore(dataStore)
            val repository = SetupRepository(source, categories, store, backgroundScope)
            assertEquals(1, repository.backlog.first { it > 0 })
            repository.answerBacklog(accept = true)
            assertEquals(listOf(listOf(1L) to 10L), writing.set)
            assertEquals(0, repository.backlog.first { it == 0 })
            assertEquals(true, categoryStore.decisions.first().backlogAnswered)
        }
    }

    @Test
    fun offersTheContractsAndIncomeStepsOnlyWithContracts() = withTestDataStore { dataStore ->
        runTest {
            val source = FakeLedgerSource()
            val categories =
                CategoryRepository(source, CategoryStore(dataStore), FakeWriting(), backgroundScope)
            val store = SetupStore(dataStore)
            val without = SetupRepository(source, categories, store, backgroundScope)
            assertEquals(listOf(SetupStep.CATEGORIES), without.steps.first())
            val contracts = flowOf(SetupContracts.Available(suggestions = 3))
            val with = SetupRepository(source, categories, store, backgroundScope, contracts)
            assertEquals(listOf(SetupStep.CATEGORIES, SetupStep.CONTRACTS), with.steps.first())
            // The income step comes first, once incomes were found
            val incomes = flowOf(SetupContracts.Available(suggestions = 3, incomeSuggestions = 1))
            val all = SetupRepository(source, categories, store, backgroundScope, incomes)
            assertEquals(SetupStep.entries, all.steps.first())
        }
    }
}
