package org.totschnig.myexpenses.next.debts

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.feature.debts.DebtChip
import org.totschnig.myexpenses.next.feature.debts.DebtSection
import org.totschnig.myexpenses.next.feature.debts.DebtsViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/** Debts with payees: which debt a transaction pays, in its details */
class DebtsFeature(activity: ComponentActivity) : NextFeature {
    val viewModel by activity.viewModelsOf {
        DebtsViewModel(RepositoryDebtDatabase(activity.injector.repository()))
    }

    @Composable
    override fun TransactionSections(transaction: ShownTransaction) {
        DebtSection(viewModel, transaction)
    }

    @Composable
    override fun TransactionChips(transaction: ShownTransaction) {
        DebtChip(viewModel, transaction)
    }
}
