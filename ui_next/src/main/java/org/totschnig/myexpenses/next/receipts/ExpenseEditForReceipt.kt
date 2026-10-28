package org.totschnig.myexpenses.next.receipts

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.feature.OcrResultFlat
import org.totschnig.myexpenses.feature.Payee
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.util.toEpoch
import org.totschnig.myexpenses.next.feature.receipts.NewTransactionEditor
import org.totschnig.myexpenses.next.feature.receipts.NewTransactionPrefill

/** [NewTransactionEditor] with the transaction screen of the app */
object ExpenseEditForReceipt : NewTransactionEditor() {
    override fun createIntent(context: Context, input: NewTransactionPrefill) =
        Intent(context, ExpenseEdit::class.java).apply {
            putExtra(KEY_AMOUNT, input.amount)
            input.date?.let { putExtra(KEY_DATE, it.toEpoch()) }
            input.payee?.let { putExtra(ExpenseEdit.KEY_OCR_RESULT, OcrResultFlat(null, null, Payee(it.id, it.name))) }
        }

    override fun parseResult(resultCode: Int, intent: Intent?) = resultCode == Activity.RESULT_OK
}
