package org.totschnig.myexpenses.next.transactions

import androidx.activity.ComponentActivity
import java.text.DecimalFormatSymbols
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.feature.transactions.TransactionEditViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/** Transactions changed by hand: the form in the details of a transaction */
class TransactionsFeature(activity: ComponentActivity) {
    val viewModel by activity.viewModelsOf {
        TransactionEditViewModel(
            RepositoryTransactionStore(activity.injector.repository()),
            it,
            DecimalFormatSymbols.getInstance().decimalSeparator
        )
    }
}
