package org.totschnig.myexpenses.next.data.categories

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        ids.map {
            LedgerTransaction(
                it,
                today,
                amount = -2000,
                accountId = 1,
                payeeId = 5,
                payeeName = "REWE"
            )
        },
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
    fun appliesCertainCategoriesOnlyToNewTransactionsWhileCollected() =
        withTestDataStore { dataStore ->
            runTest {
                val source =
                    FakeLedgerSource(
                        ledger = ledger(1),
                        catalog = Catalog(listOf(CategoryInfo(10, "Food", "Food")))
                    )
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

    @Test
    fun unclearCounterpartIsNotAppliedOrSuggested() = withTestDataStore { dataStore ->
        runTest {
            val catalog = Catalog(listOf(CategoryInfo(10, "Food", "Food")))
            val source = FakeLedgerSource(ledger = ledger(1, 2), catalog = catalog)
            val store = CategoryStore(dataStore)
            val rule = CategoryRule("r", setOf(5), 10)
            store.update { it.copy(firstNewId = 2, rules = listOf(rule)) }
            val repository = CategoryRepository(source, store, FakeWriting(), backgroundScope)
            val rewe = source.ledger.value!!.transactions.first().categoryInput()
            assertEquals(false, repository.isUnclear(rewe))
            repository.markUnclear(rewe.counterpart()!!)
            assertEquals(setOf(1L, 2L), repository.unclear.first { it.isNotEmpty() })
            assertEquals(true, repository.isUnclear(rewe))
            val analysis = repository.analysis.first { it?.unclear?.isNotEmpty() == true }!!
            assertEquals(setOf(1L, 2L), analysis.unclear)
            assertTrue(analysis.plan.apply.isEmpty() && analysis.plan.backlog.isEmpty())
            assertTrue(analysis.plan.suggestions.isEmpty())
            repository.unmarkUnclear(rewe.counterpart()!!)
            assertEquals(emptySet<Long>(), repository.unclear.first { it.isEmpty() })
        }
    }

    @Test
    fun categorizesACounterpartWithOrWithoutRule() = withTestDataStore { dataStore ->
        runTest {
            val store = CategoryStore(dataStore)
            val writing = FakeWriting()
            val repository = CategoryRepository(FakeLedgerSource(), store, writing, backgroundScope)
            val rewe = Counterpart("p:5", "REWE", payeeId = 5)
            repository.markUnclear(rewe)
            repository.categorize(rewe, listOf(1, 2), 10, always = false)
            assertEquals(listOf(listOf(1L, 2L) to 10L), writing.set)
            assertEquals(CategoryDecisions(), store.decisions.first())
            repository.categorize(rewe, listOf(3), 11, always = true)
            val rule = store.decisions.first().rules.single { it.categoryId == 11L }
            assertEquals(setOf(5L), rule.payeeIds)
            // A payee without id cannot be named by a rule
            val byName = Counterpart("n:kiosk", "Kiosk")
            repository.categorize(byName, listOf(4), 12, always = true)
            assertEquals(1, store.decisions.first().rules.size)
        }
    }

    @Test
    fun takesBackACategorizationAndRestoresTheRuleBefore() = withTestDataStore { dataStore ->
        runTest {
            val store = CategoryStore(dataStore)
            val before = CategoryRule("r", setOf(5), 9)
            store.update { it.copy(rules = listOf(before)) }
            val writing = FakeWriting()
            val repository = CategoryRepository(FakeLedgerSource(), store, writing, backgroundScope)
            val rewe = Counterpart("p:5", "REWE", payeeId = 5)
            val withRule = repository.categorize(rewe, listOf(1, 2), 10, always = true)
            val expected = Categorization(rewe, listOf(1, 2), 10, hasSetRule = true, ruleBefore = 9)
            assertEquals(expected, withRule)
            repository.takeBack(withRule)
            assertEquals(listOf(1L to 10L, 2L to 10L), writing.removed)
            assertEquals(9L, store.decisions.first().ruleCategory(rewe))
            // Without a rule before, the counterpart is in none afterwards
            repository.takeBack(withRule.copy(ruleBefore = null))
            assertTrue(store.decisions.first().rules.isEmpty())
            // Without "Immer so machen" the rules stay
            store.update { it.copy(rules = listOf(before)) }
            repository.takeBack(repository.categorize(rewe, listOf(3), 10, always = false))
            assertEquals(listOf(before), store.decisions.first().rules)
        }
    }

    @Test
    fun suggestsForATransactionThatIsTyped() = withTestDataStore { dataStore ->
        runTest {
            val source = FakeLedgerSource(
                ledger = Ledger(
                    listOf(
                        LedgerTransaction(
                            1,
                            today,
                            amount = -2000,
                            accountId = 1,
                            payeeId = 5,
                            payeeName = "REWE",
                            categoryId = 10
                        )
                    ),
                    OwnAccounts(emptySet(), emptyMap())
                ),
                catalog = Catalog(listOf(CategoryInfo(10, "Food", "Food")))
            )
            val repository =
                CategoryRepository(source, CategoryStore(dataStore), FakeWriting(), backgroundScope)
            val analysis = repository.analysis.first { it != null }!!
            val typed = CategoryInput(id = 0, amount = -1, payeeId = 5, payeeName = "REWE")
            assertEquals(10L, analysis.suggestFor(typed)?.categoryId)
            assertEquals("Food", analysis.paths[10])
            // Learned per direction, an income at the shop is no purchase
            assertEquals(null, analysis.suggestFor(typed.copy(amount = 1)))
        }
    }
}
