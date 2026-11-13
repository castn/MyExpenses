package org.totschnig.myexpenses.next.setup

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.totschnig.myexpenses.next.BehindStatusBar
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextKey
import org.totschnig.myexpenses.next.NextNavigation
import org.totschnig.myexpenses.next.NextShell
import org.totschnig.myexpenses.next.feature.setup.SetupCard
import org.totschnig.myexpenses.next.feature.setup.SetupFlow
import org.totschnig.myexpenses.next.feature.setup.SetupTransactions
import org.totschnig.myexpenses.next.feature.setup.SetupViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * The guided setup after the first start: its card at the top of the overview, which takes the
 * overview over while the setup is offered, and its pages
 *
 * @param contracts the suggestions of contracts for the contracts step, from the contracts area
 * @param incomes the regular incomes and the choice of the salary for the income step, from the
 * contracts area
 */
class SetupFeature(
    activity: ComponentActivity,
    private val data: NextData,
    private val contracts: (@Composable ColumnScope.() -> Unit)? = null,
    private val incomes: (@Composable ColumnScope.() -> Unit)? = null
) : NextFeature {
    val viewModel by activity.viewModelsOf { SetupViewModel(data.setup, it) }

    @Composable
    override fun takesOverOverview(): Boolean =
        viewModel.isOffered.collectAsStateWithLifecycle().value

    @Composable
    override fun OverviewBanner(navigation: NextNavigation) {
        SetupCard(viewModel, onOpen = { navigation.show(NextKey.Setup) })
    }

    override fun EntryProviderScope<NavKey>.pages(shell: NextShell) {
        entry<NextKey.Setup> {
            val currency by data.homeCurrency.collectAsStateWithLifecycle()
            BehindStatusBar(MaterialTheme.colorScheme.surface) {
                SetupFlow(
                    viewModel = viewModel,
                    currency = currency,
                    onClose = { shell.navigation.close(NextKey.Setup) },
                    contracts = contracts,
                    incomes = incomes,
                    transactions = SetupTransactions(
                        shell.fixedTransactions,
                        shell.transactionDetails
                    )
                )
            }
        }
    }
}
