package org.totschnig.myexpenses.next.feature.guidance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.guidance.DataGap
import org.totschnig.myexpenses.next.data.guidance.DataReadiness
import org.totschnig.myexpenses.next.data.guidance.HintStore
import org.totschnig.myexpenses.next.data.guidance.HintTopic
import org.totschnig.myexpenses.next.data.guidance.hintAfterSave
import org.totschnig.myexpenses.next.data.guidance.overviewHint
import org.totschnig.myexpenses.next.data.guidance.topic

/**
 * Hints that tell the user what makes the analyses useful, in the overview, in the details of a
 * transaction and after saving one, see [HintTopic]. At most one at a time in each place.
 *
 * @param readiness see [DataReadinessViewModel.readiness]
 */
class HintsViewModel(
    private val ledgerSource: LedgerSource,
    private val receiptFacts: Flow<ReceiptFacts>,
    readiness: Flow<DataReadiness?>,
    private val store: HintStore,
) : ViewModel() {

    /** Topics the user closed */
    val dismissed: StateFlow<Set<HintTopic>?> = store.dismissed.stateWhileShown(viewModelScope, null)

    /** The gap the overview tells about, null if none */
    val overviewHint: StateFlow<DataGap?> = combine(readiness, store.dismissed, ::overviewHint)
        .stateWhileShown(viewModelScope, null)

    /** Ids of the transactions with a scanned receipt, null while loading or when receipts cannot be scanned */
    val scanned: StateFlow<Set<Long>?> = receiptFacts.map { facts -> facts.items.keys.takeIf { facts.canScan } }
        .stateWhileShown(viewModelScope, null)

    /**
     * Tells [show] the hint for the transaction just saved, if there is one. Waits until the ledger
     * has read the change; nothing happens without consent to the analysis or for a transaction
     * outside of it.
     */
    fun afterSave(transactionId: Long, show: (HintTopic) -> Unit) {
        viewModelScope.launch {
            // The ledger shown before may not yet know the change: wait for the next one, which is
            // the same if it already did. An unchanged ledger is the same instance, see Memo.
            val before = ledgerSource.ledger.first() ?: return@launch
            val ledger = withTimeoutOrNull(WAIT_MILLIS) {
                ledgerSource.ledger.filterNotNull().first { it !== before }
            } ?: before
            val transaction = ledger.transactions.find { it.id == transactionId } ?: return@launch
            val receipts = receiptFacts.first()
            val hasReceipt = transactionId in receipts.items
            val topic = hintAfterSave(transaction, hasReceipt, store.hiddenAfterSave.first(), receipts.canScan)
                ?: return@launch
            store.shownAfterSave(topic)
            show(topic)
        }
    }

    fun dismiss(topic: HintTopic) {
        viewModelScope.launch { store.dismiss(topic) }
    }

    fun reset() {
        viewModelScope.launch { store.reset() }
    }

    private companion object {
        /** The ledger is read again a moment after the database changed */
        const val WAIT_MILLIS = 3000L
    }
}
