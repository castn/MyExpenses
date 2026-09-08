package org.totschnig.myexpenses.next.contracts

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.totschnig.myexpenses.next.R

@get:StringRes
private val BuiltInArea.labelRes: Int
    get() = when (this) {
        BuiltInArea.INSURANCE -> R.string.next_contract_area_insurance
        BuiltInArea.HOUSING -> R.string.next_contract_area_housing
        BuiltInArea.TELECOM -> R.string.next_contract_area_telecom
        BuiltInArea.STREAMING -> R.string.next_contract_area_streaming
        BuiltInArea.FITNESS -> R.string.next_contract_area_fitness
        BuiltInArea.MOBILITY -> R.string.next_contract_area_mobility
        BuiltInArea.FINANCE -> R.string.next_contract_area_finance
        BuiltInArea.SOFTWARE -> R.string.next_contract_area_software
        BuiltInArea.PRESS -> R.string.next_contract_area_press
        BuiltInArea.MEMBERSHIP -> R.string.next_contract_area_membership
        BuiltInArea.DONATIONS -> R.string.next_contract_area_donations
        BuiltInArea.EDUCATION -> R.string.next_contract_area_education
    }

/**
 * Name of the category as shown to the user, null stands for no category
 */
@Composable
internal fun ContractArea?.label(): String = when (this) {
    is BuiltInArea -> stringResource(labelRes)
    is CustomArea -> name
    null -> stringResource(R.string.next_contracts_area_none)
}

/**
 * Asks for a name, used for contracts and categories.
 *
 * @param placeholder shown while the field is empty
 * @param allowBlank whether an empty name can be confirmed, e.g. to go back to a default
 */
@Composable
internal fun NameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String? = null,
    allowBlank: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = placeholder?.let { { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = allowBlank || name.isNotBlank()
            ) {
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
