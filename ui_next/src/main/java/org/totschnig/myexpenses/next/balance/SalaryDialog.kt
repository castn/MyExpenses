package org.totschnig.myexpenses.next.balance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.next.contracts.Contract

/**
 * Lets the user choose which regular incomes are salaries, e.g. for more than one job.
 * Without any, the balance uses the calendar month.
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
    val current = incomes.salaries(choice).map { it.signature }
    var selected by rememberSaveable { mutableStateOf(current) }
    val automatic = incomes.automaticSalary()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.next_salary)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.next_salary_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                incomes.filter { it.isIncome && it.isActive }.forEach { income ->
                    val checked = income.signature in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = {
                                    selected = if (it) selected + income.signature else selected - income.signature
                                }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            income.displayName.ifEmpty { "–" },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                Text(
                    stringResource(R.string.next_salary_none_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (choice != SalaryChoice.Automatic) {
                    TextButton(
                        onClick = { onSelect(SalaryChoice.Automatic) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            stringResource(
                                R.string.next_salary_automatic,
                                automatic?.displayName ?: stringResource(R.string.next_salary_none_detected)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    // Unchanged: keep following the automatic choice
                    selected.toSet() == current.toSet() -> onDismiss()
                    selected.isEmpty() -> onSelect(SalaryChoice.None)
                    else -> onSelect(SalaryChoice.Fixed(selected.toSet()))
                }
            }) {
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
