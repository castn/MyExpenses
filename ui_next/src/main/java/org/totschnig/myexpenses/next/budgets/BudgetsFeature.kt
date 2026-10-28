package org.totschnig.myexpenses.next.budgets

import androidx.compose.material3.MaterialTheme
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.next.BehindStatusBar
import org.totschnig.myexpenses.next.NextShell
import org.totschnig.myexpenses.next.feature.budgets.BudgetFlow
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.Serializable
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextKey
import org.totschnig.myexpenses.next.NextNavigation
import org.totschnig.myexpenses.next.OverviewBudgets
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.data.budgets.paceWarning
import org.totschnig.myexpenses.next.feature.budgets.BudgetViewModel
import org.totschnig.myexpenses.next.feature.budgets.PotSection
import org.totschnig.myexpenses.next.viewModelsOf
import org.totschnig.myexpenses.viewmodel.BudgetListViewModel

/** The budget with its pots, which takes over the budgets of the classic screens */
class BudgetsFeature(private val activity: BaseActivity, private val data: NextData) : NextFeature {
    /** The budgets of the classic screens */
    private val legacy by lazy {
        activity.viewModels<BudgetListViewModel>().value.also { activity.injector.inject(it) }
    }

    val viewModel by activity.viewModelsOf { savedState ->
        BudgetViewModel(
            data.budgets,
            homeCurrency = data.homeCurrency,
            legacyCount = legacy.data.map { it.size },
            loadLegacy = { LegacyBudgetReading.load(activity.injector.repository(), legacy.data.first()) },
            savedState = savedState
        )
    }

    @Composable
    override fun TransactionSections(transaction: ShownTransaction) {
        val homeCurrency by data.homeCurrency.collectAsStateWithLifecycle()
        PotSection(viewModel, homeCurrency, transaction.id)
    }

    @Composable
    override fun overviewBudgets(navigation: NextNavigation): OverviewBudgets {
        val homeCurrency by data.homeCurrency.collectAsStateWithLifecycle()
        val overview by viewModel.current.collectAsStateWithLifecycle(null)
        val amountFormat = rememberAmountFormat(homeCurrency)
        val summary = overview?.takeIf { it.pots.isNotEmpty() }?.let {
            listOfNotNull(
                stringResource(R.string.next_budget_free_summary, amountFormat(it.frame.free)),
                it.paceWarning()?.let { warning ->
                    stringResource(
                        R.string.next_budget_pace_warning,
                        warning.pot.name,
                        amountFormat(warning.projected!! - warning.planned - warning.carried)
                    )
                }
            ).joinToString("\n")
        }
        return OverviewBudgets(
            budgets = overview?.overviewBudgets(),
            summary = summary,
            inviteDismissed = viewModel.inviteDismissed.collectAsStateWithLifecycle().value,
            onDismissInvite = viewModel::dismissInvite,
            onClick = { navigation.show(NextKey.Budget(openPot = it.key)) },
            onShowAll = { navigation.show(NextKey.Budget()) },
            onAdd = { navigation.show(NextKey.Budget(addPot = true)) }
        )
    }

    override fun EntryProviderScope<NavKey>.pages(shell: NextShell) {
        entry<NextKey.Budget> { key ->
            BehindStatusBar(MaterialTheme.colorScheme.surfaceContainer) {
                val homeCurrency by data.homeCurrency.collectAsStateWithLifecycle()
                BudgetFlow(
                    viewModel, shell.fixedTransactions, homeCurrency,
                    onBack = { shell.navigation.close(key) },
                    onConsent = { data.ledgerSource.setConsent(true) },
                    openPot = key.openPot,
                    addPot = key.addPot,
                    requestNewPot = {
                        // Pots are part of the budget feature, so the same licence applies
                        activity.contribFeatureRequested(ContribFeature.BUDGET, TAG_ADD_POT)
                    }
                )
            }
        }
    }

    override fun onContribFeatureCalled(feature: ContribFeature, tag: Serializable?): Boolean {
        if (feature != ContribFeature.BUDGET || tag != TAG_ADD_POT) return false
        activity.recordUsage(feature)
        viewModel.grantNewPot()
        return true
    }

    companion object {
        /** Tag for the budget feature request, to create a pot of the budget of the new UI */
        const val TAG_ADD_POT = "ADD_POT"
    }
}
