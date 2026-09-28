package org.totschnig.myexpenses.next.contracts

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.next.R

/**
 * Part of the details of a transaction: the contract (or regular income) it belongs to,
 * or the possibility to declare it as one
 */
@Composable
fun PaymentContractSection(viewModel: ContractsViewModel, transactionId: Long) {
    val info by remember(transactionId) { viewModel.paymentInfo(transactionId) }
        .collectAsStateWithLifecycle(PaymentContractInfo.Unavailable)
    PaymentContractSection(
        info = info,
        onConfirm = viewModel::confirm,
        onRestore = viewModel::restore,
        onMark = { viewModel.markAsContract(transactionId, it) }
    )
}

@Composable
fun PaymentContractSection(
    info: PaymentContractInfo,
    onConfirm: (Contract) -> Unit,
    onRestore: (Contract) -> Unit,
    onMark: (ContractInterval) -> Unit,
) {
    if (info == PaymentContractInfo.Unavailable) return
    var showIntervalDialog by rememberSaveable { mutableStateOf(false) }
    val isIncome = when (info) {
        is PaymentContractInfo.Confirmed -> info.contract.isIncome
        is PaymentContractInfo.Suggested -> info.contract.isIncome
        is PaymentContractInfo.Dismissed -> info.contract.isIncome
        is PaymentContractInfo.Markable -> info.isIncome
        else -> false
    }
    if (showIntervalDialog) {
        IntervalDialog(
            current = ContractInterval.MONTHLY,
            title = stringResource(R.string.next_payment_interval_question),
            onSelect = {
                onMark(it)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
        )
    }
    SectionTitle(stringResource(if (isIncome) R.string.next_income_title else R.string.next_tab_contracts))
    DetailCard {
        when (info) {
            is PaymentContractInfo.Confirmed -> InfoRow(
                stringResource(R.string.next_payment_part_of),
                info.contract.displayName.ifEmpty { "–" },
                isFirst = true
            )

            is PaymentContractInfo.Suggested -> {
                InfoRow(
                    stringResource(R.string.next_payment_suggested),
                    info.contract.displayName.ifEmpty { "–" },
                    isFirst = true
                )
                LinkRow(
                    stringResource(if (isIncome) R.string.next_income_confirm else R.string.next_contracts_confirm),
                    onClick = { onConfirm(info.contract) }
                )
            }

            is PaymentContractInfo.Dismissed -> {
                InfoRow(
                    stringResource(if (isIncome) R.string.next_income_dismiss else R.string.next_contracts_dismiss),
                    info.contract.displayName.ifEmpty { "–" },
                    isFirst = true
                )
                LinkRow(
                    stringResource(if (isIncome) R.string.next_income_confirm else R.string.next_contracts_confirm),
                    onClick = { onRestore(info.contract) }
                )
            }

            is PaymentContractInfo.Markable -> LinkRow(
                stringResource(if (isIncome) R.string.next_payment_mark_income else R.string.next_payment_mark_contract),
                onClick = { showIntervalDialog = true },
                isFirst = true
            )

            PaymentContractInfo.NoPayee -> Text(
                stringResource(R.string.next_payment_no_payee),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )

            PaymentContractInfo.Unavailable -> {}
        }
    }
}
