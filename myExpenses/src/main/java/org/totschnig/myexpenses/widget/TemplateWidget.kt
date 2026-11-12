package org.totschnig.myexpenses.widget

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.showTemplateInstantiationResult
import org.totschnig.myexpenses.db2.instantiateTemplate
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.myApplication
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.doAsync

const val CLICK_ACTION_SAVE = "save"
const val CLICK_ACTION_EDIT = "edit"

class TemplateWidget : AbstractListWidget(
    TemplateWidgetService::class.java,
    PrefKey.PROTECTION_ENABLE_TEMPLATE_WIDGET
) {
    override val emptyTextResourceId = R.string.no_templates

    override fun handleWidgetClick(context: Context, intent: Intent) {
        val templateId = intent.getLongExtra(KEY_ROWID, 0)
        val screens = context.injector.templateScreens()
        when (intent.getStringExtra(KEY_CLICK_ACTION)) {
            null -> {
                context.startActivity(screens.list(context).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_START_FROM_WIDGET, true)
                })
            }

            CLICK_ACTION_SAVE -> {
                if ((context.myApplication).shouldLock(null)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.warning_instantiate_template_from_widget_password_protected),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    doAsync {
                        val result = repository.instantiateTemplate(
                            exchangeRateHandler,
                            templateId,
                            currencyContext
                        )
                        withContext(Dispatchers.Main) {
                            context.showTemplateInstantiationResult(result)
                        }
                    }
                }
            }

            CLICK_ACTION_EDIT -> context.startActivity(
                screens.newTransaction(context, templateId).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_START_FROM_WIDGET, true)
                    putExtra(EXTRA_START_FROM_WIDGET_DATA_ENTRY, true)
                })
        }
    }

    companion object {
        val OBSERVED_URIS = arrayOf(
            TransactionProvider.TEMPLATES_URI,
            TransactionProvider.ACCOUNTS_URI //if color changes
        )

    }
}
