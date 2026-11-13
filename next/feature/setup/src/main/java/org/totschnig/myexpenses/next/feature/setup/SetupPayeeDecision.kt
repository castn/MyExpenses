package org.totschnig.myexpenses.next.feature.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.format.FormatStyle
import org.totschnig.myexpenses.designsystem.ChipRow
import org.totschnig.myexpenses.designsystem.DecisionCard
import org.totschnig.myexpenses.designsystem.EntryPreview
import org.totschnig.myexpenses.designsystem.FormPickerField
import org.totschnig.myexpenses.designsystem.LabelChip
import org.totschnig.myexpenses.designsystem.PayeeTile
import org.totschnig.myexpenses.designsystem.PreviewEntry
import org.totschnig.myexpenses.designsystem.SegmentedChoice
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.ToggleChip
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.categories.LocalCategoryPicker
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.data.categories.Counterpart

/**
 * One payee: what applies to its debits ([PayeeAnswer]) and, for one category, the category,
 * filled in from a certain suggestion or offered from an uncertain one; below, its last debits.
 * Nothing here decides: "Next" in the bar of the page confirms it, see [PayeeCardActions].
 */
@Composable
internal fun PayeeDecision(
    card: PayeeCard,
    always: Boolean,
    currency: CurrencyInfo,
    actions: PayeeCardActions,
    onShowTransactions: ((Counterpart) -> Unit)?
) {
    val amount = rememberAmountFormat(currency)
    val month = rememberMonthFormatter()
    val date = rememberDateFormatter(FormatStyle.SHORT)
    val pick = rememberLauncherForActivityResult(LocalCategoryPicker.current) { picked ->
        actions.picked(picked?.first)
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
        }
    ) {
        SegmentedChoice(
            options = PayeeAnswer.entries,
            selected = card.answer,
            onSelect = { actions.answer(card, it) },
            label = { stringResource(it.label) },
            title = stringResource(R.string.next_setup_question)
        ) { answer ->
            when (answer) {
                PayeeAnswer.CATEGORY -> CategoryAnswer(card, always, actions) {
                    actions.pickCategory(card)
                    // Expenses only, the payees of the setup are those of debits
                    pick.launch(false)
                }

                PayeeAnswer.DIFFERS -> AnswerHint(R.string.next_setup_differs_hint)

                PayeeAnswer.UNCLEAR -> AnswerHint(R.string.next_setup_unclear_hint)
            }
        }
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
    }
}

/** The category field, the uncertain suggestion to take and whether later debits get it too */
@Composable
private fun CategoryAnswer(
    card: PayeeCard,
    always: Boolean,
    actions: PayeeCardActions,
    onPick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        FormPickerField(
            label = stringResource(R.string.next_setup_category),
            value = card.categoryPath.orEmpty(),
            onClick = onPick,
            placeholder = stringResource(R.string.next_setup_choose_category),
            supportingText = stringResource(R.string.next_setup_suggestion).takeIf {
                card.isSuggested
            }
        )
        card.offerPath?.let { path ->
            ChipRow(stringResource(R.string.next_setup_suggestion)) {
                LabelChip(text = path, placeholder = path, onClick = {
                    actions.takeSuggestion(card)
                })
            }
        }
        ToggleChip(
            text = stringResource(R.string.next_setup_always),
            selected = always,
            onClick = { actions.setAlways(!always) }
        )
    }
}

@Composable
private fun AnswerHint(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val PayeeAnswer.label: Int
    get() = when (this) {
        PayeeAnswer.CATEGORY -> R.string.next_setup_answer_category
        PayeeAnswer.DIFFERS -> R.string.next_setup_differs
        PayeeAnswer.UNCLEAR -> R.string.next_setup_unclear
    }

/** "Last: REWE → Groceries", see [LastDecision] */
@Composable
internal fun lastDecisionText(last: LastDecision): String {
    val name = last.name ?: "…"
    val path = last.categoryPath ?: "…"
    return when (last.kind) {
        LastKind.CATEGORY -> stringResource(R.string.next_setup_last_category, name, path)

        LastKind.SOME -> stringResource(R.string.next_setup_last_some, name, path)

        LastKind.UNCLEAR -> stringResource(R.string.next_setup_last_unclear, name)

        LastKind.LATER -> stringResource(R.string.next_setup_last_later, name)

        LastKind.CASH -> if (last.categoryPath != null) {
            stringResource(R.string.next_setup_last_cash, path)
        } else {
            stringResource(R.string.next_setup_last_cash_later)
        }

        LastKind.CASH_ACCOUNT -> stringResource(R.string.next_setup_last_cash_account)
    }
}
