package org.totschnig.myexpenses.next.feature.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.ActionCard
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.GuideProgressHeader
import org.totschnig.myexpenses.designsystem.IntroBottomBar
import org.totschnig.myexpenses.designsystem.LastDecisionRow
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.rememberPercentFormat
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.data.setup.TARGET_SHARE

/**
 * The categories step: first the question about the transactions that can be categorized for sure,
 * then one payee after the other up to the target. The answer is chosen on the card and confirmed
 * with "Next" in a bar that stays at the bottom, below the line that tells what was decided last.
 *
 * @param onShowTransactions shows the transactions of a payee, if the app can
 * @param onDiffers shows the transactions of a payee to give them different categories
 */
@Composable
internal fun SetupCategoriesPage(
    viewModel: SetupViewModel,
    currency: CurrencyInfo,
    onBack: () -> Unit,
    onShowTransactions: ((Counterpart) -> Unit)?,
    onDiffers: (Counterpart) -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val step by viewModel.categories.collectAsStateWithLifecycle()
    val always by viewModel.always.collectAsStateWithLifecycle()
    val current = step
    val card = current?.card?.takeIf { current.backlog == 0 }
    Column(Modifier.fillMaxSize()) {
        DetailScaffold(
            onBack = onBack,
            title = stringResource(R.string.next_setup_step_categories),
            header = current?.let { { CoverageHeader(it) } },
            modifier = Modifier.weight(1f)
        ) {
            if (current == null) return@DetailScaffold
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.content)) {
                current.last?.takeIf { current.backlog == 0 }?.let { last ->
                    LastDecisionRow(
                        text = lastDecisionText(last),
                        actionLabel = stringResource(R.string.next_setup_change),
                        onAction = viewModel::back
                    )
                }
                StepContent(
                    current,
                    card,
                    always,
                    currency,
                    viewModel,
                    onShowTransactions,
                    onSkip,
                    onFinish
                )
                val isDeciding = card != null || current.cashCard != null || current.backlog > 0
                if (!current.isTargetReached && isDeciding) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.next_setup_skip_step))
                    }
                }
            }
        }
        if (card != null) {
            IntroBottomBar(
                nextLabel = stringResource(R.string.next_setup_next),
                onNext = {
                    if (card.answer == PayeeAnswer.DIFFERS) {
                        onDiffers(card.payee.counterpart)
                    } else {
                        viewModel.confirm(card)
                    }
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
                nextEnabled = card.canConfirm,
                skipLabel = stringResource(R.string.next_setup_later),
                onSkip = { viewModel.later(card) }
            )
        }
    }
}

@Composable
private fun CoverageHeader(step: CategoriesStep) {
    val percent = rememberPercentFormat()
    GuideProgressHeader(
        title = stringResource(R.string.next_setup_coverage),
        value = percent(step.share),
        progress = step.share.toFloat(),
        target = TARGET_SHARE.toFloat(),
        supportingText = if (step.left > 0) {
            stringResource(
                R.string.next_setup_step_text,
                pluralStringResource(R.plurals.next_setup_payees_left, step.left, step.left),
                minutesText(step.seconds)
            )
        } else {
            null
        }
    )
}

/** The backlog question, the card of a payee or the cash withdrawals, or what comes after them */
@Composable
private fun StepContent(
    current: CategoriesStep,
    card: PayeeCard?,
    always: Boolean,
    currency: CurrencyInfo,
    viewModel: SetupViewModel,
    onShowTransactions: ((Counterpart) -> Unit)?,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    when {
        current.backlog > 0 -> BacklogQuestion(current.backlog, viewModel)

        // A new payee slides in, so that the user sees that the last one was decided on
        card != null -> AnimatedContent(
            targetState = card,
            contentKey = { it.key },
            transitionSpec = {
                slideInHorizontally { it / SLIDE_PART } + fadeIn() togetherWith
                    fadeOut()
            },
            label = "payee card"
        ) { shown ->
            PayeeDecision(shown, always, currency, viewModel, onShowTransactions)
        }

        current.cashCard != null ->
            CashDecision(current.cashCard, currency, viewModel, viewModel::picked)

        current.isTargetReached || !current.hasSkipped -> ActionCard(
            icon = Icons.Outlined.CheckCircle,
            title = stringResource(
                if (current.isTargetReached) {
                    R.string.next_setup_target_title
                } else {
                    R.string.next_setup_no_more_title
                }
            ),
            text = stringResource(
                if (current.isTargetReached) {
                    R.string.next_setup_target_text
                } else {
                    R.string.next_setup_no_more_text
                }
            ),
            confirmLabel = stringResource(R.string.next_setup_next),
            onConfirm = onFinish,
            dismissLabel = stringResource(R.string.next_setup_go_on)
                .takeIf { current.hasMore || current.hasSkipped },
            onDismiss = viewModel::goOn
        )

        else -> ActionCard(
            icon = Icons.Outlined.Schedule,
            title = stringResource(R.string.next_setup_skipped_title),
            text = stringResource(R.string.next_setup_skipped_text),
            confirmLabel = stringResource(R.string.next_setup_show_again),
            onConfirm = viewModel::goOn,
            dismissLabel = stringResource(R.string.next_setup_skip_step),
            onDismiss = onSkip
        )
    }
}

@Composable
private fun BacklogQuestion(count: Int, actions: SetupActions) {
    Banner(
        title = stringResource(R.string.next_category_backlog_title),
        messages = listOf(pluralStringResource(R.plurals.next_setup_backlog_text, count, count))
    ) {
        TextButton(onClick = { actions.answerBacklog(accept = false) }) {
            Text(stringResource(R.string.next_reject))
        }
        Button(
            onClick = { actions.answerBacklog(accept = true) },
            modifier = Modifier.padding(start = Spacing.small)
        ) {
            Text(stringResource(R.string.next_category_accept))
        }
    }
}

/** A new card slides in from this part of its width */
private const val SLIDE_PART = 3
