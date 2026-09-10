package org.totschnig.myexpenses.next.balance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.next.contracts.Contract

/**
 * Lets the user choose which regular income is the salary, or none for the calendar month
 *
 * @param incomes the regular incomes, without dismissed ones
 */
@Composable
fun SalaryDialog(
    incomes: List<Contract>,
    choice: SalaryChoice,
    onSelect: (SalaryChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val automatic = incomes.automaticSalary()
    val choices: List<Pair<SalaryChoice, String>> = buildList {
        add(
            SalaryChoice.Automatic to stringResource(
                R.string.next_salary_automatic,
                automatic?.displayName ?: stringResource(R.string.next_salary_none_detected)
            )
        )
        incomes.filter { it.isIncome && it.isActive }.forEach {
            add(SalaryChoice.Fixed(it.signature) to it.displayName.ifEmpty { "–" })
        }
        add(SalaryChoice.None to stringResource(R.string.next_salary_none))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.next_salary)) },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.next_salary_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                choices.forEach { (option, label) ->
                    val selected = option == choice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            label,
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
