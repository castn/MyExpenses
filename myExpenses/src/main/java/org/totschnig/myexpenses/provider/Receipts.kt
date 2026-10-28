package org.totschnig.myexpenses.provider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import android.net.Uri
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Items of a scanned receipt, stored as context of a transaction. The transaction amount
 * stays unchanged, the items are no splits. A receipt scanned before its transaction is booked
 * waits without transaction, with its photo as [KEY_URI], until it is assigned.
 * Kept in a separate file to keep the changes in the existing provider classes small.
 */

const val TABLE_RECEIPTS = "receipts"
const val TABLE_RECEIPT_ITEMS = "receipt_items"

const val KEY_RECEIPT_ID = "receipt_id"
const val KEY_MERCHANT = "merchant"
const val KEY_ITEM_POSITION = "position"
const val KEY_NAME = "name"
const val KEY_QUANTITY = "quantity"
const val KEY_UNIT = "unit"
const val KEY_UNIT_PRICE = "unit_price"
const val KEY_TAX_CLASS = "tax_class"
const val KEY_PRODUCT_KEY = "product_key"
/** Position of the row on the photo: "left,top,right,bottom" */
const val KEY_BOX = "box"
/** Kind of goods, id of ItemGroup in ui_next, null if unknown */
const val KEY_ITEM_GROUP = "item_group"
/** 1 if the user chose [KEY_ITEM_GROUP], such choices are learned and never overwritten */
const val KEY_ITEM_GROUP_BY_USER = "item_group_by_user"

/** Day of the purchase printed on the receipt, yyyy-MM-dd */
const val KEY_RECEIPT_DATE = "receipt_date"
/** Amount paid by card, differs from the total by cash back or coupons */
const val KEY_CARD_AMOUNT = "card_amount"

/** Extras of [METHOD_SAVE_RECEIPT]: the receipt without receipt deletes it */
const val KEY_RECEIPT = "receipt"
const val KEY_RECEIPT_ITEMS = "receipt_items"

const val METHOD_SAVE_RECEIPT = "saveReceipt"
const val METHOD_ASSIGN_RECEIPT = "assignReceipt"

val RECEIPTS_URI: Uri = Uri.parse("content://${TransactionProvider.AUTHORITY}/receipts")
val RECEIPT_ITEMS_URI: Uri = Uri.parse("content://${TransactionProvider.AUTHORITY}/receipts/items")

const val RECEIPTS_CREATE = """
CREATE TABLE $TABLE_RECEIPTS (
    $KEY_ROWID integer primary key autoincrement,
    $KEY_TRANSACTIONID integer unique references $TABLE_TRANSACTIONS($KEY_ROWID) ON DELETE CASCADE,
    $KEY_MERCHANT text,
    $KEY_TOTAL integer,
    $KEY_CARD_AMOUNT integer,
    $KEY_RECEIPT_DATE text,
    $KEY_ATTACHMENT_ID integer references $TABLE_ATTACHMENTS($KEY_ROWID) ON DELETE SET NULL,
    $KEY_URI text
);
"""

const val RECEIPT_ITEMS_CREATE = """
CREATE TABLE $TABLE_RECEIPT_ITEMS (
    $KEY_ROWID integer primary key autoincrement,
    $KEY_RECEIPT_ID integer not null references $TABLE_RECEIPTS($KEY_ROWID) ON DELETE CASCADE,
    $KEY_ITEM_POSITION integer not null,
    $KEY_NAME text not null,
    $KEY_AMOUNT integer not null,
    $KEY_QUANTITY real not null default 1,
    $KEY_UNIT text,
    $KEY_UNIT_PRICE integer,
    $KEY_TAX_CLASS text,
    $KEY_PRODUCT_KEY text,
    $KEY_BOX text,
    $KEY_ITEM_GROUP text,
    $KEY_ITEM_GROUP_BY_USER integer not null default 0
);
"""

fun createReceiptTables(db: SupportSQLiteDatabase) {
    db.execSQL(RECEIPTS_CREATE)
    db.execSQL(RECEIPT_ITEMS_CREATE)
    db.execSQL("CREATE INDEX receipt_items_receipt ON $TABLE_RECEIPT_ITEMS($KEY_RECEIPT_ID)")
    db.execSQL("CREATE INDEX receipt_items_product ON $TABLE_RECEIPT_ITEMS($KEY_PRODUCT_KEY)")
}

/** Items joined with their receipt, so that they can be selected by transaction */
const val RECEIPT_ITEMS_JOIN =
    "$TABLE_RECEIPT_ITEMS JOIN $TABLE_RECEIPTS ON $TABLE_RECEIPT_ITEMS.$KEY_RECEIPT_ID = $TABLE_RECEIPTS.$KEY_ROWID"

/**
 * Replaces the receipt of a transaction ([KEY_TRANSACTIONID]) or a receipt without transaction
 * ([KEY_RECEIPT_ID], new if missing) with its items in one database transaction. For a
 * transaction, the receipt values may carry [KEY_URI] of an attachment of the transaction, which
 * is stored as reference to the photo; without transaction, [KEY_URI] is the photo itself.
 *
 * @return the id of the saved receipt as [KEY_RECEIPT_ID], none if it was deleted
 */
fun saveReceipt(db: SupportSQLiteDatabase, extras: Bundle): Bundle {
    val transactionId = extras.getLong(KEY_TRANSACTIONID).takeIf { extras.containsKey(KEY_TRANSACTIONID) }
    val replaced = extras.getLong(KEY_RECEIPT_ID).takeIf { extras.containsKey(KEY_RECEIPT_ID) }
    val receipt = BundleCompat.getParcelable(extras, KEY_RECEIPT, ContentValues::class.java)
    val items = BundleCompat.getParcelableArrayList(extras, KEY_RECEIPT_ITEMS, ContentValues::class.java)
        .orEmpty()
    val result = Bundle(1)
    db.beginTransaction()
    try {
        if (transactionId != null) {
            db.delete(TABLE_RECEIPTS, "$KEY_TRANSACTIONID = ?", arrayOf(transactionId))
        } else if (replaced != null) {
            db.delete(TABLE_RECEIPTS, "$KEY_ROWID = ? AND $KEY_TRANSACTIONID IS NULL", arrayOf(replaced))
        }
        if (receipt != null) {
            val values = ContentValues(receipt).apply {
                if (transactionId != null) {
                    put(KEY_TRANSACTIONID, transactionId)
                    getAsString(KEY_URI)?.let { uri ->
                        remove(KEY_URI)
                        attachmentId(db, uri)?.let { put(KEY_ATTACHMENT_ID, it) }
                    }
                } else if (replaced != null) {
                    put(KEY_ROWID, replaced)
                }
            }
            val receiptId = db.insert(TABLE_RECEIPTS, CONFLICT_NONE, values)
            result.putLong(KEY_RECEIPT_ID, receiptId)
            items.forEachIndexed { index, item ->
                db.insert(TABLE_RECEIPT_ITEMS, CONFLICT_NONE, ContentValues(item).apply {
                    put(KEY_RECEIPT_ID, receiptId)
                    put(KEY_ITEM_POSITION, index)
                })
            }
            // A choice of the user also applies to the same product on earlier receipts,
            // unless the user chose a group there, too
            items.filter { it.getAsInteger(KEY_ITEM_GROUP_BY_USER) == 1 && it.getAsString(KEY_PRODUCT_KEY) != null }
                .forEach { item ->
                    db.update(
                        TABLE_RECEIPT_ITEMS, CONFLICT_NONE,
                        ContentValues(1).apply { put(KEY_ITEM_GROUP, item.getAsString(KEY_ITEM_GROUP)) },
                        "$KEY_PRODUCT_KEY = ? AND $KEY_ITEM_GROUP_BY_USER = 0",
                        arrayOf(item.getAsString(KEY_PRODUCT_KEY))
                    )
                }
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    return result
}

private fun attachmentId(db: SupportSQLiteDatabase, uri: String) =
    db.query("SELECT $KEY_ROWID FROM $TABLE_ATTACHMENTS WHERE $KEY_URI = ?", arrayOf(uri))
        .use { if (it.moveToFirst()) it.getLong(0) else null }

/**
 * Assigns the receipt [KEY_RECEIPT_ID] waiting without transaction to the transaction
 * [KEY_TRANSACTIONID], whose receipt it replaces. Its photo must already be an attachment of the
 * transaction.
 */
fun assignReceipt(db: SupportSQLiteDatabase, extras: Bundle) {
    val receiptId = extras.getLong(KEY_RECEIPT_ID)
    val transactionId = extras.getLong(KEY_TRANSACTIONID)
    db.beginTransaction()
    try {
        val uri = db.query("SELECT $KEY_URI FROM $TABLE_RECEIPTS WHERE $KEY_ROWID = ?", arrayOf(receiptId))
            .use { if (it.moveToFirst()) it.getString(0) else null }
        db.delete(TABLE_RECEIPTS, "$KEY_TRANSACTIONID = ?", arrayOf(transactionId))
        db.update(TABLE_RECEIPTS, CONFLICT_NONE, ContentValues(3).apply {
            put(KEY_TRANSACTIONID, transactionId)
            put(KEY_ATTACHMENT_ID, uri?.let { attachmentId(db, it) })
            putNull(KEY_URI)
        }, "$KEY_ROWID = ? AND $KEY_TRANSACTIONID IS NULL", arrayOf(receiptId))
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
