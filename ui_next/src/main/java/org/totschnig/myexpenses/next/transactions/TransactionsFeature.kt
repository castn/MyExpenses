package org.totschnig.myexpenses.next.transactions

import androidx.activity.ComponentActivity
import java.text.DecimalFormatSymbols
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.feature.transactions.TagField
import org.totschnig.myexpenses.next.feature.transactions.TemplatesViewModel
import org.totschnig.myexpenses.next.feature.transactions.TransactionEditViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * Transactions entered or changed by hand: the form in the details of a transaction, the screen
 * for a new one, [TransactionEditorActivity], and the templates, [TemplatesActivity]
 *
 * @param tagField the tags in the form, from the area of the tags, null without it
 */
class TransactionsFeature(activity: ComponentActivity, data: NextData, val tagField: TagField?) {
    private val templateStore by lazy {
        val injector = activity.injector
        RepositoryTemplateStore(
            activity.applicationContext,
            injector.repository(),
            injector.currencyContext(),
            injector.exchangeRateHandler()
        )
    }

    val viewModel by activity.viewModelsOf {
        TransactionEditViewModel(
            RepositoryTransactionStore(activity.injector.repository()),
            data.categorySuggestions,
            templateStore,
            it,
            DecimalFormatSymbols.getInstance().decimalSeparator
        )
    }

    val templates by activity.viewModelsOf { TemplatesViewModel(templateStore) }
}
