package org.totschnig.myexpenses.next.feature.transactions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.FormatStyle
import org.totschnig.myexpenses.designsystem.ActionChip
import org.totschnig.myexpenses.designsystem.ChipRow
import org.totschnig.myexpenses.designsystem.ChoiceChips
import org.totschnig.myexpenses.designsystem.DateDialog
import org.totschnig.myexpenses.designsystem.FormPickerField
import org.totschnig.myexpenses.designsystem.FormTextField
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.LabelChip
import org.totschnig.myexpenses.designsystem.SingleChoiceDialog
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.SwitchRow
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.LabelIcons
import org.totschnig.myexpenses.next.core.ui.categories.LocalCategoryPicker
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.data.transactions.AccountOption
import org.totschnig.myexpenses.next.data.transactions.ChangedField
import org.totschnig.myexpenses.next.data.transactions.MethodOption
import org.totschnig.myexpenses.next.data.transactions.TransactionDraft
import org.totschnig.myexpenses.next.data.transactions.differsInBankFields

/**
 * Chips to choose the tags of a transaction in its form, handed in by the shell from the area of
 * the tags; nothing is saved until the form is
 */
typealias TagField = @Composable (tagIds: List<Long>, onChange: (List<Long>) -> Unit) -> Unit

/**
 * All fields of a transaction, for a new one on its own screen and for changing one in its
 * details. A transaction from the bank import starts with a warning. A new transaction offers the
 * templates used most as chips. For a template it has a title and whether it is saved without
 * asking instead of a day. Saving is left to the screen around it.
 *
 * @param currencySymbol after the amount
 * @param tagField the tags, left out where the area of the tags is
 */
@Composable
fun TransactionForm(
    state: TransactionEditState,
    draft: TransactionDraft,
    actions: TransactionFormActions,
    currencySymbol: String,
    tagField: TagField?,
    modifier: Modifier = Modifier
) {
    val transaction = state.transaction?.takeIf { it.id == draft.id }
    val isAmountEditable = transaction?.isAmountEditable ?: true
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showMethod by rememberSaveable { mutableStateOf(false) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    val onChange = actions::changeDraft
    val pickCategory = rememberLauncherForActivityResult(LocalCategoryPicker.current) { picked ->
        picked?.let { (id, path) ->
            onChange(draft.copy(categoryId = id, categoryPath = path, isCategorySuggested = false))
        }
    }
    Column(modifier.padding(bottom = Spacing.screen)) {
        if (transaction?.isImported == true) {
            HintRow(
                stringResource(R.string.next_transaction_imported_warning),
                isFirst = true,
                isWarning = true
            )
        }
        val template = draft.template
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (draft.isNewTransaction && state.templates.isNotEmpty()) {
                ChipRow(stringResource(R.string.next_template_chips)) {
                    state.templates.forEach { summary ->
                        ActionChip(
                            text = summary.title,
                            icon = Icons.Outlined.ContentCopy,
                            onClick = { actions.applyTemplate(summary.id) }
                        )
                    }
                }
            }
            if (template != null) {
                FormTextField(
                    label = stringResource(R.string.next_template_title),
                    value = template.title,
                    onValueChange = { onChange(draft.copy(template = template.copy(title = it))) },
                    isError = state.isTitleMissing,
                    supportingText = if (state.isTitleMissing) {
                        stringResource(R.string.next_template_title_missing)
                    } else {
                        null
                    }
                )
            }
            if (state.accounts.size > 1) {
                FormPickerField(
                    label = stringResource(R.string.next_transaction_account),
                    value = state.draftAccount?.label.orEmpty(),
                    onClick = { showAccount = true }
                )
            }
            if (isAmountEditable) {
                ChoiceChips(
                    options = listOf(false, true),
                    selected = draft.isIncome,
                    label = { stringResource(signLabel(isIncome = it)) },
                    onSelect = { onChange(draft.copy(isIncome = it)) }
                )
            }
            FormTextField(
                label = stringResource(R.string.next_transaction_amount),
                value = draft.amountText,
                onValueChange = { onChange(draft.copy(amountText = it)) },
                keyboardType = KeyboardType.Decimal,
                enabled = isAmountEditable,
                isError = state.isAmountInvalid,
                supportingText = when {
                    state.isAmountInvalid -> stringResource(
                        R.string.next_transaction_amount_invalid
                    )

                    !isAmountEditable -> stringResource(R.string.next_transaction_amount_foreign)

                    template != null -> stringResource(R.string.next_template_amount_empty)

                    else -> null
                },
                trailing = { Text(currencySymbol) }
            )
            FormTextField(
                label = stringResource(R.string.next_transaction_payee),
                value = draft.payee,
                onValueChange = { onChange(draft.copy(payee = it)) }
            )
            ChipRow(stringResource(R.string.next_details_category)) {
                LabelChip(
                    text = draft.categoryPath,
                    placeholder = stringResource(R.string.next_category_set),
                    onClick = { pickCategory.launch(draft.isIncome) },
                    icon = LabelIcons.category
                )
                state.suggestion?.takeIf { draft.categoryId == null }?.let { suggestion ->
                    ActionChip(
                        text = stringResource(R.string.next_category_suggested, suggestion.path),
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = actions::acceptSuggestion
                    )
                }
            }
            tagField?.invoke(draft.tagIds) { onChange(draft.copy(tagIds = it)) }
            if (template == null) {
                FormPickerField(
                    label = stringResource(R.string.next_transaction_date),
                    value = dateFormatter.format(draft.day),
                    onClick = { showDate = true }
                )
            }
            if (state.methods.isNotEmpty() || draft.methodId != null) {
                FormPickerField(
                    label = stringResource(R.string.next_transaction_method),
                    value = state.methods.find { it.id == draft.methodId }?.label
                        ?: stringResource(R.string.next_transaction_no_method),
                    onClick = { showMethod = true }
                )
            }
            FormTextField(
                label = stringResource(R.string.next_transaction_comment),
                value = draft.comment,
                onValueChange = { onChange(draft.copy(comment = it)) },
                singleLine = false
            )
        }
        if (template != null) {
            SwitchRow(
                label = stringResource(R.string.next_template_save_directly),
                checked = template.saveDirectly,
                onCheckedChange = {
                    onChange(draft.copy(template = template.copy(saveDirectly = it)))
                },
                supportingText = stringResource(R.string.next_template_save_directly_hint),
                isFirst = true
            )
        }
    }
    if (showAccount) {
        AccountDialog(
            state.accounts,
            draft.accountId,
            onSelect = { onChange(draft.copy(accountId = it)) },
            onDismiss = { showAccount = false }
        )
    }
    if (showDate) {
        DateDialog(
            initial = draft.day,
            onConfirm = {
                onChange(draft.copy(day = it))
                showDate = false
            },
            onDismiss = { showDate = false }
        )
    }
    if (showMethod) {
        MethodDialog(
            state.methods,
            draft.methodId,
            onSelect = { onChange(draft.copy(methodId = it)) },
            onDismiss = { showMethod = false }
        )
    }
}

@Composable
private fun AccountDialog(
    accounts: List<AccountOption>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    SingleChoiceDialog(
        title = stringResource(R.string.next_transaction_account),
        options = accounts,
        selected = accounts.find { it.id == selectedId },
        label = { it.label },
        onSelect = {
            onSelect(it.id)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

/** Picks one of [methods] or none */
@Composable
fun MethodDialog(
    methods: List<MethodOption>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val none = stringResource(R.string.next_transaction_no_method)
    SingleChoiceDialog(
        title = stringResource(R.string.next_transaction_method),
        options = listOf<MethodOption?>(null) + methods,
        selected = methods.find { it.id == selectedId },
        label = { it?.label ?: none },
        onSelect = {
            onSelect(it?.id)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

/**
 * Offers to undo the last change made in the details, in the snackbar of the sheet. For a
 * transaction from the bank import it also tells when the change no longer matches the bank.
 */
@Composable
fun UndoSnackbar(
    state: TransactionEditState,
    actions: TransactionEditActions,
    host: SnackbarHostState
) {
    val undo = state.undo ?: return
    val saved = stringResource(
        when (undo.field) {
            ChangedField.COMMENT -> R.string.next_transaction_saved_comment
            ChangedField.DATE -> R.string.next_transaction_saved_date
            ChangedField.METHOD -> R.string.next_transaction_saved_method
            ChangedField.ALL -> R.string.next_transaction_saved_all
        }
    )
    val current = state.transaction?.takeIf { it.id == undo.id }
    val differs = current != null && current.isImported &&
        undo.previous.differsInBankFields(undo.saved, ZoneId.systemDefault())
    val message = if (differs) {
        stringResource(
            R.string.next_transaction_saved_differs,
            saved
        )
    } else {
        saved
    }
    val undoLabel = stringResource(R.string.next_transaction_undo)
    LaunchedEffect(undo) {
        val result = host.showSnackbar(
            message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) actions.undo() else actions.undoShown()
    }
}

private fun signLabel(isIncome: Boolean) =
    if (isIncome) R.string.next_transaction_income else R.string.next_transaction_expense
