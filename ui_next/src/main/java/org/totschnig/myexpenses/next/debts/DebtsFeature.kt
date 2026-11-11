package org.totschnig.myexpenses.next.debts

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.BehindStatusBar
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextKey
import org.totschnig.myexpenses.next.NextNavigation
import org.totschnig.myexpenses.next.NextShell
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.debts.DebtOverviewStore
import org.totschnig.myexpenses.next.feature.debts.DebtBookViewModel
import org.totschnig.myexpenses.next.feature.debts.DebtChip
import org.totschnig.myexpenses.next.feature.debts.DebtOverviewCard
import org.totschnig.myexpenses.next.feature.debts.DebtSection
import org.totschnig.myexpenses.next.feature.debts.DebtsFlow
import org.totschnig.myexpenses.next.feature.debts.DebtsViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * Debts with payees: which debt a transaction pays, in its details; the pages of the debts, opened
 * from the card of the overview and from the menu
 */
class DebtsFeature(activity: ComponentActivity, data: NextData) : NextFeature {
    private val database by lazy { RepositoryDebtDatabase(activity.injector.repository()) }

    val viewModel by activity.viewModelsOf { DebtsViewModel(database) }

    val book by activity.viewModelsOf {
        DebtBookViewModel(database, database, DebtOverviewStore(data.dataStore), it)
    }

    @Composable
    override fun TransactionSections(transaction: ShownTransaction) {
        DebtSection(viewModel, transaction)
    }

    @Composable
    override fun TransactionChips(transaction: ShownTransaction) {
        DebtChip(viewModel, transaction)
    }

    @Composable
    override fun OverviewCard(navigation: NextNavigation) {
        DebtOverviewCard(book) { navigation.show(NextKey.Debts()) }
    }

    override fun onMenuCommand(command: Int, navigation: NextNavigation): Boolean {
        if (command != R.id.DEBT_COMMAND) return false
        navigation.show(NextKey.Debts())
        return true
    }

    override fun EntryProviderScope<NavKey>.pages(shell: NextShell) {
        entry<NextKey.Debts> { key ->
            BehindStatusBar(MaterialTheme.colorScheme.surfaceContainer) {
                DebtsFlow(book, onClose = { shell.navigation.close(key) }, openDebt = key.openDebt)
            }
        }
    }
}
