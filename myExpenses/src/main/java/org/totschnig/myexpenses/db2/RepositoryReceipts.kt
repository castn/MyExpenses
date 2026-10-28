package org.totschnig.myexpenses.db2

import android.content.ContentValues
import android.database.Cursor
import android.os.Bundle
import androidx.core.net.toUri
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.productKey
import org.totschnig.myexpenses.model2.Receipt
import org.totschnig.myexpenses.model2.ReceiptItem
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_ATTACHMENT_ID
import org.totschnig.myexpenses.provider.KEY_BOX
import org.totschnig.myexpenses.provider.KEY_CARD_AMOUNT
import org.totschnig.myexpenses.provider.KEY_ITEM_GROUP
import org.totschnig.myexpenses.provider.KEY_ITEM_GROUP_BY_USER
import org.totschnig.myexpenses.provider.KEY_ITEM_POSITION
import org.totschnig.myexpenses.provider.KEY_MERCHANT
import org.totschnig.myexpenses.provider.KEY_NAME
import org.totschnig.myexpenses.provider.KEY_PRODUCT_KEY
import org.totschnig.myexpenses.provider.KEY_QUANTITY
import org.totschnig.myexpenses.provider.KEY_RECEIPT
import org.totschnig.myexpenses.provider.KEY_RECEIPT_DATE
import org.totschnig.myexpenses.provider.KEY_RECEIPT_ID
import org.totschnig.myexpenses.provider.KEY_RECEIPT_ITEMS
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TAX_CLASS
import org.totschnig.myexpenses.provider.KEY_TOTAL
import org.totschnig.myexpenses.provider.KEY_TRANSACTIONID
import org.totschnig.myexpenses.provider.KEY_UNIT
import org.totschnig.myexpenses.provider.KEY_UNIT_PRICE
import org.totschnig.myexpenses.provider.KEY_URI
import org.totschnig.myexpenses.provider.METHOD_ASSIGN_RECEIPT
import org.totschnig.myexpenses.provider.METHOD_SAVE_RECEIPT
import org.totschnig.myexpenses.provider.RECEIPTS_URI
import org.totschnig.myexpenses.provider.RECEIPT_ITEMS_URI
import org.totschnig.myexpenses.provider.TABLE_ATTACHMENTS
import org.totschnig.myexpenses.provider.TABLE_RECEIPTS
import org.totschnig.myexpenses.provider.TABLE_RECEIPT_ITEMS
import org.totschnig.myexpenses.provider.TransactionProvider.DUAL_URI
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.provider.useAndMapToList
import java.math.BigDecimal
import java.time.LocalDate

/** Replaces the receipt of the transaction */
fun Repository.saveReceipt(transactionId: Long, receipt: Receipt) {
    contentResolver.call(DUAL_URI, METHOD_SAVE_RECEIPT, null, receiptExtras(receipt).apply {
        putLong(KEY_TRANSACTIONID, transactionId)
    })
}

/**
 * Saves a receipt that waits for its transaction, replacing the one with [receiptId]
 *
 * @return the id of the saved receipt
 */
fun Repository.saveReceiptWithoutTransaction(receiptId: Long?, receipt: Receipt): Long =
    contentResolver.call(DUAL_URI, METHOD_SAVE_RECEIPT, null, receiptExtras(receipt).apply {
        receiptId?.let { putLong(KEY_RECEIPT_ID, it) }
    })!!.getLong(KEY_RECEIPT_ID)

/** Assigns a receipt that waited for its transaction, its photo becomes an attachment of it */
fun Repository.assignReceipt(receiptId: Long, transactionId: Long) {
    loadReceiptsWithoutTransaction().firstOrNull { it.first == receiptId }?.second?.photo?.let {
        addAttachments(transactionId, listOf(it))
    }
    contentResolver.call(DUAL_URI, METHOD_ASSIGN_RECEIPT, null, Bundle(2).apply {
        putLong(KEY_RECEIPT_ID, receiptId)
        putLong(KEY_TRANSACTIONID, transactionId)
    })
}

/** Deletes a receipt that waits for its transaction, the photo stays */
fun Repository.deleteReceiptWithoutTransaction(receiptId: Long) {
    contentResolver.call(DUAL_URI, METHOD_SAVE_RECEIPT, null, Bundle(1).apply {
        putLong(KEY_RECEIPT_ID, receiptId)
    })
}

private fun receiptExtras(receipt: Receipt) = Bundle(3).apply {
    putParcelable(KEY_RECEIPT, ContentValues().apply {
        put(KEY_MERCHANT, receipt.merchant)
        put(KEY_TOTAL, receipt.total)
        put(KEY_CARD_AMOUNT, receipt.cardAmount)
        put(KEY_RECEIPT_DATE, receipt.date?.toString())
        receipt.photo?.let { put(KEY_URI, it.toString()) }
    })
    putParcelableArrayList(KEY_RECEIPT_ITEMS, receipt.items.mapTo(ArrayList()) { item ->
        ContentValues().apply {
            put(KEY_NAME, item.name)
            put(KEY_AMOUNT, item.amount)
            put(KEY_QUANTITY, item.quantity.toDouble())
            put(KEY_UNIT, item.unit)
            put(KEY_UNIT_PRICE, item.unitPrice)
            put(KEY_TAX_CLASS, item.taxClass)
            put(KEY_PRODUCT_KEY, productKey(item.name))
            put(KEY_BOX, item.box?.run { "$left,$top,$right,$bottom" })
            put(KEY_ITEM_GROUP, item.group)
            put(KEY_ITEM_GROUP_BY_USER, if (item.groupByUser) 1 else 0)
        }
    })
}

fun Repository.deleteReceipt(transactionId: Long) {
    contentResolver.call(DUAL_URI, METHOD_SAVE_RECEIPT, null, Bundle(1).apply {
        putLong(KEY_TRANSACTIONID, transactionId)
    })
}

private val RECEIPT_PROJECTION = arrayOf(
    KEY_ROWID,
    KEY_MERCHANT,
    KEY_TOTAL,
    KEY_CARD_AMOUNT,
    KEY_RECEIPT_DATE,
    "coalesce($KEY_URI, (SELECT $KEY_URI FROM $TABLE_ATTACHMENTS WHERE $TABLE_ATTACHMENTS.$KEY_ROWID = $TABLE_RECEIPTS.$KEY_ATTACHMENT_ID)) AS $KEY_URI"
)

private fun Repository.toReceipt(cursor: Cursor) = Receipt(
    merchant = cursor.getStringOrNull(KEY_MERCHANT),
    total = cursor.getLongOrNull(KEY_TOTAL),
    items = loadReceiptItems("$KEY_RECEIPT_ID = ?", cursor.getLong(KEY_ROWID)),
    photo = cursor.getStringOrNull(KEY_URI)?.toUri(),
    date = cursor.getStringOrNull(KEY_RECEIPT_DATE)?.let(LocalDate::parse),
    cardAmount = cursor.getLongOrNull(KEY_CARD_AMOUNT)
)

fun Repository.loadReceipt(transactionId: Long): Receipt? = contentResolver.query(
    RECEIPTS_URI, RECEIPT_PROJECTION, "$KEY_TRANSACTIONID = ?", arrayOf(transactionId.toString()), null
)?.use { cursor -> if (cursor.moveToFirst()) toReceipt(cursor) else null }

/** Receipts that wait for their transaction by id, the newest first */
fun Repository.loadReceiptsWithoutTransaction(): List<Pair<Long, Receipt>> = contentResolver.query(
    RECEIPTS_URI, RECEIPT_PROJECTION, "$KEY_TRANSACTIONID IS NULL", null, "$KEY_ROWID DESC"
)?.useAndMapToList { cursor -> cursor.getLong(KEY_ROWID) to toReceipt(cursor) } ?: emptyList()

private val ITEM_PROJECTION = arrayOf(
    KEY_TRANSACTIONID, KEY_NAME, KEY_AMOUNT, KEY_QUANTITY, KEY_UNIT, KEY_UNIT_PRICE, KEY_TAX_CLASS, KEY_BOX,
    KEY_ITEM_GROUP, KEY_ITEM_GROUP_BY_USER
)

private fun Cursor.toReceiptItem() = ReceiptItem(
    name = getString(KEY_NAME),
    amount = getLong(KEY_AMOUNT),
    quantity = BigDecimal.valueOf(getDouble(getColumnIndexOrThrow(KEY_QUANTITY))).stripTrailingZeros(),
    unit = getStringOrNull(KEY_UNIT),
    unitPrice = getLongOrNull(KEY_UNIT_PRICE),
    taxClass = getStringOrNull(KEY_TAX_CLASS),
    box = getStringOrNull(KEY_BOX)?.split(',')?.mapNotNull { it.toIntOrNull() }
        ?.takeIf { it.size == 4 }?.let { (left, top, right, bottom) -> Box(left, top, right, bottom) },
    group = getStringOrNull(KEY_ITEM_GROUP),
    groupByUser = getInt(getColumnIndexOrThrow(KEY_ITEM_GROUP_BY_USER)) == 1
)

private fun Repository.loadReceiptItems(selection: String, id: Long) = contentResolver.query(
    RECEIPT_ITEMS_URI, ITEM_PROJECTION, selection, arrayOf(id.toString()), KEY_ITEM_POSITION
)?.useAndMapToList { it.toReceiptItem() } ?: emptyList()

/** Items of all receipts by transaction, in the order of the receipt, for analyses */
fun Repository.loadAllReceiptItems(): Map<Long, List<ReceiptItem>> = contentResolver.query(
    RECEIPT_ITEMS_URI, ITEM_PROJECTION, "$KEY_TRANSACTIONID IS NOT NULL", null, "$KEY_TRANSACTIONID, $KEY_ITEM_POSITION"
)?.useAndMapToList { it.getLong(KEY_TRANSACTIONID) to it.toReceiptItem() }
    ?.groupBy({ it.first }, { it.second }) ?: emptyMap()

/** Names and groups of all items whose group the user chose, oldest first */
fun Repository.loadLearnedItemGroups(): List<Pair<String, String>> = contentResolver.query(
    RECEIPT_ITEMS_URI,
    arrayOf(KEY_NAME, KEY_ITEM_GROUP),
    "$KEY_ITEM_GROUP_BY_USER = 1 AND $KEY_ITEM_GROUP IS NOT NULL", null,
    "$TABLE_RECEIPT_ITEMS.$KEY_ROWID"
)?.useAndMapToList { cursor -> cursor.getString(KEY_NAME) to cursor.getString(KEY_ITEM_GROUP) } ?: emptyList()
