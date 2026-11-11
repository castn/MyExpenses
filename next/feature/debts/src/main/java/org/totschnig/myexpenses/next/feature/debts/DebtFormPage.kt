package org.totschnig.myexpenses.next.feature.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.FormatStyle
import org.totschnig.myexpenses.designsystem.ActionChip
import org.totschnig.myexpenses.designsystem.ActionChipRow
import org.totschnig.myexpenses.designsystem.AutocompleteField
import org.totschnig.myexpenses.designsystem.ChoiceChips
import org.totschnig.myexpenses.designsystem.DateDialog
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.FormPickerField
import org.totschnig.myexpenses.designsystem.FormTextField
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.data.debts.DebtDraft
import org.totschnig.myexpenses.next.data.debts.DebtProblem

/** Payees offered below the field while typing */
private const val PAYEE_SUGGESTIONS = 4

/**
 * The form of a debt: with whom, name, note, who owes whom, amount and currency, its value in the
 * home currency for another currency, and the date. The currency of a saved debt stays.
 */
@Composable
internal fun DebtFormPage(viewModel: DebtBookViewModel, onBack: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val draft = form.draft ?: return
    DetailScaffold(
        onBack = onBack,
        title = stringResource(
            if (draft.id == 0L) R.string.next_debt_form_new else R.string.next_debt_form_edit
        ),
        actions = {
            Button(onClick = viewModel::saveDraft) { Text(stringResource(R.string.next_debt_save)) }
        }
    ) {
        DebtFields(
            draft = draft,
            problems = form.problems,
            payees = form.payees,
            currencies = form.currencies,
            home = viewModel.homeCurrency,
            onChange = viewModel::changeDraft
        )
    }
}

@Composable
private fun DebtFields(
    draft: DebtDraft,
    problems: Set<DebtProblem>,
    payees: List<String>,
    currencies: List<CurrencyInfo>,
    home: CurrencyInfo,
    onChange: (DebtDraft) -> Unit
) {
    var showDate by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    val currency = currencies.find { it.code == draft.currency }
        ?: CurrencyInfo(draft.currency, draft.currency, home.fractionDigits)
    Column(
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormTextField(
            label = stringResource(R.string.next_debt_payee),
            value = draft.payee,
            onValueChange = { onChange(draft.copy(payee = it)) },
            isError = DebtProblem.Payee in problems,
            supportingText = stringResource(R.string.next_debt_payee_missing)
                .takeIf { DebtProblem.Payee in problems }
        )
        val suggestions = payeeSuggestions(payees, draft.payee)
        if (suggestions.isNotEmpty()) {
            ActionChipRow {
                suggestions.forEach { name ->
                    ActionChip(
                        text = name,
                        icon = Icons.Outlined.Person,
                        onClick = { onChange(draft.copy(payee = name)) }
                    )
                }
            }
        }
        FormTextField(
            label = stringResource(R.string.next_debt_label),
            value = draft.label,
            onValueChange = { onChange(draft.copy(label = it)) },
            isError = DebtProblem.Label in problems,
            supportingText = stringResource(R.string.next_debt_label_missing)
                .takeIf { DebtProblem.Label in problems }
        )
        ChoiceChips(
            options = listOf(true, false),
            selected = draft.isOwedToMe,
            label = { stringResource(directionLabel(it)) },
            onSelect = { onChange(draft.copy(isOwedToMe = it)) }
        )
        FormTextField(
            label = stringResource(R.string.next_debt_amount),
            value = draft.amountText,
            onValueChange = { onChange(draft.copy(amountText = it)) },
            keyboardType = KeyboardType.Decimal,
            isError = DebtProblem.Amount in problems,
            supportingText = stringResource(R.string.next_debt_amount_invalid)
                .takeIf { DebtProblem.Amount in problems },
            trailing = { Text(currency.symbol) }
        )
        if (draft.id == 0L && currencies.size > 1) {
            AutocompleteField(
                label = stringResource(R.string.next_debt_currency),
                selected = currency,
                options = currencies,
                optionLabel = { "${it.code} (${it.symbol})" },
                onSelect = { onChange(draft.copy(currency = it.code)) }
            )
        }
        if (draft.currency != home.code) {
            FormTextField(
                label = stringResource(R.string.next_debt_equivalent, home.code),
                value = draft.equivalentText,
                onValueChange = { onChange(draft.copy(equivalentText = it)) },
                keyboardType = KeyboardType.Decimal,
                isError = DebtProblem.Equivalent in problems,
                supportingText = stringResource(R.string.next_debt_equivalent_missing)
                    .takeIf { DebtProblem.Equivalent in problems },
                trailing = { Text(home.symbol) }
            )
        }
        FormPickerField(
            label = stringResource(R.string.next_debt_date),
            value = dateFormatter.format(draft.date),
            onClick = { showDate = true }
        )
        FormTextField(
            label = stringResource(R.string.next_debt_description),
            value = draft.description,
            onValueChange = { onChange(draft.copy(description = it)) },
            singleLine = false
        )
    }
    if (showDate) {
        DateDialog(
            initial = draft.date,
            onConfirm = {
                onChange(draft.copy(date = it))
                showDate = false
            },
            onDismiss = { showDate = false }
        )
    }
}

private fun directionLabel(isOwedToMe: Boolean) =
    if (isOwedToMe) R.string.next_debt_direction_owed_to_me else R.string.next_debt_direction_i_owe

/** Payees whose name contains what is typed, none once the name is complete */
internal fun payeeSuggestions(payees: List<String>, typed: String): List<String> {
    val text = typed.trim()
    if (text.isEmpty() || payees.any { it.equals(text, ignoreCase = true) }) return emptyList()
    return payees.filter { it.contains(text, ignoreCase = true) }.take(PAYEE_SUGGESTIONS)
}
