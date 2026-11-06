package org.totschnig.myexpenses.provider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.di.DataModule

private const val DATABASE = "numbers-test"

/** The receipts of this fork at its old version 191 */
private const val OLD_RECEIPTS = """
CREATE TABLE receipts (
    _id integer primary key autoincrement,
    transaction_id integer not null unique references transactions(_id) ON DELETE CASCADE,
    merchant text,
    total integer,
    attachment_id integer references attachments(_id) ON DELETE SET NULL
);
"""

private const val OLD_RECEIPT_ITEMS = """
CREATE TABLE receipt_items (
    _id integer primary key autoincrement,
    receipt_id integer not null references receipts(_id) ON DELETE CASCADE,
    position integer not null,
    name text not null,
    amount integer not null,
    quantity real not null default 1,
    unit text,
    unit_price integer,
    tax_class text,
    product_key text,
    box text
);
"""

@RunWith(AndroidJUnit4::class)
class OwnDatabaseNumbersTest : BaseTestWithRepository() {

    private fun helper(): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(application)
            .name(DATABASE)
            .callback(TransactionDatabase(application, prefHandler))
            .build()
    )

    @After
    fun tearDown() {
        application.deleteDatabase(DATABASE)
    }

    /** Creates a database of today, gives it an older shape and number, and opens it again */
    private fun upgradeFrom(
        version: Int,
        shape: SupportSQLiteDatabase.() -> Unit = {}
    ): SupportSQLiteOpenHelper {
        helper().use { helper ->
            helper.writableDatabase.run {
                // Rows without their transaction, which do not matter here
                execSQL("PRAGMA foreign_keys=OFF")
                shape()
                this.version = version
            }
        }
        return helper()
    }

    private fun SupportSQLiteDatabase.removeReceipts() {
        execSQL("DROP TABLE receipt_items")
        execSQL("DROP TABLE receipts")
    }

    private fun SupportSQLiteDatabase.writeReceiptAndItem(receipts: String, items: String) {
        val receiptColumns = "_id, transaction_id, merchant, total"
        val itemColumns = "_id, receipt_id, position, name, amount"
        execSQL("INSERT INTO $receipts ($receiptColumns) VALUES (7, 3, 'Shop', 500)")
        execSQL("INSERT INTO $items ($itemColumns) VALUES (9, 7, 0, 'Milk', 100)")
    }

    private fun SupportSQLiteDatabase.columns(table: String) =
        query("PRAGMA table_info($table)").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }

    /** Same columns as a new database, and the row written by [writeReceiptAndItem] kept */
    private fun assertReceiptsKept(helper: SupportSQLiteOpenHelper) {
        helper.use {
            val db = it.writableDatabase
            assertThat(db.version).isEqualTo(DATABASE_VERSION)
            assertThat(db.columns("receipts")).containsAtLeast("card_amount", "receipt_date", "uri")
            val itemColumns = db.columns("receipt_items")
            assertThat(itemColumns).containsAtLeast("item_group", "item_group_by_user")
            val receipt = "SELECT merchant, total, transaction_id FROM receipts WHERE _id = 7"
            db.query(receipt).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Shop")
                assertThat(c.getLong(1)).isEqualTo(500)
                assertThat(c.getLong(2)).isEqualTo(3)
            }
            val item = "SELECT name, item_group_by_user FROM receipt_items WHERE receipt_id = 7"
            db.query(item).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Milk")
                assertThat(c.getInt(1)).isEqualTo(0)
            }
        }
    }

    /** Columns, foreign keys and indexes, as the text of the definitions differs after a rename */
    private fun receiptSchema(db: SupportSQLiteDatabase) = listOf(
        "table_info(receipts)",
        "table_info(receipt_items)",
        "foreign_key_list(receipts)",
        "foreign_key_list(receipt_items)",
        "index_list(receipt_items)"
    ).associateWith { pragma ->
        db.query("PRAGMA $pragma").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add((0 until c.columnCount).map { c.getString(it) })
                }
            }.sortedBy { it.toString() }
        }
    }

    @Test
    fun createsReceiptsWhenUpgradingFromUpstream() {
        upgradeFrom(191) { removeReceipts() }.use {
            val db = it.writableDatabase
            assertThat(db.version).isEqualTo(DATABASE_VERSION)
            assertThat(db.columns("receipts")).contains("card_amount")
            assertThat(db.columns("receipt_items")).contains("item_group")
        }
    }

    @Test
    fun keepsReceiptsOfToday192() {
        assertReceiptsKept(upgradeFrom(192) { writeReceiptAndItem("receipts", "receipt_items") })
    }

    @Test
    fun keepsReceiptsOfOld193() {
        assertReceiptsKept(upgradeFrom(193) { writeReceiptAndItem("receipts", "receipt_items") })
    }

    @Test
    fun rebuildsReceiptsOfOld192() {
        val helper = upgradeFrom(192) {
            removeReceipts()
            execSQL(OLD_RECEIPTS)
            execSQL(OLD_RECEIPT_ITEMS)
            val addColumn = "ALTER TABLE receipt_items ADD COLUMN"
            execSQL("$addColumn item_group text")
            execSQL("$addColumn item_group_by_user integer not null default 0")
            writeReceiptAndItem("receipts", "receipt_items")
        }
        assertReceiptsKept(helper)
    }

    @Test
    fun rebuildsReceiptsOfOld191() {
        val helper = upgradeFrom(191) {
            removeReceipts()
            execSQL(OLD_RECEIPTS)
            execSQL(OLD_RECEIPT_ITEMS)
            writeReceiptAndItem("receipts", "receipt_items")
        }
        assertReceiptsKept(helper)
    }

    @Test
    fun receiptsMayWaitWithoutTransactionAfterRebuild() {
        upgradeFrom(191) {
            removeReceipts()
            execSQL(OLD_RECEIPTS)
            execSQL(OLD_RECEIPT_ITEMS)
        }.use {
            val values = ContentValues(1).apply { put("merchant", "Shop") }
            val id = it.writableDatabase.insert("receipts", CONFLICT_NONE, values)
            assertThat(id).isGreaterThan(0)
        }
    }

    @Test
    fun rebuildGivesSameTablesAsNewDatabase() {
        val expected = helper().use { receiptSchema(it.writableDatabase) }
        application.deleteDatabase(DATABASE)
        val actual = upgradeFrom(191) {
            removeReceipts()
            execSQL(OLD_RECEIPTS)
            execSQL(OLD_RECEIPT_ITEMS)
            execSQL("CREATE INDEX receipt_items_receipt ON receipt_items(receipt_id)")
            execSQL("CREATE INDEX receipt_items_product ON receipt_items(product_key)")
        }.use { receiptSchema(it.writableDatabase) }
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun fillsInFractionDigitsThatOldVersionsSkipped() {
        val code = "EUR"
        upgradeFrom(193) {
            execSQL(
                "UPDATE $TABLE_CURRENCIES SET $KEY_FRACTION_DIGITS = NULL WHERE $KEY_CODE = '$code'"
            )
            execSQL(
                "INSERT INTO $TABLE_ACCOUNTS ($KEY_LABEL, $KEY_CURRENCY, $KEY_TYPE) " +
                    "VALUES ('A', '$code', (SELECT min($KEY_ROWID) FROM $TABLE_ACCOUNT_TYPES))"
            )
        }.use {
            it.writableDatabase.query(
                "SELECT $KEY_FRACTION_DIGITS FROM $TABLE_CURRENCIES WHERE $KEY_CODE = '$code'"
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(2)
            }
        }
    }

    private fun backup(version: Int, withReceipts: Boolean = false): String {
        val file = application.getDatabasePath("$DATABASE-backup-$version")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.version = version
            if (withReceipts) it.execSQL("CREATE TABLE receipts (_id integer primary key)")
        }
        return file.path
    }

    private fun check(path: String) =
        DataModule().providePeekHelper(prefHandler).checkVersion(application, path)

    private fun assertRefusedAsNewerUpstream(version: Int) {
        val result = check(backup(version))
        assertThat(result.exceptionOrNull()?.message)
            .isEqualTo(application.getString(R.string.restore_newer_upstream, version))
    }

    @Test
    fun acceptsBackupsOfKnownVersions() {
        assertThat(check(backup(191)).isSuccess).isTrue()
        assertThat(check(backup(192, withReceipts = true)).isSuccess).isTrue()
        assertThat(check(backup(193, withReceipts = true)).isSuccess).isTrue()
        assertThat(check(backup(DATABASE_VERSION)).isSuccess).isTrue()
    }

    @Test
    fun refusesBackupsOfNewerUpstream() {
        assertRefusedAsNewerUpstream(192)
        assertRefusedAsNewerUpstream(193)
        assertRefusedAsNewerUpstream(194)
        assertRefusedAsNewerUpstream(FIRST_OWN_DATABASE_VERSION - 1)
    }

    @Test
    fun refusesBackupsOfNewerOwnVersion() {
        val result = check(backup(DATABASE_VERSION + 1))
        assertThat(result.exceptionOrNull()?.message).isEqualTo(
            application.getString(
                R.string.restore_cannot_downgrade,
                DATABASE_VERSION + 1,
                DATABASE_VERSION
            )
        )
    }
}
