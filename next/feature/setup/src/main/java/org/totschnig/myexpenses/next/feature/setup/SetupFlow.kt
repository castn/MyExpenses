package org.totschnig.myexpenses.next.feature.setup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.setup.SetupStep

/**
 * The guided setup after the first start, on a back stack of its own ([SetupKey]): the overview of
 * the steps, the regular incomes, categories by payee up to the target and what that brought, the
 * contracts and the end
 *
 * @param currency the home currency, all amounts of the setup are in it
 * @param onClose leaves the setup; it is offered again from the overview unless it was closed
 * @param contracts the suggestions of contracts to confirm, from the contracts area; without it
 * there is no contracts step
 * @param incomes the regular incomes to confirm and the choice of the salary, from the contracts
 * area; without it the income step shows only its text
 * @param transactions shows the transactions of a payee; without it the cards show only the last
 * ones
 */
@Composable
fun SetupFlow(
    viewModel: SetupViewModel,
    currency: CurrencyInfo,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contracts: (@Composable ColumnScope.() -> Unit)? = null,
    incomes: (@Composable ColumnScope.() -> Unit)? = null,
    transactions: SetupTransactions? = null
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(SetupKey.Start)
    // The step after [step] that is not done, else the end. A step that is over leaves the back
    // stack, so that back returns to the overview, not to the step.
    val goOnAfter = { step: SetupStep? ->
        val steps = overview?.steps.orEmpty()
        val after = if (step == null) steps else steps.dropWhile { it.step != step }.drop(1)
        val next = after.firstOrNull { it.state != StepState.DONE }?.step
        backStack.add(next?.key() ?: SetupKey.Done)
    }
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() else onClose() },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<SetupKey.Start> {
                SetupStartPage(
                    overview = overview,
                    onStart = {
                        overview?.next?.let { backStack.add(it.key()) } ?: goOnAfter(null)
                    },
                    onSkipAll = {
                        viewModel.close()
                        onClose()
                    },
                    onBack = onClose
                )
            }
            entry<SetupKey.Categories> {
                SetupCategoriesPage(
                    viewModel = viewModel,
                    currency = currency,
                    onBack = { backStack.remove(SetupKey.Categories) },
                    onShowTransactions = transactions?.let {
                        { payee -> backStack.add(SetupKey.Transactions(payee.key, payee.name)) }
                    },
                    onDiffers = { payee -> backStack.add(SetupKey.Differs(payee.key, payee.name)) },
                    onSkip = {
                        viewModel.endStep(SetupStep.CATEGORIES, skipped = true)
                        backStack.remove(SetupKey.Categories)
                        goOnAfter(SetupStep.CATEGORIES)
                    },
                    onFinish = {
                        viewModel.endStep(SetupStep.CATEGORIES, skipped = false)
                        backStack.remove(SetupKey.Categories)
                        backStack.add(SetupKey.Automatic)
                    }
                )
            }
            entry<SetupKey.Transactions> { key ->
                transactions?.let {
                    SetupTransactionsPage(viewModel, it, key, onBack = { backStack.remove(key) })
                }
            }
            entry<SetupKey.Differs> { key ->
                SetupDiffersPage(viewModel, currency, key, onBack = { backStack.remove(key) })
            }
            entry<SetupKey.Automatic> {
                SetupAutomaticPage(
                    viewModel = viewModel,
                    onBack = { backStack.remove(SetupKey.Automatic) },
                    onNext = {
                        backStack.remove(SetupKey.Automatic)
                        goOnAfter(SetupStep.CATEGORIES)
                    }
                )
            }
            entry<SetupKey.Income> {
                SetupSectionPage(
                    title = stringResource(R.string.next_setup_step_income),
                    text = stringResource(R.string.next_setup_income_text),
                    onBack = { backStack.remove(SetupKey.Income) },
                    onEnd = { skipped ->
                        viewModel.endStep(SetupStep.INCOME, skipped)
                        backStack.remove(SetupKey.Income)
                        goOnAfter(SetupStep.INCOME)
                    },
                    section = incomes
                )
            }
            entry<SetupKey.Contracts> {
                SetupSectionPage(
                    title = stringResource(R.string.next_setup_step_contracts),
                    text = stringResource(R.string.next_setup_contracts_text),
                    onBack = { backStack.remove(SetupKey.Contracts) },
                    onEnd = { skipped ->
                        viewModel.endStep(SetupStep.CONTRACTS, skipped)
                        backStack.remove(SetupKey.Contracts)
                        goOnAfter(SetupStep.CONTRACTS)
                    },
                    section = contracts
                )
            }
            entry<SetupKey.Done> {
                SetupDonePage(
                    onClose = {
                        // Steps skipped are offered again from the overview
                        if (overview?.next == null) viewModel.close()
                        onClose()
                    }
                )
            }
        }
    )
}

private fun SetupStep.key(): SetupKey = when (this) {
    SetupStep.INCOME -> SetupKey.Income
    SetupStep.CATEGORIES -> SetupKey.Categories
    SetupStep.CONTRACTS -> SetupKey.Contracts
}
