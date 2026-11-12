package org.totschnig.myexpenses.next.transactions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity
import org.totschnig.myexpenses.activity.TemplateScreens
import org.totschnig.myexpenses.next.NextFeatures
import org.totschnig.myexpenses.next.NextTheme
import org.totschnig.myexpenses.next.feature.transactions.TemplatesFlow
import org.totschnig.myexpenses.provider.KEY_TRANSACTIONID

/**
 * The templates, see [TemplatesFlow]: from the menu of the main screen and the template widget, and
 * for a new template from a transaction. A template is opened in [TransactionEditorActivity].
 */
class TemplatesActivity : ProtectedFragmentActivity() {

    private val transactions by lazy { NextFeatures(this).transactions }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fromTransaction = intent.getLongExtra(KEY_TRANSACTIONID, 0L).takeIf { it > 0L }
        setContent {
            NextTheme {
                TemplatesFlow(
                    templates = transactions.templates,
                    editor = transactions.viewModel,
                    tagField = transactions.tagField,
                    onOpenForm = {
                        startActivity(TransactionEditorActivity.fromTemplate(this, it))
                    },
                    onClose = ::finish,
                    fromTransaction = fromTransaction
                )
            }
        }
    }

    companion object {
        /** A new template with the values of transaction [transactionId] */
        fun fromTransaction(context: Context, transactionId: Long) = Intent(
            context,
            TemplatesActivity::class.java
        ).putExtra(KEY_TRANSACTIONID, transactionId)
    }
}

/** The screens of the templates for widget and shortcuts of :myExpenses */
object NextTemplateScreens : TemplateScreens {
    override fun list(context: Context) = Intent(context, TemplatesActivity::class.java)

    override fun newTransaction(context: Context, templateId: Long) =
        TransactionEditorActivity.fromTemplate(context, templateId)
}
