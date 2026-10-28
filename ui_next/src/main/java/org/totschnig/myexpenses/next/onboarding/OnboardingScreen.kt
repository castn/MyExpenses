package org.totschnig.myexpenses.next.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.totschnig.myexpenses.designsystem.FeatureRow
import org.totschnig.myexpenses.designsystem.R as DesignR
import org.totschnig.myexpenses.designsystem.HeroIllustration
import org.totschnig.myexpenses.designsystem.IntroBottomBar
import org.totschnig.myexpenses.designsystem.IntroPage
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.StepIndicator
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R as NextR

/** The pages of the first start, in their order */
internal enum class OnboardingStep { WELCOME, PRIVACY, START, ACCOUNT, DONE }

/**
 * The pages of the first start, one after the other. Only the buttons move between them, so that
 * nothing is left out by swiping.
 *
 * @param privacyChoices null while they are read
 * @param offersBanking false where the app cannot connect to banks
 * @param onRestore brings data the user already has instead of a new account
 * @param initialDraft the first account as suggested, before the user changes it
 * @param isStarting true while the first account is created
 * @param onStart creates the first account and opens the app
 */
@Composable
internal fun OnboardingScreen(
    privacyChoices: PrivacyChoices?,
    onPrivacyChange: (PrivacySetting, Boolean) -> Unit,
    offersBanking: Boolean,
    onRestore: (StartChoice) -> Unit,
    initialDraft: AccountDraft,
    accountChoices: AccountChoices,
    isStarting: Boolean,
    onStart: (AccountDraft) -> Unit,
) {
    val steps = OnboardingStep.entries
    var current by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var startChoice by rememberSaveable { mutableStateOf(StartChoice.NEW_ACCOUNT) }
    var draft by rememberSaveable(stateSaver = AccountDraft.Saver) { mutableStateOf(initialDraft) }
    val pagerState = rememberPagerState(initialPage = current.ordinal) { steps.size }
    LaunchedEffect(current) { pagerState.animateScrollToPage(current.ordinal) }
    BackHandler(enabled = current.ordinal > 0) { current = steps[current.ordinal - 1] }

    Surface(color = MaterialTheme.colorScheme.surface) {
        // The keyboard pushes the bar up, so that the fields of a page and going on stay visible
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            StepIndicator(
                count = steps.size,
                current = current.ordinal,
                description = stringResource(NextR.string.next_onboarding_step, current.ordinal + 1, steps.size),
                modifier = Modifier.padding(Spacing.screen)
            )
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (steps[page]) {
                    OnboardingStep.WELCOME -> WelcomePage()
                    OnboardingStep.PRIVACY -> privacyChoices?.let { PrivacyPage(it, onPrivacyChange) }
                    OnboardingStep.START -> StartPage(startChoice, offersBanking, onSelect = { startChoice = it })
                    OnboardingStep.ACCOUNT -> AccountPage(draft, accountChoices, onDraftChange = { draft = it })
                    OnboardingStep.DONE -> DonePage()
                }
            }
            when (current) {
                // How to start must be decided, everything before it can be left as it is
                OnboardingStep.WELCOME, OnboardingStep.PRIVACY -> IntroBottomBar(
                    nextLabel = stringResource(NextR.string.next_onboarding_next),
                    onNext = { current = steps[current.ordinal + 1] },
                    skipLabel = stringResource(NextR.string.next_onboarding_skip),
                    onSkip = { current = OnboardingStep.START }
                )

                // Existing data opens the app once restored, without the pages of a new account
                OnboardingStep.START -> IntroBottomBar(
                    nextLabel = stringResource(NextR.string.next_onboarding_next),
                    onNext = {
                        if (startChoice == StartChoice.NEW_ACCOUNT) current = OnboardingStep.ACCOUNT
                        else onRestore(startChoice)
                    }
                )

                OnboardingStep.ACCOUNT -> IntroBottomBar(
                    nextLabel = stringResource(NextR.string.next_onboarding_next),
                    onNext = { current = OnboardingStep.DONE },
                    nextEnabled = draft.openingBalance != null
                )

                OnboardingStep.DONE -> IntroBottomBar(
                    nextLabel = stringResource(NextR.string.next_onboarding_start),
                    onNext = { onStart(draft) },
                    nextEnabled = !isStarting
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    IntroPage(
        title = stringResource(NextR.string.next_onboarding_welcome_title),
        text = stringResource(NextR.string.next_onboarding_welcome_text),
        hero = { HeroIllustration(DesignR.drawable.illustration_welcome) }
    ) {
        FeatureRow(
            Icons.Outlined.PieChart,
            stringResource(NextR.string.next_onboarding_feature_overview),
            stringResource(NextR.string.next_onboarding_feature_overview_text)
        )
        FeatureRow(
            Icons.Outlined.EventRepeat,
            stringResource(NextR.string.next_onboarding_feature_contracts),
            stringResource(NextR.string.next_onboarding_feature_contracts_text)
        )
        FeatureRow(
            Icons.Outlined.DocumentScanner,
            stringResource(NextR.string.next_onboarding_feature_receipts),
            stringResource(NextR.string.next_onboarding_feature_receipts_text)
        )
    }
}

@Composable
private fun DonePage() {
    IntroPage(
        title = stringResource(NextR.string.next_onboarding_done_title),
        text = stringResource(NextR.string.next_onboarding_done_text),
        hero = { HeroIllustration(DesignR.drawable.illustration_done) }
    )
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun OnboardingScreenPreview() {
    NextPreviewTheme {
        OnboardingScreen(
            privacyChoices = null,
            onPrivacyChange = { _, _ -> },
            offersBanking = false,
            onRestore = {},
            initialDraft = AccountDraft("Haushaltsbuch", "EUR", ""),
            accountChoices = AccountChoices(emptyList()),
            isStarting = false,
            onStart = {}
        )
    }
}
