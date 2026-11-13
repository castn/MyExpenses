package org.totschnig.myexpenses.next.feature.contracts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.MultiChoiceSheet
import org.totschnig.myexpenses.designsystem.OptionRow
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.SalaryChoice
import org.totschnig.myexpenses.next.data.contracts.automaticSalary
import org.totschnig.myexpenses.next.data.contracts.fixSalary
import org.totschnig.myexpenses.next.data.contracts.salaries
import org.totschnig.myexpenses.next.data.contracts.toggleSalary

/**
 * Lets the user choose which regular incomes are salaries, e.g. for more than one job.
 * Without any, the balance uses the calendar month.
 *
 * @param incomes the regular incomes, without dismissed ones
 */
@Composable
fun SalarySheet(
    incomes: List<Contract>,
    choice: SalaryChoice,
    onSelect: (SalaryChoice) -> Unit,
    onDismiss: () -> Unit
) {
    val current = incomes.salaries(choice).map { it.signature }
    var selected by rememberSaveable { mutableStateOf(current) }
    val automatic = incomes.automaticSalary()
    MultiChoiceSheet(
        title = stringResource(R.string.next_salary),
        options = incomes.filter { it.isIncome && it.isActive },
        selected = incomes.filterTo(HashSet()) { it.signature in selected },
        onToggle = { income ->
            selected =
                if (income.signature in selected) {
                    selected - income.signature
                } else {
                    selected + income.signature
                }
        },
        label = { it.title },
        onConfirm = {
            when {
                // Unchanged: keep following the automatic choice
                selected.toSet() == current.toSet() -> onDismiss()

                selected.isEmpty() -> onSelect(SalaryChoice.None)

                else -> onSelect(SalaryChoice.Fixed(selected.toSet()))
            }
        },
        onDismiss = onDismiss,
        intro = stringResource(R.string.next_salary_explanation),
        footer = {
            Text(
                stringResource(R.string.next_salary_none_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (choice != SalaryChoice.Automatic) {
                TextButton(onClick = {
                    onSelect(SalaryChoice.Automatic)
                }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        stringResource(
                            R.string.next_salary_automatic,
                            automatic?.displayName
                                ?: stringResource(R.string.next_salary_none_detected)
                        )
                    )
                }
            }
        }
    )
}

/**
 * Chooses the salaries within another page, e.g. the income step of the setup: the active regular
 * incomes with the salaries checked; checking one fixes the choice, and "Set as salary" keeps the
 * automatic one as it is. Nothing while the incomes load or without active incomes.
 */
@Composable
fun SalaryChoiceSection(viewModel: ContractsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.incomeState.collectAsStateWithLifecycle()
    val incomes = state.valueOrNull ?: return
    val currency by viewModel.homeCurrency.collectAsStateWithLifecycle()
    val amount = rememberAmountFormat(currency)
    val options = incomes.active.filter { it.isIncome && it.isActive }
    if (options.isEmpty()) return
    val choice = incomes.salaryChoice
    val salaries = options.salaries(choice).mapTo(HashSet()) { it.signature }
    Column(modifier.padding(top = Spacing.content)) {
        SectionTitle(stringResource(R.string.next_salary))
        Text(
            stringResource(R.string.next_salary_explanation),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        DetailCard {
            options.forEach { income ->
                OptionRow(
                    label = income.title,
                    selected = income.signature in salaries,
                    onClick = {
                        viewModel.setSalary(options.toggleSalary(choice, income.signature))
                    },
                    supportingText = amount(income.lastAmount)
                )
            }
        }
        Text(
            stringResource(R.string.next_salary_none_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (choice == SalaryChoice.Automatic && salaries.isNotEmpty()) {
            Button(
                onClick = { viewModel.setSalary(options.fixSalary(choice)) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.next_salary_set))
            }
        }
    }
}
