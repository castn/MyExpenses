package org.totschnig.myexpenses.next.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.HeroIllustration
import org.totschnig.myexpenses.designsystem.R as DesignR
import org.totschnig.myexpenses.designsystem.IntroPage
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.SwitchRow
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.settings.AreaSettings
import org.totschnig.myexpenses.next.settings.InsightSetting
import org.totschnig.myexpenses.next.settings.SettingControl
import org.totschnig.myexpenses.next.core.common.R as NextR

/** What the user decides about their data on the first start */
enum class PrivacySetting { CRASH_REPORTS, TRACKING, AUTO_BACKUP, ENCRYPTION }

/**
 * The choices on the page about privacy, as stored
 *
 * @param offersReporting false in distributions that never send crash reports or usage data
 * @param autoBackupTime when the automatic backup runs, e.g. "03:00"
 * @param areaSettings what the areas ask on the first start, after the data sent by the app
 */
@Immutable
data class PrivacyChoices(
    val offersReporting: Boolean,
    val autoBackupTime: String,
    val checked: Set<PrivacySetting>,
    val areaSettings: List<FirstStartSetting> = emptyList(),
)

/** A switch of an area on the page about privacy, see [AreaSettings.Factory.onFirstStart] */
data class FirstStartSetting(val setting: InsightSetting, val area: AreaSettings)

@Composable
internal fun PrivacyPage(choices: PrivacyChoices, onChange: (PrivacySetting, Boolean) -> Unit) {
    IntroPage(
        title = stringResource(NextR.string.next_onboarding_privacy_title),
        text = stringResource(NextR.string.next_onboarding_privacy_text),
        hero = { HeroIllustration(DesignR.drawable.illustration_privacy) }
    ) {
        @Composable
        fun Switch(setting: PrivacySetting, label: String, supportingText: String, isFirst: Boolean = false) =
            SwitchRow(
                label,
                checked = setting in choices.checked,
                onCheckedChange = { onChange(setting, it) },
                supportingText = supportingText,
                isFirst = isFirst
            )

        if (choices.offersReporting || choices.areaSettings.isNotEmpty()) {
            SectionTitle(stringResource(NextR.string.next_onboarding_privacy_sent))
            DetailCard {
                if (choices.offersReporting) {
                    Switch(
                        PrivacySetting.CRASH_REPORTS,
                        stringResource(NextR.string.next_onboarding_crash_reports),
                        stringResource(NextR.string.next_onboarding_crash_reports_text),
                        isFirst = true
                    )
                    Switch(
                        PrivacySetting.TRACKING,
                        stringResource(NextR.string.next_onboarding_tracking),
                        stringResource(NextR.string.next_onboarding_tracking_text)
                    )
                }
                choices.areaSettings.forEachIndexed { index, (setting, area) ->
                    val control = area.control(setting) as? SettingControl.Switch ?: return@forEachIndexed
                    SwitchRow(
                        stringResource(setting.title),
                        checked = control.checked,
                        onCheckedChange = control.onChange,
                        supportingText = control.summary,
                        isFirst = !choices.offersReporting && index == 0
                    )
                }
            }
        }
        SectionTitle(stringResource(NextR.string.next_onboarding_privacy_protection))
        DetailCard {
            Switch(
                PrivacySetting.AUTO_BACKUP,
                stringResource(NextR.string.next_onboarding_auto_backup),
                stringResource(NextR.string.next_onboarding_auto_backup_text, choices.autoBackupTime),
                isFirst = true
            )
            Switch(
                PrivacySetting.ENCRYPTION,
                stringResource(NextR.string.next_onboarding_encryption),
                stringResource(NextR.string.next_onboarding_encryption_text)
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun PrivacyPagePreview() {
    NextPreviewTheme {
        PrivacyPage(
            PrivacyChoices(
                offersReporting = true,
                autoBackupTime = "03:00",
                checked = setOf(PrivacySetting.CRASH_REPORTS, PrivacySetting.TRACKING)
            ),
            onChange = { _, _ -> }
        )
    }
}
