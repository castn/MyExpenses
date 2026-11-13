package org.totschnig.myexpenses.next.feature.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.FormatStyle
import org.totschnig.myexpenses.designsystem.ActionCard
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.ChipRow
import org.totschnig.myexpenses.designsystem.DecisionCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.EntryPreview
import org.totschnig.myexpenses.designsystem.GuideProgressHeader
import org.totschnig.myexpenses.designsystem.LabelChip
import org.totschnig.myexpenses.designsystem.PayeeTile
import org.totschnig.myexpenses.designsystem.PreviewEntry
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.ToggleChip
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.categories.LocalCategoryPicker
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.core.ui.rememberPercentFormat
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.data.setup.TARGET_SHARE

/**
 * The categories step: first the question about the transactions that can be categorized for sure,
 * then one payee after the other up to the target, with what helps to decide
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
    val percent = rememberPercentFormat()
    val current = step
    DetailScaffold(
        onBack = onBack,
        title = stringResource(R.string.next_setup_step_categories),
        header = current?.let {
            {
                GuideProgressHeader(
                    title = stringResource(R.string.next_setup_coverage),
                    value = percent(it.share),
                    progress = it.share.toFloat(),
                    target = TARGET_SHARE.toFloat(),
                    supportingText = if (it.left > 0) {
                        stringResource(
                            R.string.next_setup_step_text,
                            pluralStringResource(
                                R.plurals.next_setup_payees_left,
                                it.left,
                                it.left
                            ),
                            minutesText(it.seconds)
                        )
                    } else {
                        null
                    }
                )
            }
        }
    ) {
        if (current == null) return@DetailScaffold
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.content)) {
            val card = current.card
            if (current.canGoBack && current.backlog == 0) {
                TextButton(
                    onClick = viewModel::back,
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.next_setup_back))
                }
            }
            when {
                current.backlog > 0 -> BacklogQuestion(current.backlog, viewModel)

                card != null -> PayeeDecision(
                    card,
                    always,
                    currency,
                    viewModel,
                    onShowTransactions,
                    onDiffers
                )

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
            val isDeciding = card != null || current.cashCard != null || current.backlog > 0
            if (!current.isTargetReached && isDeciding) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.next_setup_skip_step))
                }
            }
        }
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

/** One payee with its last transactions and a suggestion, see [SetupActions] for what the user can do */
@Composable
private fun PayeeDecision(
    card: PayeeCard,
    always: Boolean,
    currency: CurrencyInfo,
    actions: SetupActions,
    onShowTransactions: ((Counterpart) -> Unit)?,
    onDiffers: (Counterpart) -> Unit
) {
    val amount = rememberAmountFormat(currency)
    val month = rememberMonthFormatter()
    val date = rememberDateFormatter(FormatStyle.SHORT)
    val pick = rememberLauncherForActivityResult(LocalCategoryPicker.current) { picked ->
        actions.picked(picked?.first)
    }
    val pickCategory = {
        actions.pickCategory(card)
        // Expenses only, the payees of the setup are those of debits
        pick.launch(false)
    }
    LaunchedEffect(card.key) { actions.cardShown(card) }
    val payee = card.payee
    val count = payee.transactionIds.size
    DecisionCard(
        title = payee.counterpart.name,
        value = amount(payee.amount),
        supportingText = pluralStringResource(
            R.plurals.next_setup_payee_transactions,
            count,
            count,
            month.format(payee.since)
        ),
        leading = {
            PayeeTile(payee.counterpart.name, logo = null) {
                Icon(Icons.Outlined.Storefront, contentDescription = null)
            }
        },
        content = {
            if (payee.recent.isNotEmpty()) {
                EntryPreview(
                    payee.recent.map {
                        PreviewEntry(date.format(it.date), it.purpose, amount(it.amount))
                    },
                    moreLabel = onShowTransactions?.let {
                        pluralStringResource(R.plurals.next_setup_show_transactions, count, count)
                    },
                    onMore = { onShowTransactions?.invoke(payee.counterpart) }
                )
            }
            card.suggestionPath?.let { path ->
                ChipRow(stringResource(R.string.next_setup_suggestion)) {
                    LabelChip(
                        text = path,
                        placeholder = path,
                        onClick = { actions.acceptSuggestion(card) }
                    )
                    LabelChip(
                        text = null,
                        placeholder = stringResource(R.string.next_setup_other_category),
                        onClick = pickCategory
                    )
                }
            }
            ToggleChip(
                text = stringResource(R.string.next_setup_always),
                selected = always,
                onClick = { actions.setAlways(!always) }
            )
        },
        actions = {
            TextButton(onClick = { onDiffers(payee.counterpart) }) {
                Text(stringResource(R.string.next_setup_differs))
            }
            TextButton(onClick = { actions.leave(card, unclear = true) }) {
                Text(stringResource(R.string.next_setup_unclear))
            }
            TextButton(onClick = { actions.leave(card, unclear = false) }) {
                Text(stringResource(R.string.next_setup_later))
            }
            if (card.suggestion != null) {
                Button(onClick = { actions.acceptSuggestion(card) }) {
                    Text(stringResource(R.string.next_setup_accept))
                }
            } else {
                Button(onClick = pickCategory) {
                    Text(stringResource(R.string.next_setup_choose_category))
                }
            }
        }
    )
}
