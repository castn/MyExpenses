package org.totschnig.myexpenses.next.feature.receipts

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.receipts.BookedTransaction
import org.totschnig.myexpenses.next.data.receipts.LeftoverRow
import org.totschnig.myexpenses.next.data.receipts.Receipt
import org.totschnig.myexpenses.next.data.receipts.ReceiptDatabase
import org.totschnig.myexpenses.next.data.receipts.ReceiptDraft
import org.totschnig.myexpenses.next.data.receipts.ReceiptHeader
import org.totschnig.myexpenses.next.data.receipts.ReceiptMatch
import org.totschnig.myexpenses.next.data.receipts.ReceiptMatcher
import org.totschnig.myexpenses.next.data.receipts.ReceiptTarget
import timber.log.Timber

/**
 * A receipt scanned on its own, or waiting, whose transaction the user chooses from the
 * [candidates], the [preselected] one first. Without a fitting transaction it can wait for it.
 *
 * @param waitingId of the saved receipt that waits for its transaction, null for a new scan
 */
data class ReceiptAssignment(
    val receipt: Receipt,
    val itemCount: Int?,
    val waitingId: Long?,
    val candidates: List<ReceiptMatch>,
    val leftoverRows: List<LeftoverRow> = emptyList(),
) {
    val preselected: ReceiptMatch?
        get() = ReceiptMatcher.sure(candidates)

    /** What the items are checked against while the receipt waits for its transaction */
    val receiptAmount: Long
        get() = receipt.total ?: receipt.items.sumOf { it.amount }
}

/**
 * What a new transaction for a receipt is filled with
 *
 * @param amount negative, as it is an expense
 * @param payee the one of the latest expense at the shop of the receipt, if any
 */
data class NewTransactionPrefill(val amount: BigDecimal, val date: LocalDate?, val payee: PrefillPayee?)

data class PrefillPayee(val id: Long, val name: String)

/**
 * Assigns receipts to their transactions for [ReceiptViewModel]: a receipt scanned on its own is
 * assigned to a transaction the [ReceiptMatcher] suggests or waits for its transaction to be
 * booked; a waiting one is assigned once it is.
 *
 * @param scope of the view model
 * @param openDraft opens a new scan for checking, with the payee of its transaction, on [io]
 * @param onFailed assigning a waiting receipt to the transaction of the target failed
 */
class ReceiptAssignments(
    private val database: ReceiptDatabase,
    private val homeCurrency: StateFlow<CurrencyInfo>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val openDraft: (ReceiptDraft, payee: String?) -> Unit,
    private val onFailed: (ReceiptTarget) -> Unit,
) {
    private val _assignment = MutableStateFlow<ReceiptAssignment?>(null)

    /** The receipt whose transaction the user chooses, at most one at a time */
    val assignment: StateFlow<ReceiptAssignment?> = _assignment.asStateFlow()

    private val _otherExpenses = MutableStateFlow<List<BookedTransaction>?>(null)

    /** Expenses of the last weeks for choosing another transaction than the candidates, once loaded */
    val otherExpenses: StateFlow<List<BookedTransaction>?> = _otherExpenses.asStateFlow()

    /** Currency of a [ReceiptDraft.isStandalone] draft: of its transaction, or the home currency */
    var standaloneCurrency: CurrencyInfo? = null
        private set

    /**
     * Receipts that wait for their transaction, each with the transactions booked meanwhile that
     * may be it; those with a sure one first
     */
    val waiting: Flow<List<ReceiptAssignment>> = database.changes(withTransactions = true).map {
        withContext(io) {
            database.loadWaiting()
                .map { (id, receipt) -> ReceiptAssignment(receipt, null, id, candidates(receipt)) }
                .sortedBy { if (it.preselected != null) 0 else if (it.candidates.isNotEmpty()) 1 else 2 }
        }
    }

    /** Shows a new scan for choosing its transaction; call it on [io], it reads the candidates */
    fun showScan(receipt: Receipt, itemCount: Int?, leftoverRows: List<LeftoverRow>) {
        _assignment.value = ReceiptAssignment(receipt, itemCount, null, candidates(receipt), leftoverRows)
    }

    /** Opens a waiting receipt for choosing its transaction */
    fun openWaiting(assignment: ReceiptAssignment) {
        _otherExpenses.value = null
        _assignment.value = assignment
    }

    /** What was shown before Android ended the process, see [ReceiptSavedState] */
    fun restore(assignment: ReceiptAssignment?, standaloneCurrency: CurrencyInfo?) {
        this.standaloneCurrency = standaloneCurrency
        assignment?.let {
            _assignment.value = it
            refreshCandidates()
        }
    }

    /** Deletes the waiting receipt of the [assignment] */
    fun deleteWaiting() {
        val id = _assignment.value?.waitingId ?: return
        close()
        scope.launch(io) { database.deleteWaiting(id) }
    }

    /** Expenses around the date of the receipt that may be its transaction, best first */
    private fun candidates(receipt: Receipt): List<ReceiptMatch> {
        val header = ReceiptHeader(receipt.merchant, receipt.date, receipt.total, receipt.cardAmount)
        val today = LocalDate.now()
        val from = header.date?.minusDays(1) ?: today.minusDays(ReceiptMatcher.DAYS_WITHOUT_DATE)
        val to = (header.date?.plusDays(ReceiptMatcher.MAX_DAYS_AFTER) ?: today).plusDays(1)
        return ReceiptMatcher.candidates(header, database.loadExpenses(from, to), today)
    }

    /**
     * The user chose the [transaction] of the receipt of the [assignment]: a new scan is checked
     * before it is saved, a waiting receipt was checked before and is assigned right away
     */
    fun assign(transaction: BookedTransaction) {
        val assignment = _assignment.value ?: return
        close()
        if (assignment.waitingId != null) {
            scope.launch(io) {
                try {
                    database.assign(assignment.waitingId, transaction.id)
                } catch (e: Exception) {
                    Timber.e(e)
                    onFailed(ReceiptTarget.Transaction(transaction.id))
                }
            }
        } else {
            val draft = ReceiptDraft.of(
                ReceiptTarget.Transaction(transaction.id), -transaction.amount, assignment.receipt,
                isNewPhoto = true, assignment.itemCount, assignment.leftoverRows
            ).copy(isStandalone = true)
            standaloneCurrency = transaction.currency ?: homeCurrency.value
            scope.launch(io) { openDraft(draft, transaction.payeeName) }
        }
    }

    /** No transaction fits yet: the new scan is checked and then waits for its transaction */
    fun keepWaiting() {
        val assignment = _assignment.value ?: return
        close()
        if (assignment.waitingId != null) return
        val draft = ReceiptDraft.of(
            ReceiptTarget.Waiting(null), assignment.receiptAmount, assignment.receipt,
            isNewPhoto = true, assignment.itemCount, assignment.leftoverRows
        ).copy(isStandalone = true)
        standaloneCurrency = homeCurrency.value
        scope.launch(io) { openDraft(draft, null) }
    }

    /**
     * For a purchase not booked by the bank, e.g. in cash: amount and date of the receipt, and the
     * payee of the latest expense at its shop
     */
    suspend fun newTransactionPrefill(): NewTransactionPrefill? {
        val assignment = _assignment.value ?: return null
        val receipt = assignment.receipt
        val currency = homeCurrency.value
        val amount = receipt.cardAmount ?: receipt.total ?: receipt.items.sumOf { it.amount }
        val payee = receipt.merchant?.let { merchant ->
            withContext(io) {
                val today = LocalDate.now()
                database.loadExpenses(today.minusDays(PAYEE_HISTORY_DAYS), today.plusDays(1))
                    .firstOrNull { it.payeeId != null && ReceiptMatcher.merchantFits(merchant, it) }
                    ?.let { PrefillPayee(it.payeeId!!, it.payeeName.orEmpty()) }
            }
        }
        return NewTransactionPrefill(BigDecimal.valueOf(-amount, currency.fractionDigits), receipt.date, payee)
    }

    /** After a transaction was created for the receipt, it is among the candidates */
    fun refreshCandidates() {
        val assignment = _assignment.value ?: return
        scope.launch(io) {
            val candidates = candidates(assignment.receipt)
            _assignment.update { if (it == assignment) it.copy(candidates = candidates) else it }
        }
    }

    fun close() {
        _assignment.value = null
        _otherExpenses.value = null
    }

    /**
     * Loads the expenses of [OTHER_EXPENSES_DAYS] up to the last day the transaction of the
     * receipt may have been booked, for choosing one that was not suggested
     */
    fun loadOtherExpenses() {
        val assignment = _assignment.value ?: return
        scope.launch(io) {
            val end = (assignment.receipt.date ?: LocalDate.now()).plusDays(ReceiptMatcher.MAX_DAYS_AFTER + 1)
            _otherExpenses.value = database.loadExpenses(end.minusDays(OTHER_EXPENSES_DAYS), end)
                .filter { !it.isTransfer && !it.hasReceipt }
        }
    }

    companion object {
        const val OTHER_EXPENSES_DAYS = 60L
        private const val PAYEE_HISTORY_DAYS = 365L
    }
}
