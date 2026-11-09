package org.totschnig.myexpenses.next.feature.tags

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Catalog
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.data.tags.TagRow
import org.totschnig.myexpenses.next.data.tags.TagStore
import org.totschnig.myexpenses.next.data.tags.tagRows

@OptIn(ExperimentalCoroutinesApi::class)
class TagDetectionsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val august: LocalDate = LocalDate.of(2026, 8, 1)

    private fun expense(
        id: Long,
        day: Long,
        payeeName: String,
        amount: Long = -2000,
        originalCurrency: String? = null
    ) = LedgerTransaction(
        id,
        august.plusDays(day),
        amount,
        accountId = 1,
        currency = "EUR",
        originalCurrency = originalCurrency,
        payeeName = payeeName
    )

    /** A hotel and a payment in francs on the next day make a trip, Zalando paid back a parcel */
    private val transactions = listOf(
        expense(1, 0, "Hotel Adler"),
        expense(2, 1, "Trattoria Ticino", originalCurrency = "CHF"),
        expense(3, 1, "Bäckerei"),
        expense(10, 20, "Zalando SE", amount = -4999),
        expense(11, 30, "Zalando SE", amount = 4999)
    )

    private fun ledgerOf(transactions: List<LedgerTransaction>) =
        Ledger(transactions, OwnAccounts(emptySet(), emptyMap()))

    private class Fixture(
        val viewModel: TagDetectionsViewModel,
        val store: TagStore,
        val database: FakeTagDatabase,
        val source: FakeLedgerSource,
        val rows: MutableStateFlow<List<TagRow>?>,
        val contractFacts: MutableStateFlow<ContractFacts?>
    ) {
        /** Like the ledger read again after the database changed */
        fun setLedger(ledger: Ledger) {
            source.ledger.value = ledger
            rows.value = tagRows(ledger)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withViewModel(catalog: Catalog = Catalog(), block: suspend Fixture.() -> Unit) =
        withTestDataStore { dataStore ->
            runTest(dispatcher) {
                val ledger = ledgerOf(transactions)
                val source = FakeLedgerSource(ledger = ledger, catalog = catalog)
                val rows = MutableStateFlow<List<TagRow>?>(tagRows(ledger))
                val contractFacts = MutableStateFlow<ContractFacts?>(null)
                val store = TagStore(dataStore)
                val database = FakeTagDatabase()
                val viewModel = TagDetectionsViewModel(
                    source,
                    rows,
                    contractFacts,
                    store,
                    database,
                    io = dispatcher,
                    compute = dispatcher
                )
                // Like a shown screen
                backgroundScope.launch { viewModel.trips.collect {} }
                backgroundScope.launch { viewModel.refunds.collect {} }
                try {
                    Fixture(viewModel, store, database, source, rows, contractFacts).block()
                } finally {
                    // Its coroutines must not outlive the test dispatcher set as Main
                    viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
                }
            }
        }

    /** The transactions with ids [ids] get [tagId] */
    private fun tagged(ids: Collection<Long>, tagId: Long) =
        ledgerOf(transactions.map { if (it.id in ids) it.copy(tagIds = setOf(tagId)) else it })

    @Test
    fun suggestsTripAndRefund() = withViewModel {
        val trip = viewModel.trips.first { it.isNotEmpty() }.single()
        assertEquals(august, trip.start)
        assertEquals(august.plusDays(1), trip.end)
        assertEquals(listOf(1L, 2L, 3L), trip.transactions.map { it.id })
        assertEquals(listOf(1L, 2L), trip.signals.map { it.id })
        val refunds = viewModel.refunds.first { it.isNotEmpty() }
        assertEquals(setOf(10L, 11L), refunds.keys)
        assertEquals(10L, refunds.getValue(11).debit.id)
        assertEquals(refunds.getValue(10), refunds.getValue(11))
    }

    @Test
    fun suggestsNothingWhileRowsAreLoading() = withViewModel {
        viewModel.trips.first { it.isNotEmpty() }
        viewModel.refunds.first { it.isNotEmpty() }
        rows.value = null
        viewModel.trips.first { it.isEmpty() }
        viewModel.refunds.first { it.isEmpty() }
    }

    @Test
    fun contractPaymentsAreNoSignOfATrip() = withViewModel {
        viewModel.trips.first { it.isNotEmpty() }
        contractFacts.value = ContractFacts(
            active = emptyList(),
            confirmed = emptyList(),
            salaries = emptyList(),
            paymentIds = setOf(2),
            transactions = emptyList()
        )
        // Only the hotel is left as a sign, a single day is no trip
        viewModel.trips.first { it.isEmpty() }
    }

    @Test
    fun acceptedTripTagsAllItsTransactionsAndIsNotSuggestedAgain() = withViewModel {
        val trip = viewModel.trips.first { it.isNotEmpty() }.single()
        viewModel.acceptTrip("Tessin", trip.transactions.map { it.id })
        database.writes.first { it == 3 }
        val tagId = database.labels.getValue("Tessin")
        assertEquals(
            mapOf(1L to listOf(tagId), 2L to listOf(tagId), 3L to listOf(tagId)),
            database.tags
        )
        assertEquals(mapOf(tagId to TagMeaning.TRIP), store.meanings.first())
        setLedger(tagged(listOf(1, 2, 3), tagId))
        viewModel.trips.first { it.isEmpty() }
    }

    @Test
    fun rejectedTripIsNotSuggestedAgain() = withViewModel {
        val trip = viewModel.trips.first { it.isNotEmpty() }.single()
        viewModel.rejectTrip(trip)
        viewModel.trips.first { it.isEmpty() }
        assertEquals(setOf(trip.key), store.rejected(TagStore.Rejected.TRIPS).first())
        assertEquals(0, database.writes.value)
    }

    @Test
    fun acceptedRefundGetsTheTagThatMeansReimbursed() =
        withViewModel(Catalog(tags = listOf(TagInfo(7, "Erstattet")))) {
            store.setMeaning(7, TagMeaning.REIMBURSED)
            val refund = viewModel.refunds.first { it.isNotEmpty() }.getValue(10)
            viewModel.acceptRefund(refund, "Zurückgezahlt")
            database.writes.first { it == 2 }
            assertEquals(mapOf(10L to listOf(7L), 11L to listOf(7L)), database.tags)
            assertEquals(emptyMap<String, Long>(), database.labels)
            setLedger(tagged(listOf(10, 11), 7))
            viewModel.refunds.first { it.isEmpty() }
        }

    @Test
    fun acceptedRefundCreatesTagWhenNoneMeansReimbursed() = withViewModel {
        val refund = viewModel.refunds.first { it.isNotEmpty() }.getValue(11)
        viewModel.acceptRefund(refund, "Erstattet")
        database.writes.first { it == 2 }
        val tagId = database.labels.getValue("Erstattet")
        assertEquals(mapOf(10L to listOf(tagId), 11L to listOf(tagId)), database.tags)
        assertEquals(mapOf(tagId to TagMeaning.REIMBURSED), store.meanings.first())
    }

    @Test
    fun rejectedRefundIsNotSuggestedAgain() = withViewModel {
        val refund = viewModel.refunds.first { it.isNotEmpty() }.getValue(10)
        viewModel.rejectRefund(refund)
        viewModel.refunds.first { it.isEmpty() }
        assertEquals(setOf(refund.key), store.rejected(TagStore.Rejected.REFUNDS).first())
        // The trip is not touched
        assertEquals(1, viewModel.trips.first { it.isNotEmpty() }.size)
    }
}
