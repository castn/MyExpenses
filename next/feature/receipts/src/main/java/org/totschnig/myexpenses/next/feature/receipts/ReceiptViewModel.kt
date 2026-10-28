package org.totschnig.myexpenses.next.feature.receipts

import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.common.measured
import androidx.lifecycle.ViewModel
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import timber.log.Timber
import java.time.LocalDate
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup
import org.totschnig.myexpenses.next.data.receipts.ItemGroupClassifier
import org.totschnig.myexpenses.next.data.receipts.LearnedItemGroup
import org.totschnig.myexpenses.next.data.receipts.ParsedReceipt
import org.totschnig.myexpenses.next.data.receipts.Receipt
import org.totschnig.myexpenses.next.data.receipts.ReceiptDatabase
import org.totschnig.myexpenses.next.data.receipts.ReceiptDraft
import org.totschnig.myexpenses.next.data.receipts.ReceiptItem
import org.totschnig.myexpenses.next.data.receipts.ReceiptMatcher
import org.totschnig.myexpenses.next.data.receipts.ReceiptParser
import org.totschnig.myexpenses.next.data.receipts.ReceiptTarget
import org.totschnig.myexpenses.next.data.receipts.withSuggestedGroups

sealed interface ReceiptScanState {
    val target: ReceiptTarget

    data class Recognizing(override val target: ReceiptTarget) : ReceiptScanState

    data class Failed(override val target: ReceiptTarget, val reason: Reason) : ReceiptScanState

    enum class Reason {
        /** Text recognition is not installed or happens in an external app */
        NoRecognition,
        /** Recognition or saving went wrong */
        Error,
        /** No item found on the photo */
        NoItems
    }
}

/**
 * The photo of a receipt, reduced for showing excerpts of it
 *
 * @param scale from positions of recognition to pixels of [bitmap]
 */
class ReceiptPhoto(val uri: Uri, val bitmap: Bitmap, val scale: Float)

/**
 * Adds a scanned receipt to a transaction: the photo is taken with the document scanner of ML Kit
 * (or picked from the gallery without it), read by the configured text recognition and
 * [ReceiptParser], checked and corrected as [ReceiptDraft] and saved together with the photo
 * as attachment. Saved receipts are corrected the same way.
 *
 * A receipt scanned on its own is first assigned to a transaction the [ReceiptMatcher] suggests,
 * or waits for its transaction to be booked.
 *
 * The receipt being checked or assigned is kept in [savedState], see [ReceiptSavedState].
 */
class ReceiptViewModel(
    private val savedState: SavedStateHandle,
    private val database: ReceiptDatabase,
    /** The home currency, which the user can change */
    val homeCurrency: StateFlow<CurrencyInfo>,
    /** See [ReceiptPhotoLoader] */
    private val loadPhoto: (Uri) -> ReceiptPhoto?,
    /** Where the database and the files are read */
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** The text recognition, null while its module is not installed */
    private val textRecognitionProvider: () -> TextRecognition?,
) : ViewModel() {

    private val textRecognition: TextRecognition? get() = textRecognitionProvider()

    private val _scan = MutableStateFlow<ReceiptScanState?>(null)

    /** Reading a photo, shown in the details of the transaction */
    val scan: StateFlow<ReceiptScanState?> = _scan.asStateFlow()

    private val _draft = MutableStateFlow<ReceiptDraft?>(null)

    /** The receipt being checked, at most one at a time */
    val draft: StateFlow<ReceiptDraft?> = _draft.asStateFlow()

    private val _photo = MutableStateFlow<ReceiptPhoto?>(null)

    /** Photo of [draft], once it is loaded */
    val photo: StateFlow<ReceiptPhoto?> = _photo.asStateFlow()

    private val _recognizingAgain = MutableStateFlow(false)

    /** The photo of [draft] is read again */
    val recognizingAgain: StateFlow<Boolean> = _recognizingAgain.asStateFlow()

    private val _recognizeAgainFailed = MutableStateFlow<ReceiptScanState.Reason?>(null)

    /** Why reading the photo of [draft] again gave nothing, until the user closes the notice */
    val recognizeAgainFailed: StateFlow<ReceiptScanState.Reason?> = _recognizeAgainFailed.asStateFlow()

    /** Receipts can be read, so scanning one on its own makes sense */
    val canRecognize: Boolean
        get() = textRecognition != null

    /** Payee of the transaction of [draft], kept with it when Android ends the process */
    private var draftPayee: String? = null

    /** Receipts scanned on their own or waiting, whose transaction the user chooses */
    val assignments = ReceiptAssignments(
        database, homeCurrency, viewModelScope, io,
        openDraft = { draft, payee -> open(draft.classified(payee)) },
        onFailed = { target -> _scan.value = ReceiptScanState.Failed(target, ReceiptScanState.Reason.Error) }
    )

    init {
        savedState.get<Bundle>(KEY_SAVED_STATE)?.let { restore(ReceiptSavedState.of(it)) }
        savedState.setSavedStateProvider(KEY_SAVED_STATE) {
            ReceiptSavedState(_draft.value, draftPayee, assignments.standaloneCurrency, assignments.assignment.value).toBundle()
        }
    }

    private fun restore(state: ReceiptSavedState) {
        draftPayee = state.draftPayee
        state.draft?.let(::open)
        assignments.restore(state.assignment, state.standaloneCurrency)
    }

    /** @param payee of the transaction, tells the kind of shop when the receipt does not show it */
    fun receipt(transactionId: Long, payee: String?): Flow<Receipt?> = database.changesOf(transactionId).map {
        withContext(io) {
            measured("receipts: load $transactionId") {
                database.load(transactionId)?.let { classifiedOnce(transactionId, it, payee) }
            }
        }
    }.logTimings("receipts: receipt $transactionId")

    /**
     * Receipts saved before there were groups get them once. Items no source knows stay without
     * group, so saving happens only when something was found, and the saved receipt is stable.
     */
    private fun classifiedOnce(transactionId: Long, receipt: Receipt, payee: String?): Receipt {
        if (receipt.items.none { it.group == null && !it.groupByUser && it.amount > 0 }) return receipt
        val items = receipt.items.withSuggestedGroups(classifier().classify(receipt.items, shopHint(receipt.merchant, payee)))
        if (items == receipt.items) return receipt
        return receipt.copy(items = items).also { database.save(transactionId, it) }
    }

    private fun shopHint(merchant: String?, payee: String?) =
        listOfNotNull(merchant, payee).joinToString(" ").ifEmpty { null }

    /** null if there is no scanner, then the photo is picked from the gallery */
    suspend fun documentScanIntent(activity: Activity): IntentSender? = try {
        textRecognition?.documentScanIntent(activity)
    } catch (e: Exception) {
        // E.g. without Google Play services
        Timber.w(e, "Document scanner not available")
        null
    }

    fun documentScanResult(data: Intent?) = textRecognition?.documentScanResult(data)

    /** The photo of a saved receipt as an uri other apps can open */
    fun viewableUri(photo: Uri) = database.viewableUri(photo)

    /** The classifier of the last learned groups, kept as long as they do not change */
    @Volatile
    private var classifierCache: Pair<List<LearnedItemGroup>, ItemGroupClassifier>? = null

    /** Learns from all groups the user chose so far */
    private fun classifier(): ItemGroupClassifier {
        val learned = database.loadLearnedItemGroups().mapNotNull { (name, id) ->
            ItemGroup.ofId(id)?.let { LearnedItemGroup(name, it) }
        }
        classifierCache?.takeIf { it.first == learned }?.let { return it.second }
        return ItemGroupClassifier(learned).also { classifierCache = learned to it }
    }


    /**
     * Groups for the items without one. The merchant printed on the receipt and the payee of the
     * transaction both may tell the kind of shop.
     */
    private fun ReceiptDraft.classified(payee: String?) =
        withSuggestedGroups(classifier().classify(items, shopHint(merchant, payee)))

    /**
     * @param transactionAmount absolute amount of the transaction in minor units
     * @param payee of the transaction, tells the kind of shop when the photo does not show it
     */
    fun recognize(transactionId: Long, transactionAmount: Long, payee: String?, photo: Uri) {
        draftPayee = payee
        val target = ReceiptTarget.Transaction(transactionId)
        recognize(target, photo) { receipt ->
            open(ReceiptDraft.recognized(target, transactionAmount, receipt, photo).classified(payee))
        }
    }

    /** Reads a receipt scanned on its own and suggests the transactions it may belong to */
    fun recognizeUnassigned(photo: Uri) {
        recognize(ReceiptTarget.Unassigned, photo) { receipt ->
            val stored = receipt.toReceipt().copy(photo = photo)
            assignments.showScan(stored, receipt.itemCount, receipt.leftoverRows)
        }
    }

    private fun recognize(target: ReceiptTarget, photo: Uri, onRecognized: (ParsedReceipt) -> Unit) {
        _scan.value = ReceiptScanState.Recognizing(target)
        viewModelScope.launch(io) {
            _scan.value = try {
                val text = textRecognition?.recognize(photo)
                if (text == null) {
                    ReceiptScanState.Failed(target, ReceiptScanState.Reason.NoRecognition)
                } else {
                    val receipt = ReceiptParser.parse(text)
                    if (receipt.items.isEmpty()) {
                        ReceiptScanState.Failed(target, ReceiptScanState.Reason.NoItems)
                    } else {
                        onRecognized(receipt)
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
                ReceiptScanState.Failed(target, ReceiptScanState.Reason.Error)
            }
        }
    }

    fun dismissScan() {
        _scan.value = null
    }

    /** Opens the saved receipt for corrections; receipts saved before there were groups get them now */
    fun edit(transactionId: Long, transactionAmount: Long, payee: String?, receipt: Receipt) {
        val draft = ReceiptDraft.saved(ReceiptTarget.Transaction(transactionId), transactionAmount, receipt)
        draftPayee = payee
        open(draft)
        if (draft.itemsWithoutGroup.isNotEmpty()) {
            viewModelScope.launch(io) {
                val classified = draft.classified(payee)
                _draft.update { if (it == draft) classified else it }
            }
        }
    }

    private fun open(draft: ReceiptDraft) {
        _draft.value = draft
        if (_photo.value?.uri != draft.photo) {
            _photo.value = null
            draft.photo?.let { uri ->
                viewModelScope.launch(io) {
                    loadPhoto(uri)?.let { photo -> if (_draft.value?.photo == uri) _photo.value = photo }
                }
            }
        }
    }

    /**
     * Reads the photo of the [draft] again, e.g. after the parser was improved or for the rows not
     * taken of a saved receipt; replaces what was read and corrected so far
     */
    fun recognizeAgain() {
        val draft = _draft.value ?: return
        val photo = draft.photo ?: return
        _recognizingAgain.value = true
        _recognizeAgainFailed.value = null
        viewModelScope.launch(io) {
            try {
                val text = textRecognition?.recognize(photo)
                val parsed = text?.let(ReceiptParser::parse)
                when {
                    text == null -> _recognizeAgainFailed.value = ReceiptScanState.Reason.NoRecognition
                    parsed == null || parsed.items.isEmpty() -> _recognizeAgainFailed.value = ReceiptScanState.Reason.NoItems
                    else -> {
                        val again = ReceiptDraft.recognized(draft.target, draft.transactionAmount, parsed, photo)
                            .copy(isNewPhoto = draft.isNewPhoto, isStandalone = draft.isStandalone, isChanged = true)
                            .classified(null)
                        _draft.update { if (it?.photo == photo) again else it }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
                _recognizeAgainFailed.value = ReceiptScanState.Reason.Error
            } finally {
                _recognizingAgain.value = false
            }
        }
    }

    fun dismissRecognizeAgainFailed() {
        _recognizeAgainFailed.value = null
    }

    fun setMerchant(merchant: String?) = _draft.update { it?.withDetails(merchant = merchant) }

    fun setDate(date: LocalDate?) = _draft.update { it?.withDetails(date = date) }

    fun setTotal(total: Long?) = _draft.update { it?.withDetails(total = total) }

    fun setCardAmount(cardAmount: Long?) = _draft.update { it?.withDetails(cardAmount = cardAmount) }

    fun updateItem(index: Int, item: ReceiptItem) = _draft.update { it?.update(index, item) }

    fun removeItem(index: Int) = _draft.update { it?.remove(index) }

    fun insertItem(index: Int, item: ReceiptItem) = _draft.update { it?.insert(index, item) }

    fun setGroup(index: Int, group: ItemGroup) = _draft.update { it?.setGroup(index, group) }

    /** Deletes the items, the photo stays attached to the transaction */
    fun deleteReceipt(transactionId: Long) {
        viewModelScope.launch(io) { database.delete(transactionId) }
    }

    fun closeDraft() {
        _recognizeAgainFailed.value = null
        _draft.value = null
    }

    /**
     * Keeps a copy of a new photo, the scanner only leaves it in the cache: as attachment of the
     * transaction, or with the receipt while it waits for its transaction
     */
    fun saveDraft() {
        val draft = _draft.value ?: return
        _draft.value = null
        viewModelScope.launch(io) {
            try {
                val shot = draft.photo
                val photo = if (draft.isNewPhoto && shot != null) {
                    database.keepPhoto(shot, (draft.target as? ReceiptTarget.Transaction)?.id)
                } else shot
                val receipt = draft.receipt.copy(photo = photo)
                when (val target = draft.target) {
                    is ReceiptTarget.Transaction -> database.save(target.id, receipt)
                    is ReceiptTarget.Waiting -> database.saveWaiting(target.id, receipt)
                    ReceiptTarget.Unassigned -> error("Draft without target")
                }
            } catch (e: Exception) {
                Timber.e(e)
                val target = if (draft.isStandalone) ReceiptTarget.Unassigned else draft.target
                _scan.value = ReceiptScanState.Failed(target, ReceiptScanState.Reason.Error)
            }
        }
    }

    companion object {
        private const val KEY_SAVED_STATE = "receipt"
    }
}
