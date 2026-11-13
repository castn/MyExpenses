package org.totschnig.myexpenses.next.feature.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.totschnig.myexpenses.designsystem.ChipRow
import org.totschnig.myexpenses.designsystem.DecisionCard
import org.totschnig.myexpenses.designsystem.FormPickerField
import org.totschnig.myexpenses.designsystem.FormTextField
import org.totschnig.myexpenses.designsystem.Highlighted
import org.totschnig.myexpenses.designsystem.LabelChip
import org.totschnig.myexpenses.designsystem.PayeeTile
import org.totschnig.myexpenses.designsystem.SegmentedChoice
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.categories.LocalCategoryPicker
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.data.transactions.parseAmountText

/**
 * The cash withdrawals: kept in a new cash account, with what the user holds now, or as spending;
 * the withdrawals so far get a category either way. Nothing here decides: "Next" in the bar of the
 * page confirms it, see [CashActions].
 *
 * @param missingTaps see [CardInput.missingTaps]
 * @param onPicked the category picker returned a category, see [PayeeCardActions.picked]
 */
@Composable
internal fun CashDecision(
    card: CashCard,
    missingTaps: Int,
    currency: CurrencyInfo,
    actions: CashActions,
    onPicked: (Long?) -> Unit
) {
    val amount = rememberAmountFormat(currency)
    val month = rememberMonthFormatter()
    val pick = rememberLauncherForActivityResult(LocalCategoryPicker.current) { picked ->
        onPicked(picked?.first)
    }
    val pickCategory = {
        actions.pickCashCategory()
        // Expenses only, cash withdrawals are debits
        pick.launch(false)
    }
    val missing = CashMissing(
        missingTaps,
        balance = card.toAccount && card.balance(currency) == null,
        category = card.categoryId == null
    )
    val cash = card.cash
    val title = stringResource(R.string.next_setup_cash_title)
    DecisionCard(
        title = title,
        value = amount(cash.amount),
        supportingText = stringResource(
            R.string.next_setup_cash_per_month,
            amount(cash.perMonth),
            month.format(cash.since)
        ),
        leading = {
            PayeeTile(title, logo = null) {
                Icon(Icons.Outlined.Payments, contentDescription = null)
            }
        }
    ) {
        if (card.canOpenAccount) {
            SegmentedChoice(
                options = listOf(true, false),
                selected = card.toAccount,
                onSelect = actions::chooseCashAccount,
                label = { stringResource(if (it) ACCOUNT_LABEL else SPENDING_LABEL) },
                title = stringResource(R.string.next_setup_cash_question)
            ) { toAccount ->
                if (toAccount) {
                    AccountAnswer(card, currency, missing, actions, pickCategory)
                } else {
                    SpendingAnswer(card, missing, actions, pickCategory)
                }
            }
        } else {
            SpendingAnswer(card, missing, actions, pickCategory)
        }
    }
}

/** What the cash account holds now, then the category of the withdrawals so far */
@Composable
private fun AccountAnswer(
    card: CashCard,
    currency: CurrencyInfo,
    missing: CashMissing,
    actions: CashActions,
    onPick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Hint(R.string.next_setup_cash_account_text)
        Highlighted(trigger = missing.balanceTrigger) {
            FormTextField(
                label = stringResource(R.string.next_setup_cash_balance),
                value = card.balanceText,
                onValueChange = actions::setCashBalance,
                keyboardType = KeyboardType.Decimal,
                isError = missing.showsBalance ||
                    (card.balanceText.isNotBlank() && card.balance(currency) == null),
                supportingText = stringResource(R.string.next_setup_cash_balance_missing)
                    .takeIf { missing.showsBalance },
                trailing = { Text(currency.symbol) }
            )
        }
        CashCategory(card, R.string.next_setup_cash_so_far, missing, actions, onPick)
    }
}

/** The category of the withdrawals, the later ones too */
@Composable
private fun SpendingAnswer(
    card: CashCard,
    missing: CashMissing,
    actions: CashActions,
    onPick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Hint(R.string.next_setup_cash_spending_text)
        CashCategory(card, R.string.next_setup_category, missing, actions, onPick)
    }
}

@Composable
private fun CashCategory(
    card: CashCard,
    label: Int,
    missing: CashMissing,
    actions: CashActions,
    onPick: () -> Unit
) {
    Highlighted(trigger = missing.categoryTrigger) {
        FormPickerField(
            label = stringResource(label),
            value = card.categoryPath.orEmpty(),
            onClick = onPick,
            placeholder = stringResource(R.string.next_setup_choose_category),
            supportingText = when {
                missing.showsCategory -> stringResource(R.string.next_setup_category_missing)
                card.isSuggested -> stringResource(R.string.next_setup_suggestion)
                else -> null
            },
            isError = missing.showsCategory
        )
        card.offerPath?.let { path ->
            ChipRow(stringResource(R.string.next_setup_suggestion)) {
                LabelChip(text = path, placeholder = path, onClick = {
                    actions.takeCashSuggestion(card)
                })
            }
        }
    }
}

/**
 * What is missing on the cash card after "Next" was tapped [taps] times without it: the field
 * higher up lights up first, both show their error
 */
private class CashMissing(val taps: Int, val balance: Boolean, val category: Boolean) {
    val balanceTrigger: Int get() = if (balance) taps else 0
    val categoryTrigger: Int get() = if (category && !balance) taps else 0
    val showsBalance: Boolean get() = taps > 0 && balance
    val showsCategory: Boolean get() = taps > 0 && category
}

@Composable
private fun Hint(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** What the cash account holds as typed, in minor units of [currency]; null if it is no amount */
internal fun CashCard.balance(currency: CurrencyInfo): Long? =
    parseAmountText(balanceText, currency.fractionDigits)

/** "Next" can confirm: a category, and for a cash account what it holds */
internal fun CashCard.canConfirm(currency: CurrencyInfo): Boolean =
    categoryId != null && (!toAccount || balance(currency) != null)

private val ACCOUNT_LABEL = R.string.next_setup_cash_account
private val SPENDING_LABEL = R.string.next_setup_cash_spending
