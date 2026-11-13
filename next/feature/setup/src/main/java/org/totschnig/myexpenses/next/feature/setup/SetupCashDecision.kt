package org.totschnig.myexpenses.next.feature.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.totschnig.myexpenses.designsystem.ChipRow
import org.totschnig.myexpenses.designsystem.DecisionCard
import org.totschnig.myexpenses.designsystem.FormTextField
import org.totschnig.myexpenses.designsystem.LabelChip
import org.totschnig.myexpenses.designsystem.PayeeTile
import org.totschnig.myexpenses.designsystem.RadioRow
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.categories.LocalCategoryPicker
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.data.transactions.parseAmountText

/**
 * The cash withdrawals: kept in a cash account, with what the user holds now, or as spending; the
 * withdrawals so far get a category either way, see [CashActions]
 *
 * @param onPicked the category picker returned a category, see [SetupActions.picked]
 */
@Composable
internal fun CashDecision(
    card: CashCard,
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
    var toAccount by rememberSaveable { mutableStateOf(card.canOpenAccount) }
    var balanceText by rememberSaveable { mutableStateOf("") }
    val balance = parseAmountText(balanceText, currency.fractionDigits)
    val isToAccount = toAccount && card.canOpenAccount
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
        },
        content = {
            if (card.canOpenAccount) {
                Text(
                    stringResource(R.string.next_setup_cash_question),
                    style = MaterialTheme.typography.titleSmall
                )
                Column {
                    RadioRow(
                        title = stringResource(R.string.next_setup_cash_account),
                        supportingText = stringResource(R.string.next_setup_cash_account_text),
                        selected = isToAccount,
                        onClick = { toAccount = true },
                        isFirst = true
                    )
                    RadioRow(
                        title = stringResource(R.string.next_setup_cash_spending),
                        supportingText = stringResource(R.string.next_setup_cash_spending_text),
                        selected = !isToAccount,
                        onClick = { toAccount = false }
                    )
                }
                if (isToAccount) {
                    FormTextField(
                        label = stringResource(R.string.next_setup_cash_balance),
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        keyboardType = KeyboardType.Decimal,
                        isError = balanceText.isNotBlank() && balance == null,
                        trailing = { Text(currency.symbol) }
                    )
                }
            }
            val categoryLabel = if (isToAccount) {
                R.string.next_setup_cash_so_far
            } else {
                R.string.next_setup_cash_category
            }
            ChipRow(stringResource(categoryLabel)) {
                LabelChip(
                    text = card.categoryPath,
                    placeholder = stringResource(R.string.next_setup_choose_category),
                    onClick = pickCategory
                )
            }
        },
        actions = {
            TextButton(onClick = actions::leaveCash) {
                Text(stringResource(R.string.next_setup_later))
            }
            Button(
                onClick = { actions.keepCash(card, balance.takeIf { isToAccount }) },
                enabled = card.categoryId != null && (!isToAccount || balance != null)
            ) {
                Text(stringResource(R.string.next_setup_accept))
            }
        }
    )
}
