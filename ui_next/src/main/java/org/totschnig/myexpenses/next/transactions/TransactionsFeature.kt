package org.totschnig.myexpenses.next.transactions

import androidx.activity.ComponentActivity
import java.text.DecimalFormatSymbols
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.feature.transactions.TagField
import org.totschnig.myexpenses.next.feature.transactions.TransactionEditViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * Transactions entered or changed by hand: the form in the details of a transaction and the screen
 * for a new one, [NewTransactionActivity]
 *
 * @param tagField the tags in the form, from the area of the tags, null without it
 */
class TransactionsFeature(activity: ComponentActivity, data: NextData, val tagField: TagField?) {
    val viewModel by activity.viewModelsOf {
        TransactionEditViewModel(
            RepositoryTransactionStore(activity.injector.repository()),
            data.categorySuggestions,
            it,
            DecimalFormatSymbols.getInstance().decimalSeparator
        )
    }
}
