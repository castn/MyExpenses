package org.totschnig.myexpenses.next.transactions

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import java.math.BigDecimal
import java.time.LocalDate
import org.totschnig.myexpenses.activity.EXTRA_DAY
import org.totschnig.myexpenses.activity.EXTRA_INCOME
import org.totschnig.myexpenses.activity.EXTRA_PAYEE_NAME
import org.totschnig.myexpenses.activity.KEY_OCR_RESULT
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity
import org.totschnig.myexpenses.activity.TransactionEditorEntry
import org.totschnig.myexpenses.activity.newTransactionIntent
import org.totschnig.myexpenses.feature.OcrResultFlat
import org.totschnig.myexpenses.next.NextFeatures
import org.totschnig.myexpenses.next.NextTheme
import org.totschnig.myexpenses.next.data.transactions.NewTransaction
import org.totschnig.myexpenses.next.data.transactions.parseAmountText
import org.totschnig.myexpenses.next.feature.transactions.TransactionEditorFlow
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_ROWID

/**
 * The screen for a new expense or income and for changing one outside of its details, see
 * [TransactionEditorFlow]. An activity of its own, so that the main screen, the receipts, widgets,
 * launcher shortcuts and the screens of :myExpenses open it alike; they build its intent with
 * [newTransactionIntent], see [TransactionEditorEntry]. Its result is OK with [KEY_ROWID] of the
 * transaction saved last, if one was saved.
 */
class TransactionEditorActivity : ProtectedFragmentActivity() {

    private val transactions by lazy { NextFeatures(this).transactions }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transactionId = intent.getLongExtra(KEY_ROWID, 0L).takeIf { it > 0L }
        val prefill = intent.toNewTransaction()
        setContent {
            NextTheme {
                TransactionEditorFlow(
                    transactions.viewModel,
                    transactionId,
                    prefill,
                    transactions.tagField
                ) { savedId ->
                    if (savedId != null) setResult(RESULT_OK, Intent().putExtra(KEY_ROWID, savedId))
                    finish()
                }
            }
        }
    }
}

/**
 * What a new transaction starts with, from the extras described at [TransactionEditorEntry]. A
 * scanned receipt gives amount, day and payee; its amount is read with two decimals.
 */
private fun Intent.toNewTransaction(): NewTransaction {
    val ocr = IntentCompat.getParcelableExtra(this, KEY_OCR_RESULT, OcrResultFlat::class.java)
    val amount = IntentCompat.getSerializableExtra(this, KEY_AMOUNT, BigDecimal::class.java)
        ?: ocr?.amount?.let { text -> parseAmountText(text, 2)?.let { BigDecimal.valueOf(-it, 2) } }
    return NewTransaction(
        accountId = getLongExtra(KEY_ACCOUNTID, 0L).takeIf { it > 0L },
        currency = getStringExtra(KEY_CURRENCY),
        isIncome = amount?.let { it.signum() > 0 } ?: getBooleanExtra(EXTRA_INCOME, false),
        amount = amount?.abs(),
        day =
            getLongExtra(EXTRA_DAY, Long.MIN_VALUE).takeIf {
                it != Long.MIN_VALUE
            }?.let(LocalDate::ofEpochDay)
                ?: ocr?.date?.first,
        payee = getStringExtra(EXTRA_PAYEE_NAME) ?: ocr?.payee?.name
    )
}
