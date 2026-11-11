package org.totschnig.myexpenses.next.receipts

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.totschnig.myexpenses.activity.newTransactionIntent
import org.totschnig.myexpenses.next.feature.receipts.NewTransactionEditor
import org.totschnig.myexpenses.next.feature.receipts.NewTransactionPrefill

/** [NewTransactionEditor] with the screen for a new transaction of the new UI */
object NewTransactionForReceipt : NewTransactionEditor() {
    // The amount is negative for an expense, its sign tells the screen
    override fun createIntent(context: Context, input: NewTransactionPrefill) =
        context.newTransactionIntent(
            amount = input.amount,
            day = input.date,
            payee = input.payee?.name
        )

    override fun parseResult(resultCode: Int, intent: Intent?) = resultCode == Activity.RESULT_OK
}
