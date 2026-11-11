package org.totschnig.myexpenses.activity

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.RepositoryTransaction
import org.totschnig.myexpenses.db2.instantiateTemplate
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import org.totschnig.myexpenses.viewmodel.AccountSealedException

/** Saves a transaction from a template without a screen, for its pinned shortcut */
class TemplateSaver : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val templateId = intent.getLongExtra(KEY_TEMPLATEID, 0)
        if (templateId <= 0) {
            finish()
            return
        }
        val injector = injector
        lifecycleScope.launch {
            val result = withContext(injector.coroutineDispatcher()) {
                injector.repository().instantiateTemplate(
                    injector.exchangeRateHandler(),
                    templateId,
                    injector.currencyContext()
                )
            }
            showTemplateInstantiationResult(result)
            finish()
        }
    }
}

fun Context.showTemplateInstantiationResult(result: Result<RepositoryTransaction>) {
    Toast.makeText(
        this,
        result.fold(
            onSuccess = {
                resources.getQuantityString(
                    R.plurals.save_transaction_from_template_success,
                    1,
                    1
                )
            },
            onFailure = {
                when (it) {
                    is AccountSealedException -> getString(R.string.object_sealed)
                    else -> getString(R.string.save_transaction_template_deleted)
                }
            }
        ),
        Toast.LENGTH_LONG
    ).show()
}
