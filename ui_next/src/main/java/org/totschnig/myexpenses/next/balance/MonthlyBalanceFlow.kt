package org.totschnig.myexpenses.next.balance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.next.contracts.ContractTransactionList
import org.totschnig.myexpenses.next.contracts.ContractsUiState
import org.totschnig.myexpenses.next.contracts.ContractsViewModel
import org.totschnig.myexpenses.next.contracts.FixedTransactionsScreen
import org.totschnig.myexpenses.next.contracts.NextContractsScreen

private enum class BalancePage { Details, Income, Savings, Other }

/**
 * The pages behind the balance card of the overview: the breakdown, the regular incomes
 * and the transactions of savings and other expenses.
 *
 * @param onBack leaves the breakdown
 * @param onOpenContracts shows the contracts, which have their own tab
 */
@Composable
fun MonthlyBalanceFlow(
    viewModel: ContractsViewModel,
    onBack: () -> Unit,
    onOpenContracts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.balance.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(BalancePage.Details) }
    val currency = viewModel.homeCurrency
    val ready = state as? BalanceUiState.Ready
    if (ready == null) {
        BackHandler(onBack = onBack)
        CircularProgressIndicator(modifier.fillMaxSize().wrapContentSize())
        return
    }
    val balance = ready.balance
    val incomeState by viewModel.incomeState.collectAsStateWithLifecycle()
    val incomes = incomeState as? ContractsUiState.Ready
    var showSalaryDialog by rememberSaveable { mutableStateOf(false) }
    if (showSalaryDialog && incomes != null) {
        SalaryDialog(
            incomes = incomes.active,
            choice = incomes.salaryChoice,
            onSelect = {
                viewModel.setSalary(it)
                showSalaryDialog = false
            },
            onDismiss = { showSalaryDialog = false }
        )
    }
    val savingsLabel = stringResource(R.string.next_balance_savings)
    val otherLabel = stringResource(R.string.next_balance_other)

    when (page) {
        BalancePage.Details -> {
            BackHandler(onBack = onBack)
            MonthlyBalanceScreen(
                balance = balance,
                today = ready.today,
                currency = currency,
                onBack = onBack,
                onOpenIncome = { page = BalancePage.Income },
                onOpenContracts = onOpenContracts,
                onOpenSavings = { page = BalancePage.Savings }.takeIf { balance.savingsTransactionIds.isNotEmpty() },
                onOpenOther = { page = BalancePage.Other }.takeIf { balance.otherTransactionIds.isNotEmpty() },
                salaryName = incomes?.salary?.displayName?.takeIf { balance.period.isSalaryCycle },
                onChangeSalary = { showSalaryDialog = true },
                modifier = modifier
            )
        }

        BalancePage.Income -> {
            // Registered before the screen, so that its own back handling for details takes precedence
            BackHandler { page = BalancePage.Details }
            NextContractsScreen(
                state = incomeState,
                currency = currency,
                isIncome = true,
                onBack = { page = BalancePage.Details },
                onDismiss = viewModel::dismiss,
                onRestore = viewModel::restore,
                onRename = viewModel::rename,
                onSetSalary = viewModel::setSalary,
                contractTransactions = { contract, contentModifier ->
                    ContractTransactionList(viewModel, contract, contentModifier)
                },
                modifier = modifier
            )
        }

        BalancePage.Savings, BalancePage.Other -> {
            BackHandler { page = BalancePage.Details }
            val (label, ids) = if (page == BalancePage.Savings) savingsLabel to balance.savingsTransactionIds
            else otherLabel to balance.otherTransactionIds
            LaunchedEffect(ids) { viewModel.showTransactions(label, ids) }
            FixedTransactionsScreen(
                title = label,
                subtitle = balance.period.label(),
                onBack = { page = BalancePage.Details },
                modifier = modifier
            ) {
                ContractTransactionList(viewModel.contractTransactions.collectAsLazyPagingItems(), it)
            }
        }
    }
}
