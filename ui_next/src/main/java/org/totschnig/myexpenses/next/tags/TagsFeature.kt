package org.totschnig.myexpenses.next.tags

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.settings.AreaSettings
import org.totschnig.myexpenses.next.settings.InsightSetting
import org.totschnig.myexpenses.next.settings.RulesPage
import org.totschnig.myexpenses.next.settings.SettingControl
import org.totschnig.myexpenses.next.settings.SettingKind
import org.totschnig.myexpenses.next.settings.SettingsGroup
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextNavigation
import org.totschnig.myexpenses.next.RowDecorations
import org.totschnig.myexpenses.next.transactions.toShown
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.tags.TagStore
import org.totschnig.myexpenses.next.feature.tags.OneOffSection
import org.totschnig.myexpenses.next.feature.tags.RefundSection
import org.totschnig.myexpenses.next.feature.tags.TagDetectionsViewModel
import org.totschnig.myexpenses.next.feature.tags.TagLearningViewModel
import org.totschnig.myexpenses.next.feature.tags.TagSuggestionSection
import org.totschnig.myexpenses.next.feature.tags.TagChips
import org.totschnig.myexpenses.next.feature.tags.TagsViewModel
import org.totschnig.myexpenses.next.feature.tags.TripBanner
import org.totschnig.myexpenses.next.viewModelsOf

/** Tags: their meanings, suggestions learned from the user, trips and refunds */
class TagsFeature(private val activity: ComponentActivity, private val data: NextData) : NextFeature {
    private val database get() = RepositoryTagDatabase(activity.injector.repository())

    val viewModel by activity.viewModelsOf { TagsViewModel(data.tags, database) }
    val learning by activity.viewModelsOf { TagLearningViewModel(data.tags, database) }
    val detections by activity.viewModelsOf {
        TagDetectionsViewModel(data.ledgerSource, data.tags.rows, data.contracts.facts, TagStore(data.dataStore), database)
    }

    @Composable
    override fun TransactionSections(transaction: ShownTransaction) {
        RefundSection(detections, transaction)
        TagSuggestionSection(learning, viewModel, transaction)
    }

    @Composable
    override fun OverviewBanner(navigation: NextNavigation) {
        val homeCurrency by data.homeCurrency.collectAsStateWithLifecycle()
        TripBanner(detections, homeCurrency)
    }

    /** Whether a payment is one-off, asked e.g. in the details of an outlier of the saving tips */
    @Composable
    fun OneOff(transaction: ShownTransaction) {
        OneOffSection(viewModel, transaction)
    }

    @Composable
    override fun rowDecorations(): RowDecorations {
        val meanings by viewModel.meanings.collectAsStateWithLifecycle()
        return RowDecorations(
            tagChips = { transaction -> { modifier -> TagChips(viewModel, transaction.toShown(), modifier) } },
            isMarker = { meanings[it]?.isMarker == true }
        )
    }
}

/** The rules of tags in the settings, and what the user said no to, also of categories */
class TagSettings(activity: ComponentActivity) : AreaSettings {
    private val viewModel by activity.viewModelsOf {
        val dataStore = activity.injector.preferencesDataStore()
        TagSettingsViewModel(TagStore(dataStore), CategoryStore(dataStore), activity.injector.repository())
    }

    private var isResetRejectedShown by mutableStateOf(false)

    @Composable
    override fun control(setting: InsightSetting): SettingControl? = when (setting) {
        RULES -> {
            val rules = viewModel.rules.collectAsState().value
            SettingControl.Page(
                value = rules?.let { pluralStringResource(R.plurals.next_settings_rules, it.size, it.size) },
                isEnabled = true,
                setting = setting
            )
        }
        REJECTED -> {
            val count = viewModel.rejectedCount.collectAsState().value
            SettingControl.Action(
                value = pluralStringResource(R.plurals.next_settings_rejected, count, count),
                isEnabled = count > 0,
                onClick = { isResetRejectedShown = true }
            )
        }
        else -> null
    }

    @Composable
    override fun Page(setting: InsightSetting, onBack: () -> Unit) {
        RulesPage(
            title = stringResource(setting.title),
            hint = stringResource(R.string.next_settings_tag_rules_hint),
            rules = viewModel.rules.collectAsState().value,
            onBack = onBack
        )
    }

    @Composable
    override fun Dialogs() {
        if (isResetRejectedShown) {
            ConfirmDialog(
                title = stringResource(R.string.next_settings_rejected_suggestions),
                text = stringResource(R.string.next_settings_rejected_suggestions_confirm),
                confirmLabel = stringResource(R.string.next_settings_reset),
                onConfirm = { isResetRejectedShown = false; viewModel.resetRejected() },
                onDismiss = { isResetRejectedShown = false }
            )
        }
    }

    companion object : AreaSettings.Factory {
        val RULES = InsightSetting("TAG_RULES", SettingsGroup.INSIGHTS, SettingKind.ACTION, R.string.next_settings_tag_rules)
        val REJECTED = InsightSetting(
            "REJECTED_SUGGESTIONS", SettingsGroup.INSIGHTS, SettingKind.ACTION, R.string.next_settings_rejected_suggestions
        )

        override val settings = listOf(RULES, REJECTED)

        override fun create(activity: ComponentActivity) = TagSettings(activity)
    }
}
