package org.totschnig.myexpenses.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.math.BigDecimal
import java.time.LocalDate
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.viewmodel.data.Transaction2
import org.totschnig.myexpenses.widget.EXTRA_START_FROM_WIDGET
import org.totschnig.myexpenses.widget.EXTRA_START_FROM_WIDGET_DATA_ENTRY

/**
 * The screen for entering an expense or income and changing one. It lives in :ui_next, which
 * :myExpenses does not know, so :app hands it in through [org.totschnig.myexpenses.di.FeatureModule],
 * like [MainScreenEntry]. The screen reads the extras below.
 *
 * Extras of a new transaction, all optional: [KEY_ACCOUNTID] (else the open account used last),
 * [KEY_CURRENCY] (an account of this currency, for the aggregate accounts), [EXTRA_INCOME],
 * [KEY_AMOUNT] as [BigDecimal] (its sign wins over [EXTRA_INCOME]), [EXTRA_DAY] as epoch day,
 * [EXTRA_PAYEE_NAME] and [KEY_OCR_RESULT]. A transaction to change is given by [KEY_ROWID]. The
 * result is OK with [KEY_ROWID] of the transaction saved.
 */
class TransactionEditorEntry(val activityClass: Class<out Activity>)

const val EXTRA_INCOME = "income"
const val EXTRA_DAY = "day"
const val EXTRA_PAYEE_NAME = "payeeName"

/** An [org.totschnig.myexpenses.feature.OcrResultFlat] read from a receipt */
const val KEY_OCR_RESULT = "ocrResult"

/** Opens the screen for a new expense or income, see [TransactionEditorEntry] for the extras */
fun Context.newTransactionIntent(
    accountId: Long? = null,
    isIncome: Boolean = false,
    amount: BigDecimal? = null,
    day: LocalDate? = null,
    payee: String? = null
): Intent = Intent(this, injector.transactionEditor().activityClass).apply {
    accountId?.takeIf { it > 0 }?.let { putExtra(KEY_ACCOUNTID, it) }
    putExtra(EXTRA_INCOME, isIncome)
    amount?.let { putExtra(KEY_AMOUNT, it) }
    day?.let { putExtra(EXTRA_DAY, it.toEpochDay()) }
    payee?.let { putExtra(EXTRA_PAYEE_NAME, it) }
}

/** Opens the screen to change transaction [id], if [canBeEdited] */
fun Context.editTransactionIntent(id: Long): Intent =
    Intent(this, injector.transactionEditor().activityClass).putExtra(KEY_ROWID, id)

/**
 * Marks an intent of a widget or launcher shortcut: the screen may then be used without unlocking
 * the app if the user allowed it, see `PrefKey.PROTECTION_ENABLE_DATA_ENTRY_FROM_WIDGET`
 */
fun Intent.asDataEntryFromWidget(): Intent = apply {
    putExtra(EXTRA_START_FROM_WIDGET, true)
    putExtra(EXTRA_START_FROM_WIDGET_DATA_ENTRY, true)
}

/**
 * Whether the screen of [TransactionEditorEntry] can change this transaction. Splits and transfers
 * are only shown and deleted now, since the import brings them; transfers between own accounts are
 * recognized instead, and a category answers what a transaction was for.
 */
val Transaction2.canBeEdited: Boolean
    get() = !isSplit && !isTransfer && !isSplitPart && !isArchive && crStatus != CrStatus.VOID
