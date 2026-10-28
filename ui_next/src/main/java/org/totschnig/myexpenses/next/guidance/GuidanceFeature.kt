package org.totschnig.myexpenses.next.guidance

import androidx.activity.ComponentActivity
import com.google.android.material.snackbar.Snackbar
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.next.feature.guidance.afterSaveText
import org.totschnig.myexpenses.ui.SnackbarAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.pluralStringResource
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.settings.AreaSettings
import org.totschnig.myexpenses.next.settings.InsightSetting
import org.totschnig.myexpenses.next.settings.SettingControl
import org.totschnig.myexpenses.next.settings.SettingKind
import org.totschnig.myexpenses.next.settings.SettingsGroup
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextKey
import org.totschnig.myexpenses.next.NextNavigation
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.guidance.HintStore
import org.totschnig.myexpenses.next.feature.guidance.DataReadinessViewModel
import org.totschnig.myexpenses.next.feature.guidance.HintsViewModel
import org.totschnig.myexpenses.next.feature.guidance.OverviewHint
import org.totschnig.myexpenses.next.feature.guidance.PendingTipsSection
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.data.tips.SavingTipsOverview
import org.totschnig.myexpenses.next.feature.guidance.ReceiptHint
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * Hints after saving, on the overview and in the details, and what the data is ready for
 *
 * @param isAskingAboutCategories whether the overview already asks about categories, then it shows
 * no hint, one question at a time
 * @param explained the page that explains the hints of the overview, e.g. the analysis with what
 * the data is ready for
 */
class GuidanceFeature(
    private val activity: BaseActivity,
    data: NextData,
    private val isAskingAboutCategories: @Composable () -> Boolean = { false },
    private val explained: NextKey? = null,
) : NextFeature {
    val hints by activity.viewModelsOf {
        HintsViewModel(data.ledgerSource, data.receiptFacts, data.readiness.readiness, HintStore(data.dataStore))
    }
    val readiness by activity.viewModelsOf { DataReadinessViewModel(data.readiness) }

    @Composable
    override fun TransactionSections(transaction: ShownTransaction) {
        ReceiptHint(hints, transaction)
    }

    @Composable
    override fun OverviewBanner(navigation: NextNavigation) {
        if (!isAskingAboutCategories()) {
            OverviewHint(hints, onOpen = explained?.let { key -> { navigation.show(key) } })
        }
    }

    /** The saving tips that wait for more data, below the saving tips */
    @Composable
    fun PendingTips(tips: AnalysisState<SavingTipsOverview>) {
        PendingTipsSection(readiness, tips)
    }

    /** A hint after saving, e.g. that a receipt can be scanned, until the user closes it for good */
    override fun onTransactionSaved(transactionId: Long) {
        hints.afterSave(transactionId) { topic ->
            activity.showSnackBar(
                activity.getString(topic.afterSaveText()),
                Snackbar.LENGTH_LONG,
                SnackbarAction(activity.getString(R.string.next_hint_never)) { hints.dismiss(topic) }
            )
        }
    }
}

/** The hints the user closed, in the settings */
class HintSettings(activity: ComponentActivity) : AreaSettings {
    private val viewModel by activity.viewModelsOf { HintSettingsViewModel(HintStore(activity.injector.preferencesDataStore())) }

    @Composable
    override fun control(setting: InsightSetting): SettingControl? {
        if (setting != CLOSED) return null
        val count = viewModel.closedHints.collectAsState().value
        return SettingControl.Action(
            value = pluralStringResource(R.plurals.next_settings_closed_hints_count, count, count),
            isEnabled = count > 0,
            onClick = viewModel::resetHints
        )
    }

    companion object : AreaSettings.Factory {
        val CLOSED = InsightSetting("CLOSED_HINTS", SettingsGroup.INSIGHTS, SettingKind.ACTION, R.string.next_settings_closed_hints)

        override val settings = listOf(CLOSED)

        override fun create(activity: ComponentActivity) = HintSettings(activity)
    }
}
