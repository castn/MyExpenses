package org.totschnig.myexpenses.next.analysis

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.totschnig.myexpenses.next.BehindStatusBar
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextKey
import org.totschnig.myexpenses.next.NextShell
import org.totschnig.myexpenses.next.NextTab
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.feature.analysis.AnalysisFlow
import org.totschnig.myexpenses.next.feature.analysis.CategorySpendingViewModel
import org.totschnig.myexpenses.next.feature.analysis.MoneyFlowViewModel
import org.totschnig.myexpenses.next.feature.analysis.TagAnalysisViewModel
import org.totschnig.myexpenses.next.guidance.GuidanceFeature
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * The tab with the analyses: tags, spending per category and the flow of money
 *
 * @param guidance what the data is ready for, shown above the analyses
 */
class AnalysisFeature(
    activity: ComponentActivity,
    private val data: NextData,
    private val guidance: GuidanceFeature,
) : NextFeature {
    val tags by activity.viewModelsOf { TagAnalysisViewModel(data.ledgerSource, data.tags.facts) }
    val categorySpending by activity.viewModelsOf { CategorySpendingViewModel(data.ledgerSource, data.tags.facts) }
    val moneyFlow by activity.viewModelsOf { MoneyFlowViewModel(data.ledgerSource, data.tags.facts) }

    override val tab = NextTab(Icons.Default.Insights, R.string.next_tab_analysis, NextKey.Analysis)

    override fun EntryProviderScope<NavKey>.pages(shell: NextShell) {
        entry<NextKey.Analysis> {
            BehindStatusBar(MaterialTheme.colorScheme.surfaceContainer) {
                val homeCurrency by data.homeCurrency.collectAsStateWithLifecycle()
                AnalysisFlow(
                    tags, categorySpending, moneyFlow, shell.fixedTransactions,
                    homeCurrency,
                    onInsightSettings = shell.openInsightSettings,
                    readiness = guidance.readiness.readiness,
                    transactionDetails = shell.transactionDetails
                )
            }
        }
    }
}
