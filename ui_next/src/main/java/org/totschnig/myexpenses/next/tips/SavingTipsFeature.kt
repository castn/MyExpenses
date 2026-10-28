package org.totschnig.myexpenses.next.tips

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.totschnig.myexpenses.designsystem.ActionChipRow
import org.totschnig.myexpenses.next.BehindStatusBar
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextKey
import org.totschnig.myexpenses.next.NextNavigation
import org.totschnig.myexpenses.next.NextShell
import org.totschnig.myexpenses.next.NextTab
import org.totschnig.myexpenses.next.contracts.ContractsFeature
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.data.tips.SavingTipsOverview
import org.totschnig.myexpenses.next.feature.tips.SavingTipCard
import org.totschnig.myexpenses.next.feature.tips.SavingTipsFlow
import org.totschnig.myexpenses.next.feature.tips.SavingTipsViewModel
import org.totschnig.myexpenses.next.tags.TagsFeature
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * Saving tips from the ledger and the facts of the other areas
 *
 * @param contracts the contract of a payment in the details of a tip
 * @param tags whether a payment is one-off, asked in the details of an outlier
 * @param footer below the tips, e.g. those that wait for more data
 */
class SavingTipsFeature(
    activity: ComponentActivity,
    private val data: NextData,
    private val contracts: ContractsFeature,
    private val tags: TagsFeature,
    private val footer: @Composable (AnalysisState<SavingTipsOverview>) -> Unit = {},
) : NextFeature {
    val viewModel by activity.viewModelsOf { SavingTipsViewModel(data.savingTips) }

    @Composable
    override fun OverviewCard(navigation: NextNavigation) {
        val homeCurrency by data.homeCurrency.collectAsStateWithLifecycle()
        SavingTipCard(
            state = viewModel.savingTips.collectAsStateWithLifecycle().value,
            currency = homeCurrency,
            onOpen = { navigation.show(NextKey.SavingTips) },
            modifier = Modifier
        )
    }

    override val tab = NextTab(Icons.Default.Percent, R.string.next_tab_saving_tips, NextKey.SavingTips)

    /** Saving tips the user has not seen yet */
    @Composable
    override fun hasNews() = viewModel.savingTips.collectAsStateWithLifecycle().value.valueOrNull?.newIds?.isNotEmpty() == true

    override fun EntryProviderScope<NavKey>.pages(shell: NextShell) {
        entry<NextKey.SavingTips> {
            BehindStatusBar(MaterialTheme.colorScheme.surfaceContainer) {
                val homeCurrency by data.homeCurrency.collectAsStateWithLifecycle()
                SavingTipsFlow(
                    viewModel, shell.fixedTransactions, homeCurrency,
                    onOpenContract = { signature -> shell.navigation.show(NextKey.Contracts(signature)) },
                    onInsightSettings = shell.openInsightSettings,
                    transactionDetails = { transaction, isOutlier ->
                        contracts.TransactionSections(transaction)
                        if (isOutlier) tags.OneOff(transaction)
                        ActionChipRow { contracts.TransactionChips(transaction) }
                    },
                    footer = { footer(viewModel.savingTips.collectAsStateWithLifecycle().value) }
                )
            }
        }
    }
}
