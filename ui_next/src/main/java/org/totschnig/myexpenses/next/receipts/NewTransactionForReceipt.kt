package org.totschnig.myexpenses.next.receipts

import android.content.Context
import android.content.Intent
import org.totschnig.myexpenses.next.data.transactions.NewTransaction
import org.totschnig.myexpenses.next.feature.receipts.NewTransactionEditor
import org.totschnig.myexpenses.next.feature.receipts.NewTransactionPrefill
import org.totschnig.myexpenses.next.transactions.NewTransactionActivity

/** [NewTransactionEditor] with the screen for a new transaction of the new UI */
object NewTransactionForReceipt : NewTransactionEditor() {
    override fun createIntent(context: Context, input: NewTransactionPrefill) =
        NewTransactionActivity.Contract.createIntent(
            context,
            NewTransaction(
                isIncome = input.amount.signum() > 0,
                amount = input.amount.abs(),
                day = input.date,
                payee = input.payee?.name
            )
        )

    override fun parseResult(resultCode: Int, intent: Intent?) =
        NewTransactionActivity.Contract.parseResult(resultCode, intent) != null
}
