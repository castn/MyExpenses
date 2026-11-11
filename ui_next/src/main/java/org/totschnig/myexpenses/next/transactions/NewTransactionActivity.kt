package org.totschnig.myexpenses.next.transactions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity
import org.totschnig.myexpenses.next.NextFeatures
import org.totschnig.myexpenses.next.NextTheme
import org.totschnig.myexpenses.next.data.transactions.NewTransaction
import org.totschnig.myexpenses.next.feature.transactions.NewTransactionFlow
import org.totschnig.myexpenses.provider.KEY_ROWID

/**
 * The screen for a new expense or income, see [NewTransactionFlow]. An activity of its own, so that
 * the main screen, the receipts and later widgets and shortcuts open it alike. Its result is OK
 * with the id of the transaction saved last, if one was saved.
 */
class NewTransactionActivity : ProtectedFragmentActivity() {

    private val transactions by lazy { NextFeatures(this).transactions }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefill =
            IntentCompat.getSerializableExtra(intent, EXTRA_PREFILL, NewTransaction::class.java)
                ?: NewTransaction()
        setContent {
            NextTheme {
                NewTransactionFlow(
                    transactions.viewModel,
                    prefill,
                    transactions.tagField
                ) { createdId ->
                    if (createdId !=
                        null
                    ) {
                        setResult(RESULT_OK, Intent().putExtra(KEY_ROWID, createdId))
                    }
                    finish()
                }
            }
        }
    }

    /** Opens the screen with what the transaction starts with; the result is the id of the transaction saved */
    object Contract : ActivityResultContract<NewTransaction, Long?>() {
        override fun createIntent(context: Context, input: NewTransaction) =
            Intent(context, NewTransactionActivity::class.java).putExtra(EXTRA_PREFILL, input)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            intent?.getLongExtra(KEY_ROWID, 0L)?.takeIf {
                resultCode == Activity.RESULT_OK &&
                    it > 0L
            }
    }

    companion object {
        private const val EXTRA_PREFILL = "prefill"

        /** For a new expense or income in [accountId], null for the account used last */
        fun intent(context: Context, accountId: Long?, isIncome: Boolean) = Contract.createIntent(
            context,
            NewTransaction(accountId = accountId, isIncome = isIncome)
        )
    }
}
