package org.totschnig.myexpenses.next.receipts

import org.totschnig.myexpenses.MyApplication
import android.net.Uri
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.addAttachments
import org.totschnig.myexpenses.db2.assignReceipt
import org.totschnig.myexpenses.db2.deleteReceipt
import org.totschnig.myexpenses.db2.deleteReceiptWithoutTransaction
import org.totschnig.myexpenses.db2.loadAllReceiptItems
import org.totschnig.myexpenses.db2.loadLearnedItemGroups
import org.totschnig.myexpenses.db2.loadReceipt
import org.totschnig.myexpenses.db2.loadReceiptsWithoutTransaction
import org.totschnig.myexpenses.db2.saveReceipt
import org.totschnig.myexpenses.db2.saveReceiptWithoutTransaction
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.next.toInfo
import org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_SPLIT_PART
import org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_VOID
import org.totschnig.myexpenses.provider.KEY_ACCOUNT_LABEL
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_STATUS
import org.totschnig.myexpenses.provider.KEY_TRANSACTIONID
import org.totschnig.myexpenses.provider.KEY_TRANSFER_ACCOUNT
import org.totschnig.myexpenses.provider.RECEIPTS_URI
import org.totschnig.myexpenses.provider.STATUS_ARCHIVE
import org.totschnig.myexpenses.provider.STATUS_UNCOMMITTED
import org.totschnig.myexpenses.provider.TransactionProvider.TRANSACTIONS_URI
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.PictureDirHelper
import org.totschnig.myexpenses.util.epoch2LocalDate
import org.totschnig.myexpenses.util.io.FileCopyUtils
import org.totschnig.myexpenses.util.toEpoch
import java.time.LocalDate
import java.time.LocalTime
import org.totschnig.myexpenses.model2.Receipt as AppReceipt
import org.totschnig.myexpenses.model2.ReceiptItem as AppReceiptItem
import org.totschnig.myexpenses.next.data.receipts.BookedTransaction
import org.totschnig.myexpenses.next.data.receipts.ReceiptDatabase
import org.totschnig.myexpenses.next.data.receipts.Receipt
import org.totschnig.myexpenses.next.data.receipts.ReceiptItem

/** [ReceiptDatabase] through the repository and provider of the app */
class ProviderReceiptDatabase(
    private val application: MyApplication,
    private val repository: Repository,
    private val currencyContext: CurrencyContext,
) : ReceiptDatabase {
    private val contentResolver get() = repository.contentResolver

    override fun changes(withTransactions: Boolean): Flow<Unit> {
        val receipts = contentResolver.observeQuery(RECEIPTS_URI, notifyForDescendants = true)
        return if (withTransactions) {
            combine(receipts, contentResolver.observeQuery(TRANSACTIONS_URI, notifyForDescendants = true)) { _, _ -> }
        } else receipts.map { }
    }

    override fun changesOf(transactionId: Long): Flow<Unit> = contentResolver.observeQuery(
        uri = RECEIPTS_URI,
        selection = "$KEY_TRANSACTIONID = ?",
        selectionArgs = arrayOf(transactionId.toString()),
        notifyForDescendants = true
    ).map { }

    override fun load(transactionId: Long) = repository.loadReceipt(transactionId)?.toNext()

    override fun loadWaiting() = repository.loadReceiptsWithoutTransaction().map { (id, receipt) -> id to receipt.toNext() }

    override fun loadAllItems() = repository.loadAllReceiptItems().mapValues { (_, items) -> items.map { it.toNext() } }

    override fun loadLearnedItemGroups() = repository.loadLearnedItemGroups()

    override fun loadExpenses(from: LocalDate, to: LocalDate): List<BookedTransaction> {
        val withReceipt = contentResolver.query(
            RECEIPTS_URI, arrayOf(KEY_TRANSACTIONID), "$KEY_TRANSACTIONID IS NOT NULL", null, null
        )?.useAndMapToList { it.getLong(KEY_TRANSACTIONID) }?.toSet() ?: emptySet()
        return contentResolver.query(
            TRANSACTIONS_URI,
            arrayOf(KEY_ROWID, KEY_DATE, KEY_AMOUNT, KEY_PAYEEID, KEY_PAYEE_NAME, KEY_COMMENT, KEY_TRANSFER_ACCOUNT, KEY_CURRENCY, KEY_ACCOUNT_LABEL),
            "$KEY_AMOUNT < 0 AND $WHERE_NOT_SPLIT_PART AND $KEY_STATUS NOT IN ($STATUS_UNCOMMITTED, $STATUS_ARCHIVE)" +
                    " AND $WHERE_NOT_VOID AND $KEY_DATE >= ? AND $KEY_DATE < ?",
            arrayOf(from.toEpoch(LocalTime.MIN).toString(), to.toEpoch(LocalTime.MIN).toString()),
            "$KEY_DATE DESC"
        )?.useAndMapToList { cursor ->
            val id = cursor.getLong(KEY_ROWID)
            BookedTransaction(
                id = id,
                date = epoch2LocalDate(cursor.getLong(KEY_DATE)),
                amount = cursor.getLong(KEY_AMOUNT),
                payeeId = cursor.getLongOrNull(KEY_PAYEEID),
                payeeName = cursor.getStringOrNull(KEY_PAYEE_NAME),
                comment = cursor.getStringOrNull(KEY_COMMENT),
                isTransfer = cursor.getLongOrNull(KEY_TRANSFER_ACCOUNT) != null,
                hasReceipt = id in withReceipt,
                currency = currencyContext[cursor.getString(KEY_CURRENCY)].toInfo(),
                accountLabel = cursor.getStringOrNull(KEY_ACCOUNT_LABEL)
            )
        } ?: emptyList()
    }

    override fun save(transactionId: Long, receipt: Receipt) = repository.saveReceipt(transactionId, receipt.toApp())

    override fun saveWaiting(receiptId: Long?, receipt: Receipt) =
        repository.saveReceiptWithoutTransaction(receiptId, receipt.toApp())

    override fun assign(receiptId: Long, transactionId: Long) = repository.assignReceipt(receiptId, transactionId)

    override fun delete(transactionId: Long) = repository.deleteReceipt(transactionId)

    override fun deleteWaiting(receiptId: Long) = repository.deleteReceiptWithoutTransaction(receiptId)

    override fun keepPhoto(photo: Uri, transactionId: Long?): Uri =
        PictureDirHelper.getOutputMediaUri(false, application, prefix = "receipt_").also {
            FileCopyUtils.copy(contentResolver, photo, it)
            if (transactionId != null) repository.addAttachments(transactionId, listOf(it))
        }

    override fun viewableUri(photo: Uri): Uri = AppDirHelper.ensureContentUri(photo, application)
}

private fun AppReceipt.toNext() = Receipt(merchant, total, items.map { it.toNext() }, photo, date, cardAmount)

private fun AppReceiptItem.toNext() =
    ReceiptItem(name, amount, quantity, unit, unitPrice, taxClass, box, group, groupByUser)

private fun Receipt.toApp() = AppReceipt(merchant, total, items.map { it.toApp() }, photo, date, cardAmount)

private fun ReceiptItem.toApp() =
    AppReceiptItem(name, amount, quantity, unit, unitPrice, taxClass, box, group, groupByUser)
