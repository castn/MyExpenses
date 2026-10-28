package org.totschnig.myexpenses.next.data.receipts

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The receipts and expenses in the database of the app, implemented by the shell. Apart from the
 * flows, the functions block and are called on `Dispatchers.IO`.
 */
interface ReceiptDatabase {
    /** Emits at once and on each change of receipts, with [withTransactions] also of transactions */
    fun changes(withTransactions: Boolean = false): Flow<Unit>

    /** Emits at once and on each change of the receipt of the transaction */
    fun changesOf(transactionId: Long): Flow<Unit>

    fun load(transactionId: Long): Receipt?

    /** Receipts that wait for their transaction, with their ids */
    fun loadWaiting(): List<Pair<Long, Receipt>>

    /** Items of all receipts by the id of their transaction */
    fun loadAllItems(): Map<Long, List<ReceiptItem>>

    /** [productKey][org.totschnig.myexpenses.feature.productKey] and id of the group the user chose */
    fun loadLearnedItemGroups(): List<Pair<String, String>>

    /**
     * Expenses from [from] up to before [to] a receipt may belong to, newest first. Split
     * transactions count as a whole, as their payee is stored with them.
     */
    fun loadExpenses(from: LocalDate, to: LocalDate): List<BookedTransaction>

    /** Replaces the receipt of the transaction */
    fun save(transactionId: Long, receipt: Receipt)

    /** Saves a receipt that waits for its transaction, replacing the one with [receiptId]; returns its id */
    fun saveWaiting(receiptId: Long?, receipt: Receipt): Long

    /** Assigns a receipt that waited for its transaction, its photo becomes an attachment of it */
    fun assign(receiptId: Long, transactionId: Long)

    /** Deletes the items, the photo stays attached to the transaction */
    fun delete(transactionId: Long)

    fun deleteWaiting(receiptId: Long)

    /**
     * Copies a new photo, which the scanner only leaves in the cache, to the pictures of the app,
     * as attachment of the transaction if there is one
     */
    fun keepPhoto(photo: Uri, transactionId: Long?): Uri

    /** The stored photo as an uri other apps can open */
    fun viewableUri(photo: Uri): Uri
}
