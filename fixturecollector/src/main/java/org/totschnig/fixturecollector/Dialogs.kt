package org.totschnig.fixturecollector

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.totschnig.myexpenses.designsystem.FormDialog
import org.totschnig.myexpenses.designsystem.SingleChoiceDialog
import org.totschnig.myexpenses.feature.ExpectedItem

@Composable
fun TextFieldDialog(
    title: String,
    initial: String,
    keyboardType: KeyboardType,
    hint: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    FormDialog(title = title, onConfirm = { onConfirm(value) }, onDismiss = onDismiss) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            supportingText = hint?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** All values of an item; confirming needs a name and an amount */
@Composable
fun ItemDialog(item: ExpectedItem, onDismiss: () -> Unit, onConfirm: (ExpectedItem) -> Unit) {
    var name by rememberSaveable { mutableStateOf(item.name) }
    var amount by rememberSaveable {
        mutableStateOf(
            if (item.name.isEmpty() &&
                item.amount == 0L
            ) {
                ""
            } else {
                formatCents(item.amount)
            }
        )
    }
    var quantity by rememberSaveable { mutableStateOf(item.quantity) }
    var unit by rememberSaveable { mutableStateOf(item.unit.orEmpty()) }
    var unitPrice by rememberSaveable {
        mutableStateOf(item.unitPrice?.let(::formatCents).orEmpty())
    }
    var taxClass by rememberSaveable { mutableStateOf(item.taxClass.orEmpty()) }
    var group by rememberSaveable { mutableStateOf(item.group) }
    var pickGroup by rememberSaveable { mutableStateOf(false) }

    val parsedAmount = parseCents(amount)
    val parsedQuantity = quantity.trim().replace(',', '.').takeIf {
        it.toBigDecimalOrNull() != null
    }
    val parsedUnitPrice = unitPrice.takeIf { it.isNotBlank() }?.let(::parseCents)
    val valid = name.isNotBlank() && parsedAmount != null && parsedQuantity != null &&
        (unitPrice.isBlank() || parsedUnitPrice != null)

    FormDialog(
        title = stringResource(R.string.item),
        confirmEnabled = valid,
        onConfirm = {
            onConfirm(
                ExpectedItem(
                    name = name.trim(),
                    amount = parsedAmount!!,
                    quantity = parsedQuantity!!,
                    unit = unit.trim().ifEmpty { null },
                    unitPrice = parsedUnitPrice,
                    taxClass = taxClass.trim().ifEmpty { null },
                    group = group
                )
            )
        },
        onDismiss = onDismiss
    ) {
        Field(R.string.name, name, { name = it }, KeyboardType.Text)
        Field(
            R.string.amount,
            amount,
            { amount = it },
            KeyboardType.Decimal,
            isError =
                amount.isNotBlank() && parsedAmount == null
        )
        Field(
            R.string.quantity,
            quantity,
            { quantity = it },
            KeyboardType.Decimal,
            isError =
                parsedQuantity == null
        )
        Field(R.string.unit, unit, { unit = it }, KeyboardType.Text)
        Field(
            R.string.unit_price,
            unitPrice,
            { unitPrice = it },
            KeyboardType.Decimal,
            isError =
                unitPrice.isNotBlank() && parsedUnitPrice == null
        )
        Field(R.string.tax_class, taxClass, { taxClass = it }, KeyboardType.Ascii)
        OutlinedButton(onClick = { pickGroup = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.group) + ": " +
                    (
                        ItemGroups.ofId(group)?.let { stringResource(it.label) }
                            ?: stringResource(R.string.no_group)
                        )
            )
        }
    }

    if (pickGroup) {
        val options: List<ItemGroups?> = listOf(null) + ItemGroups.entries
        SingleChoiceDialog(
            title = stringResource(R.string.group),
            options = options,
            selected = ItemGroups.ofId(group),
            label = { option ->
                option?.let { stringResource(it.label) }
                    ?: stringResource(R.string.no_group)
            },
            onSelect = {
                group = it?.id
                pickGroup = false
            },
            onDismiss = { pickGroup = false }
        )
    }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}
