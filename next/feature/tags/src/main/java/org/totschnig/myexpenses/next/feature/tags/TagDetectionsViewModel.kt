package org.totschnig.myexpenses.next.feature.tags

import org.totschnig.myexpenses.next.core.common.ledger.Memo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.tags.Refund
import org.totschnig.myexpenses.next.data.tags.RefundDetector
import org.totschnig.myexpenses.next.data.tags.TagDatabase
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.data.tags.TagRepository
import org.totschnig.myexpenses.next.data.tags.TagRow
import org.totschnig.myexpenses.next.data.tags.TagStore
import org.totschnig.myexpenses.next.data.tags.Trip
import org.totschnig.myexpenses.next.data.tags.TripDetector
import org.totschnig.myexpenses.next.data.tags.TripInput
import org.totschnig.myexpenses.next.data.tags.addMeaningTag
import org.totschnig.myexpenses.next.data.tags.addTag

/**
 * Trips and refunds found in the transactions, to be tagged on the user's say, see [TripDetector]
 * and [RefundDetector]. Needs the consent to analyse transactions.
 */
class TagDetectionsViewModel(
    private val ledgerSource: LedgerSource,
    /** See [TagRepository.rows] */
    private val rows: Flow<List<TagRow>?>,
    private val contractFacts: Flow<ContractFacts?>,
    private val store: TagStore,
    private val tagDatabase: TagDatabase,
    /** Where the database is read and written */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** Transactions with a tag of [meaning] */
    private fun taggedAs(meaning: TagMeaning): Flow<Set<Long>> =
        combine(ledgerSource.ledger, store.meanings) { ledger, meanings ->
            ledger?.let { TagMeaning.taggedWith(it, meanings) { tagMeaning -> tagMeaning == meaning } }.orEmpty()
        }.distinctUntilChanged()

    /** Contract payments are no signs of a trip, e.g. a subscription paid in dollars */
    private val contractPayments: Flow<Set<Long>> = contractFacts.map { it?.paymentIds.orEmpty() }.distinctUntilChanged()

    private data class RefundSearch(val rows: List<TagRow>, val reimbursed: Set<Long>, val rejected: Set<String>)

    private val refundMemo = Memo<RefundSearch, Map<Long, Refund>>("tags: refunds") { (rows, reimbursed, rejected) ->
        RefundDetector.find(rows.map { it.refund }, reimbursed, rejected)
            .flatMap { listOf(it.debit.id to it, it.credit.id to it) }
            .toMap()
    }

    private data class TripSearch(val expenses: List<TripInput>, val tagged: Set<Long>, val rejected: Set<String>)

    private val tripMemo = Memo<TripSearch, List<Trip>>("tags: trips") { (expenses, tagged, rejected) ->
        TripDetector.find(expenses, tagged, rejected)
    }

    /** Suggested refunds by the ids of both their transactions */
    val refunds: StateFlow<Map<Long, Refund>> =
        combine(rows, taggedAs(TagMeaning.REIMBURSED), store.rejected(TagStore.Rejected.REFUNDS)) { rows, reimbursed, rejected ->
            rows ?: return@combine emptyMap()
            refundMemo.of(RefundSearch(rows, reimbursed, rejected))
        }
            .flowOn(Dispatchers.Default)
            .stateWhileShown(viewModelScope, emptyMap())

    /** Suggested trips, the latest first */
    val trips: StateFlow<List<Trip>> =
        combine(
            rows, contractPayments, taggedAs(TagMeaning.TRIP), store.rejected(TagStore.Rejected.TRIPS)
        ) { rows, contractPayments, tagged, rejected ->
            rows ?: return@combine emptyList()
            tripMemo.of(
                TripSearch(
                    rows.filter { it.refund.amount < 0 && it.refund.id !in contractPayments }.map { it.toTripInput() },
                    tagged,
                    rejected
                )
            )
        }
            .flowOn(Dispatchers.Default)
            .stateWhileShown(viewModelScope, emptyList())

    /** Tags [transactionIds] with a tag [name] that means [TagMeaning.TRIP] */
    fun acceptTrip(name: String, transactionIds: Collection<Long>) {
        viewModelScope.launch(io) {
            val tagId = tagDatabase.tagIdOf(name)
            store.setMeaning(tagId, TagMeaning.TRIP)
            tagDatabase.addTag(transactionIds, tagId)
        }
    }

    fun rejectTrip(trip: Trip) {
        viewModelScope.launch { store.reject(TagStore.Rejected.TRIPS, trip.key) }
    }

    /** Marks both transactions of [refund] as reimbursed */
    fun acceptRefund(refund: Refund, label: String) {
        viewModelScope.launch(io) {
            val tagIds = ledgerSource.catalog.first().tags.mapTo(HashSet()) { it.id }
            tagDatabase.addMeaningTag(store, tagIds, listOf(refund.debit.id, refund.credit.id), TagMeaning.REIMBURSED, label)
        }
    }

    fun rejectRefund(refund: Refund) {
        viewModelScope.launch { store.reject(TagStore.Rejected.REFUNDS, refund.key) }
    }
}
