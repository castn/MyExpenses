package org.totschnig.myexpenses.next.categories

import android.content.ContentResolver
import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_DEBT_ID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_SEALED
import org.totschnig.myexpenses.provider.TABLE_ACCOUNTS
import org.totschnig.myexpenses.provider.TABLE_DEBTS
import org.totschnig.myexpenses.provider.TransactionProvider.TRANSACTIONS_URI
import org.totschnig.myexpenses.next.data.categories.CategoryWriting

/** Changes of transactions in sealed accounts or of sealed debts are rejected by the database */
private val NOT_SEALED = "$KEY_ACCOUNTID NOT IN (SELECT $KEY_ROWID FROM $TABLE_ACCOUNTS WHERE $KEY_SEALED = 1)" +
        " AND ($KEY_DEBT_ID IS NULL OR $KEY_DEBT_ID NOT IN (SELECT $KEY_ROWID FROM $TABLE_DEBTS WHERE $KEY_SEALED = 1))"

/** [CategoryWriting] through the provider of the app */
class ProviderCategoryWriting(private val contentResolver: ContentResolver) : CategoryWriting {

    override suspend fun changeCategory(transactionId: Long, fromCategoryId: Long?, categoryId: Long) {
        withContext(Dispatchers.IO) {
            contentResolver.update(
                TRANSACTIONS_URI,
                ContentValues().apply { put(KEY_CATID, categoryId) },
                "$KEY_ROWID = ? AND ($KEY_CATID IS NULL" + (fromCategoryId?.let { " OR $KEY_CATID = $it" } ?: "") + ") AND $NOT_SEALED",
                arrayOf(transactionId.toString())
            )
        }
    }

    override suspend fun removeCategory(transactionId: Long, categoryId: Long) {
        withContext(Dispatchers.IO) {
            contentResolver.update(
                TRANSACTIONS_URI,
                ContentValues().apply { putNull(KEY_CATID) },
                "$KEY_ROWID = ? AND $KEY_CATID = ?",
                arrayOf(transactionId.toString(), categoryId.toString())
            )
        }
    }

    override suspend fun setMissingCategory(transactionIds: List<Long>, categoryId: Long) {
        if (transactionIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            transactionIds.chunked(500).forEach { chunk ->
                contentResolver.update(
                    TRANSACTIONS_URI,
                    ContentValues().apply { put(KEY_CATID, categoryId) },
                    "$KEY_ROWID IN (${chunk.joinToString()}) AND $KEY_CATID IS NULL AND $NOT_SEALED",
                    null
                )
            }
        }
    }
}
