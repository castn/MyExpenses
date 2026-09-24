package org.totschnig.myexpenses.next.contracts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.util.convAmount

/**
 * Which payments belong to a confirmed contract, as defined by its [ContractRule]:
 * interval, payees and amount range can be changed, and other contracts can be joined into it.
 *
 * @param mergeCandidates contracts and suggestions that can be joined into this one
 */
@Composable
internal fun ContractRuleSettings(
    contract: Contract,
    rule: ContractRule,
    currency: CurrencyUnit,
    mergeCandidates: List<Contract>,
    onSetInterval: (ContractInterval) -> Unit,
    onRemovePayee: (Long) -> Unit,
    onRemoveTargetAccount: (Long) -> Unit = {},
    onSetAmountRange: (LongRange?) -> Unit,
    onMerge: (Contract) -> Unit,
    onSetReserve: (Boolean) -> Unit = {},
) {
    var showIntervalDialog by rememberSaveable { mutableStateOf(false) }
    var showAmountRangeDialog by rememberSaveable { mutableStateOf(false) }
    var showMergeDialog by rememberSaveable { mutableStateOf(false) }
    val formatter = LocalCurrencyFormatter.current

    if (showIntervalDialog) {
        IntervalDialog(
            current = rule.interval,
            onSelect = {
                onSetInterval(it)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
        )
    }
    if (showAmountRangeDialog) {
        AmountRangeDialog(
            range = rule.amountRange,
            currency = currency,
            onConfirm = {
                onSetAmountRange(it)
                showAmountRangeDialog = false
            },
            onDismiss = { showAmountRangeDialog = false }
        )
    }
    if (showMergeDialog) {
        MergeDialog(
            isIncome = contract.isIncome,
            candidates = mergeCandidates,
            currency = currency,
            onSelect = {
                onMerge(it)
                showMergeDialog = false
            },
            onDismiss = { showMergeDialog = false }
        )
    }

    SectionTitle(stringResource(R.string.next_contracts_matching))
    DetailCard {
        EditableRow(
            label = stringResource(R.string.next_contracts_interval),
            value = stringResource(rule.interval.labelRes),
            onClick = { showIntervalDialog = true },
            isFirst = true
        )
        val payeeLabel = stringResource(if (contract.isIncome) R.string.next_income_payers else R.string.next_contracts_payees)
        val removePayeeLabel =
            stringResource(if (contract.isIncome) R.string.next_income_remove_payer else R.string.next_contracts_remove_payee)
        val accountLabel = stringResource(R.string.next_contracts_target_account)
        val removeAccountLabel = stringResource(R.string.next_contracts_remove_target_account)
        // At least one payee, account or the template has to remain, or the rule would match nothing
        val canRemove = rule.payeeIds.size + rule.targetAccountIds.size > 1 || rule.templateId != null
        rule.payeeIds.sortedBy { it }.forEach { payeeId ->
            MatcherRow(
                label = payeeLabel,
                value = contract.transactions.firstOrNull { it.payeeId == payeeId && it.targetAccountId == null }?.payeeName,
                removeLabel = removePayeeLabel.takeIf { canRemove },
                onRemove = { onRemovePayee(payeeId) }
            )
        }
        rule.targetAccountIds.sortedBy { it }.forEach { accountId ->
            MatcherRow(
                label = accountLabel,
                // Movements are named after the account they go to
                value = contract.transactions.firstOrNull { it.targetAccountId == accountId }?.payeeName,
                removeLabel = removeAccountLabel.takeIf { canRemove },
                onRemove = { onRemoveTargetAccount(accountId) }
            )
        }
        if (!contract.isIncome) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            ReserveRow(contract, onSetReserve)
        }
        rule.amountRange?.let { range ->
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            EditableRow(
                label = stringResource(R.string.next_contracts_amount_range),
                value = stringResource(
                    R.string.next_contracts_amount_range_value,
                    formatter.convAmount(range.first, currency),
                    formatter.convAmount(range.last, currency)
                ),
                onClick = { showAmountRangeDialog = true },
                isFirst = true
            )
        }
        LinkRow(
            stringResource(if (contract.isIncome) R.string.next_income_merge else R.string.next_contracts_merge),
            onClick = { showMergeDialog = true }
        )
    }
}

/**
 * Whether the contract puts money aside, with a hint whether this was detected automatically
 */
@Composable
private fun ReserveRow(contract: Contract, onSetReserve: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = contract.isReserve, role = Role.Switch, onValueChange = onSetReserve)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.next_reserve),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(
                    if (contract.reserveChoice == null) R.string.next_reserve_automatic else R.string.next_reserve_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = contract.isReserve, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}

/**
 * A payee or target account whose payments belong to the contract
 *
 * @param removeLabel null if it cannot be removed, since it is the last one
 */
@Composable
private fun MatcherRow(label: String, value: String?, removeLabel: String?, onRemove: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value ?: "–",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            textAlign = TextAlign.End
        )
        if (removeLabel != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    contentDescription = removeLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Label and value like [InfoRow], with a pencil showing that tapping changes the value
 */
@Composable
private fun EditableRow(label: String, value: String, onClick: () -> Unit, isFirst: Boolean = false) {
    if (!isFirst) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary
        )
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(16.dp)
        )
    }
}

@Composable
internal fun IntervalDialog(
    current: ContractInterval,
    onSelect: (ContractInterval) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.next_contracts_interval),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                ContractInterval.entries.forEach { interval ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = interval == current,
                                role = Role.RadioButton,
                                onClick = { onSelect(interval) }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = interval == current, onClick = null)
                        Text(
                            stringResource(interval.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * Plain decimal number for editing, e.g. "7.19" for 719 minor units in EUR
 */
private fun Long.toDecimalString(currency: CurrencyUnit) =
    BigDecimal.valueOf(this, currency.fractionDigits).toPlainString()

private fun String.toMinorUnits(currency: CurrencyUnit): Long? =
    trim().replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() >= 0 }
        ?.movePointRight(currency.fractionDigits)
        ?.takeIf { it.stripTrailingZeros().scale() <= 0 }
        ?.toLong()

@Composable
private fun AmountRangeDialog(
    range: LongRange?,
    currency: CurrencyUnit,
    onConfirm: (LongRange?) -> Unit,
    onDismiss: () -> Unit,
) {
    var min by rememberSaveable { mutableStateOf(range?.first?.toDecimalString(currency) ?: "") }
    var max by rememberSaveable { mutableStateOf(range?.last?.toDecimalString(currency) ?: "") }
    val minValue = min.toMinorUnits(currency)
    val maxValue = max.toMinorUnits(currency)
    val isValid = minValue != null && maxValue != null && minValue <= maxValue
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.next_contracts_amount_range)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.next_contracts_amount_range_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = min,
                    onValueChange = { min = it },
                    label = { Text(stringResource(R.string.next_contracts_amount_min)) },
                    suffix = { Text(currency.symbol) },
                    singleLine = true,
                    isError = min.isNotEmpty() && minValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = max,
                    onValueChange = { max = it },
                    label = { Text(stringResource(R.string.next_contracts_amount_max)) },
                    suffix = { Text(currency.symbol) },
                    singleLine = true,
                    isError = max.isNotEmpty() && (maxValue == null || minValue != null && maxValue < minValue),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(
                    onClick = { onConfirm(null) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.next_contracts_amount_range_remove))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(minValue!!..maxValue!!) }, enabled = isValid) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun MergeDialog(
    isIncome: Boolean,
    candidates: List<Contract>,
    currency: CurrencyUnit,
    onSelect: (Contract) -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = LocalCurrencyFormatter.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isIncome) R.string.next_income_merge else R.string.next_contracts_merge)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(if (isIncome) R.string.next_income_merge_hint else R.string.next_contracts_merge_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (candidates.isEmpty()) {
                    Text(
                        stringResource(R.string.next_contracts_merge_empty),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(candidate) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                candidate.displayName.ifEmpty { "–" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(candidate.interval.labelRes) +
                                        if (candidate.isConfirmed) "" else " · " + stringResource(R.string.next_contracts_suggestion),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            formatter.convAmount(candidate.lastAmount, currency),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
