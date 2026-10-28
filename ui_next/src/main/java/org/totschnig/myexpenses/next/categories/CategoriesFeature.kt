package org.totschnig.myexpenses.next.categories

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.totschnig.myexpenses.injector
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
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.NextFeature
import org.totschnig.myexpenses.next.NextNavigation
import org.totschnig.myexpenses.next.RowDecorations
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.feature.categories.CategoryBacklogBanner
import org.totschnig.myexpenses.next.feature.categories.CategoryChip
import org.totschnig.myexpenses.next.feature.categories.LocalCategoryPicker
import org.totschnig.myexpenses.next.feature.categories.hasCategoryChoice
import org.totschnig.myexpenses.next.transactions.toShown
import org.totschnig.myexpenses.next.feature.categories.CategorySuggestionSection
import org.totschnig.myexpenses.next.feature.categories.CategorySuggestionsViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/** Categories: suggestions for transactions without one */
class CategoriesFeature(activity: ComponentActivity, data: NextData) : NextFeature {
    val viewModel by activity.viewModelsOf { CategorySuggestionsViewModel(data.categories) }

    /** Whether the overview asks whether to categorize transactions automatically */
    @Composable
    fun isAsking(): Boolean = viewModel.backlog.collectAsStateWithLifecycle().value != null

    @Composable
    override fun TransactionSections(transaction: ShownTransaction) {
        CategorySuggestionSection(viewModel, transaction)
    }

    @Composable
    override fun OverviewBanner(navigation: NextNavigation) {
        CategoryBacklogBanner(viewModel)
    }

    @Composable
    override fun rowDecorations(): RowDecorations {
        val suggestedPaths by viewModel.suggestedPaths.collectAsStateWithLifecycle()
        return RowDecorations(
            suggestedCategory = suggestedPaths::get,
            categoryChip = { transaction ->
                val shown = transaction.toShown()
                if (shown.hasCategoryChoice()) {
                    { modifier -> CategoryChip(viewModel, shown, modifier) }
                } else null
            }
        )
    }

    companion object {
        /** Lets screens of all areas pick a category, also outside of the main screen */
        val picker = LocalCategoryPicker provides ManageCategoriesPicker
    }
}

/** The rules of categories in the settings */
class CategorySettings(activity: ComponentActivity) : AreaSettings {
    private val viewModel by activity.viewModelsOf {
        CategoryRulesViewModel(CategoryStore(activity.injector.preferencesDataStore()), activity.injector.repository())
    }

    @Composable
    override fun control(setting: InsightSetting): SettingControl? {
        if (setting != RULES) return null
        val rules = viewModel.rules.collectAsState().value
        return SettingControl.Page(
            value = rules?.let { pluralStringResource(R.plurals.next_settings_rules, it.size, it.size) },
            isEnabled = true,
            setting = setting
        )
    }

    @Composable
    override fun Page(setting: InsightSetting, onBack: () -> Unit) {
        RulesPage(
            title = stringResource(setting.title),
            hint = stringResource(R.string.next_settings_category_rules_hint),
            rules = viewModel.rules.collectAsState().value,
            onBack = onBack
        )
    }

    companion object : AreaSettings.Factory {
        val RULES = InsightSetting("CATEGORY_RULES", SettingsGroup.INSIGHTS, SettingKind.ACTION, R.string.next_settings_category_rules)

        override val settings = listOf(RULES)

        override fun create(activity: ComponentActivity) = CategorySettings(activity)
    }
}
