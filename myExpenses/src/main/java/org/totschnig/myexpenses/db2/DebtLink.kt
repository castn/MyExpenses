package org.totschnig.myexpenses.db2

import android.content.ContentUris
import android.content.ContentValues
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_AMOUNT_HOME_EQUIVALENT
import org.totschnig.myexpenses.provider.KEY_CR_STATUS
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_DEBT_ID
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.TransactionProvider.DEBTS_URI
import org.totschnig.myexpenses.provider.TransactionProvider.TRANSACTIONS_URI
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.viewmodel.data.DisplayDebt

/**
 * A transaction as a debt sees it, for the new UI
 *
 * @param homeAmount [amount] in the home currency
 */
data class DebtLink(
    val transactionId: Long,
    val amount: Long,
    val currency: String,
    val homeAmount: Long,
    val payeeId: Long?,
    val debtId: Long?,
    val isVoid: Boolean
)

/** Debts with their stored values, without sums; also sealed ones */
fun Repository.loadDebts(selection: String?, selectionArgs: Array<String>?): List<DisplayDebt> =
    contentResolver.query(DEBTS_URI, null, selection, selectionArgs, null)
        ?.useAndMapToList { DisplayDebt.fromCursor(it, currencyContext) }
        .orEmpty()

fun Repository.loadDebt(debtId: Long): DisplayDebt? =
    contentResolver.query(ContentUris.withAppendedId(DEBTS_URI, debtId), null, null, null, null)
        ?.useAndMapToList { DisplayDebt.fromCursor(it, currencyContext) }
        ?.firstOrNull()

/** Transactions matching [selection], also parts of splits and voided ones */
fun Repository.loadDebtLinks(selection: String, selectionArgs: Array<String>?): List<DebtLink> =
    contentResolver.query(
        TransactionProvider.EXTENDED_URI.buildUpon()
            .appendQueryParameter(TransactionProvider.QUERY_PARAMETER_INCLUDE_ALL, "1")
            .build(),
        arrayOf(
            KEY_ROWID,
            KEY_AMOUNT,
            KEY_CURRENCY,
            KEY_AMOUNT_HOME_EQUIVALENT,
            KEY_PAYEEID,
            KEY_DEBT_ID,
            KEY_CR_STATUS
        ),
        selection,
        selectionArgs,
        null
    )?.useAndMapToList { cursor ->
        DebtLink(
            transactionId = cursor.getLong(KEY_ROWID),
            amount = cursor.getLong(KEY_AMOUNT),
            currency = cursor.getString(KEY_CURRENCY),
            homeAmount = cursor.getLong(KEY_AMOUNT_HOME_EQUIVALENT),
            payeeId = cursor.getLongOrNull(KEY_PAYEEID),
            debtId = cursor.getLongOrNull(KEY_DEBT_ID),
            isVoid = cursor.getString(KEY_CR_STATUS) == CrStatus.VOID.name
        )
    }.orEmpty()

/**
 * Links the transaction to the debt, or unlinks it with null. The database refuses it with an
 * exception if the old or new debt is sealed.
 */
fun Repository.linkToDebt(transactionId: Long, debtId: Long?) {
    val values = ContentValues(1).apply {
        if (debtId == null) putNull(KEY_DEBT_ID) else put(KEY_DEBT_ID, debtId)
    }
    val count = contentResolver.update(
        ContentUris.withAppendedId(TRANSACTIONS_URI, transactionId),
        values,
        null,
        null
    )
    check(count == 1) { "Transaction $transactionId not updated" }
}
