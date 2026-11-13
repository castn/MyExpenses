package org.totschnig.myexpenses.next.setup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.NextData
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.settings.AreaSettings
import org.totschnig.myexpenses.next.settings.InsightSetting
import org.totschnig.myexpenses.next.settings.SettingControl
import org.totschnig.myexpenses.next.settings.SettingKind
import org.totschnig.myexpenses.next.settings.SettingsGroup

/**
 * The setup in the settings: resume it where the user left it, also after "Stop reminding", which
 * leaves the settings for the overview with its card, or start it again from the beginning
 */
class SetupSettings(private val activity: ComponentActivity) : AreaSettings {
    private val repository = NextData.of(activity).setup

    /** Steps done of the steps there are */
    private val stepsDone = combine(repository.steps, repository.progress) { steps, progress ->
        steps.count { it in progress.finished } to steps.size
    }

    @Composable
    override fun control(setting: InsightSetting): SettingControl? = when (setting) {
        RESUME -> {
            val steps by stepsDone.collectAsState(null)
            val (done, total) = steps ?: (0 to 0)
            SettingControl.Action(
                value = stringResource(R.string.next_settings_setup_steps, done, total),
                isEnabled = done < total,
                onClick = {
                    activity.lifecycleScope.launch {
                        repository.store.resume()
                        activity.finish()
                    }
                }
            )
        }

        RESTART -> SettingControl.Action(
            value = stringResource(R.string.next_settings_setup_restart_value),
            isEnabled = true,
            onClick = {
                activity.lifecycleScope.launch {
                    repository.store.reset()
                    activity.finish()
                }
            }
        )

        else -> null
    }

    companion object : AreaSettings.Factory {
        val RESUME = InsightSetting(
            "SETUP_RESUME",
            SettingsGroup.INSIGHTS,
            SettingKind.ACTION,
            R.string.next_settings_setup_resume
        )

        val RESTART = InsightSetting(
            "SETUP_RESTART",
            SettingsGroup.INSIGHTS,
            SettingKind.ACTION,
            R.string.next_settings_setup_restart
        )

        override val settings = listOf(RESUME, RESTART)

        override fun create(activity: ComponentActivity) = SetupSettings(activity)
    }
}
