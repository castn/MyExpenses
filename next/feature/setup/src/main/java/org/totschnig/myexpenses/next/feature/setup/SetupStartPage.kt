package org.totschnig.myexpenses.next.feature.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.GuideStepRow
import org.totschnig.myexpenses.designsystem.GuideStepStatus
import org.totschnig.myexpenses.designsystem.HeroIllustration
import org.totschnig.myexpenses.designsystem.IntroBottomBar
import org.totschnig.myexpenses.designsystem.IntroPage
import org.totschnig.myexpenses.designsystem.R as DesignR
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.setup.SetupStep

/**
 * The overview of the setup: what it brings, its steps with what they take, and how to go on
 *
 * @param overview null while loading
 */
@Composable
internal fun SetupStartPage(
    overview: SetupOverview?,
    onStart: () -> Unit,
    onSkipAll: () -> Unit,
    onBack: () -> Unit
) {
    IntroColumn(onBack) {
        IntroPage(
            title = stringResource(R.string.next_setup_title),
            text = stringResource(R.string.next_setup_intro),
            modifier = Modifier.weight(1f),
            hero = { HeroIllustration(DesignR.drawable.illustration_start) }
        ) {
            if (overview != null) {
                DetailCard {
                    overview.steps.forEachIndexed { index, info ->
                        val onClick = onStart.takeIf { info.step == overview.next }
                        StepRow(index, info, onClick)
                    }
                }
            }
        }
        IntroBottomBar(
            nextLabel = stringResource(
                if (overview?.hasStarted == true) {
                    R.string.next_setup_go_on
                } else {
                    R.string.next_setup_start
                }
            ),
            onNext = onStart,
            nextEnabled = overview != null,
            skipLabel = stringResource(R.string.next_setup_skip_all),
            onSkip = onSkipAll
        )
    }
}

@Composable
private fun StepRow(index: Int, info: StepInfo, onClick: (() -> Unit)?) {
    val title = when (info.step) {
        SetupStep.INCOME -> R.string.next_setup_step_income
        SetupStep.CATEGORIES -> R.string.next_setup_step_categories
        SetupStep.CONTRACTS -> R.string.next_setup_step_contracts
    }
    val decisions = when (info.step) {
        SetupStep.INCOME -> R.plurals.next_setup_decisions
        SetupStep.CATEGORIES -> R.plurals.next_setup_payees
        SetupStep.CONTRACTS -> R.plurals.next_setup_contracts
    }
    val text = if (info.decisions == 0) {
        stringResource(R.string.next_setup_nothing_to_do)
    } else {
        stringResource(
            R.string.next_setup_step_text,
            pluralStringResource(decisions, info.decisions, info.decisions),
            minutesText(info.seconds)
        )
    }
    GuideStepRow(
        number = index + 1,
        title = stringResource(title),
        text = text,
        status = when (info.state) {
            StepState.OPEN -> GuideStepStatus.OPEN
            StepState.CURRENT -> GuideStepStatus.CURRENT
            StepState.DONE -> GuideStepStatus.DONE
            StepState.SKIPPED -> GuideStepStatus.SKIPPED
        },
        isFirst = index == 0,
        statusLabel = when (info.state) {
            StepState.DONE -> stringResource(R.string.next_setup_state_done)
            StepState.SKIPPED -> stringResource(R.string.next_setup_state_skipped)
            else -> null
        },
        onClick = onClick
    )
}

/** "about 3 min.", at least one minute */
@Composable
internal fun minutesText(seconds: Long): String {
    val minutes = ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).coerceAtLeast(1).toInt()
    return pluralStringResource(R.plurals.next_setup_minutes, minutes, minutes)
}

private const val SECONDS_PER_MINUTE = 60L
