package org.totschnig.myexpenses.next

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.Serializable
import org.totschnig.myexpenses.activity.BudgetActivity
import org.totschnig.myexpenses.activity.BudgetEdit
import org.totschnig.myexpenses.activity.MyExpensesV2
import org.totschnig.myexpenses.activity.SplashActivity
import org.totschnig.myexpenses.compose.accounts.AccountEventHandler
import org.totschnig.myexpenses.compose.main.AppEventHandler
import org.totschnig.myexpenses.compose.transactions.TransactionEvent
import org.totschnig.myexpenses.compose.transactions.TransactionEventHandler
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.AccountFlag
import org.totschnig.myexpenses.model.AccountGroupingKey
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.next.balance.MonthlyBalanceCard
import org.totschnig.myexpenses.next.balance.MonthlyBalanceFlow
import org.totschnig.myexpenses.next.contracts.ContractTransactionList
import org.totschnig.myexpenses.next.contracts.ContractsViewModel
import org.totschnig.myexpenses.next.contracts.NextContractsScreen
import org.totschnig.myexpenses.next.contracts.PaymentContractSection
import org.totschnig.myexpenses.next.contracts.TransactionActions
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.viewmodel.BudgetListViewModel
import org.totschnig.myexpenses.viewmodel.MyExpensesV2ViewModel
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.PageAccount
import org.totschnig.myexpenses.viewmodel.data.Transaction2

/** Tag for the budget feature request, to create a new budget instead of showing the budget list */
private const val TAG_ADD_BUDGET = "ADD_BUDGET"

/**
 * Entry point of the new UI, started from its own launcher icon ("MyExpenses Next").
 *
 * Reuses all of [MyExpensesV2] (view model, dialogs, event handling) and only replaces
 * what is exposed through its hooks:
 * - [MainTheme]: look and feel ([NextTheme])
 * - [MainScreen]: the main screen ([NextMainScreen])
 * - [dispatchCommand]: navigation to other screens ([NextRouter])
 */
class MyExpensesNext : MyExpensesV2() {

    private val budgetViewModel: BudgetListViewModel by viewModels()

    /** Actions on transactions shown outside of the list of an account, e.g. those of a contract */
    private val transactionEvents = object : TransactionEventHandler {
        override fun invoke(event: TransactionEvent, transaction: Transaction2) {
            handleTransactionEvent(event, transaction, isCurrentPage = true)
        }
    }
    private val contractsViewModel: ContractsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(budgetViewModel)
        injector.inject(contractsViewModel)
        if (prefHandler.getInt(PrefKey.CURRENT_VERSION, -1) == -1) {
            // Fresh install: onboarding is handled by the regular entry point
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
        }
    }

    @Composable
    override fun MainTheme(content: @Composable () -> Unit) {
        NextTheme(content)
    }

    @Composable
    override fun MainScreen(
        viewModel: MyExpensesV2ViewModel,
        accounts: List<FullAccount>,
        allCurrencies: List<CurrencyUnit>,
        availableFilters: List<AccountGroupingKey>,
        selectedAccountId: Long,
        onAppEvent: AppEventHandler,
        onAccountEvent: AccountEventHandler,
        onPrepareContextMenuItem: (itemId: Int) -> Boolean,
        onPrepareMenuItem: (itemId: Int) -> Boolean,
        flags: List<AccountFlag>,
        bankIcon: (@Composable (Modifier, Long) -> Unit)?,
        adView: @Composable (MutableState<Boolean>) -> Unit,
        isNavigationVisible: Boolean,
        isCurrencyUsed: suspend (String) -> Boolean,
        onCreateAsset: suspend (code: String, symbol: String, fractionDigits: Int, label: String?, commodityType: CommodityType) -> CurrencyUnit?,
        pageContent: @Composable (pageAccount: PageAccount, isCurrent: Boolean) -> Unit,
    ) {
        val budgets by remember { budgetViewModel.overviewBudgets() }
            .collectAsStateWithLifecycle(emptyList())
        val transactionActions = remember(accounts.size) { TransactionActions(transactionEvents, accounts.size) }
        LaunchedEffect(accounts) {
            val ownAccounts = accounts.filter { !it.isAggregate }
            contractsViewModel.setOwnAccounts(
                dailyIds = ownAccounts.filter { it.isDailyAccount }.mapTo(HashSet()) { it.id },
                labels = ownAccounts.associate { it.id to it.label }
            )
        }
        NextMainScreen(
            viewModel = viewModel,
            budgets = budgets,
            // Goes through the licence check, which shows the upgrade dialog without access
            onAddBudget = { contribFeatureRequested(ContribFeature.BUDGET, TAG_ADD_BUDGET) },
            onBudgetClick = { budgetId ->
                startActivity(Intent(this, BudgetActivity::class.java).apply {
                    putExtra(KEY_ROWID, budgetId)
                })
            },
            accounts = accounts,
            allCurrencies = allCurrencies,
            availableFilters = availableFilters,
            selectedAccountId = selectedAccountId,
            onAppEvent = onAppEvent,
            onAccountEvent = onAccountEvent,
            onPrepareContextMenuItem = onPrepareContextMenuItem,
            onPrepareMenuItem = onPrepareMenuItem,
            flags = flags,
            bankIcon = bankIcon,
            adView = adView,
            isNavigationVisible = isNavigationVisible,
            isCurrencyUsed = isCurrencyUsed,
            onCreateAsset = onCreateAsset,
            pageContent = pageContent,
            transactionList = { pageAccount ->
                // Day groups with the balance at the end of each day only make sense when sorted by date
                val grouping = if (pageAccount.sortBy == KEY_DATE) Grouping.DAY else Grouping.NONE
                Page(pageAccount.copy(grouping = grouping), accounts.size, true, v2 = true) { content ->
                    NextTransactionList(
                        content,
                        Modifier.weight(1f),
                        detailsContent = { transaction -> PaymentContractSection(contractsViewModel, transaction.id) }
                    )
                }
            },
            balanceCard = { modifier, onOpen ->
                val balance by contractsViewModel.balance.collectAsStateWithLifecycle()
                MonthlyBalanceCard(
                    state = balance,
                    currency = contractsViewModel.homeCurrency,
                    onOpen = onOpen,
                    onConsent = { contractsViewModel.setConsent(true) },
                    modifier = modifier
                )
            },
            contractsHaveNews = contractsViewModel.news.collectAsStateWithLifecycle().value
                .any { !it.isRead && !it.isIncome },
            balanceDetails = { onBack, onOpenContracts ->
                MonthlyBalanceFlow(contractsViewModel, onBack, onOpenContracts, transactionActions = transactionActions)
            },
            contractsContent = {
                val state by contractsViewModel.state.collectAsStateWithLifecycle()
                NextContractsScreen(
                    state = state,
                    currency = contractsViewModel.homeCurrency,
                    onConsent = contractsViewModel::setConsent,
                    onDismiss = contractsViewModel::dismiss,
                    onRestore = contractsViewModel::restore,
                    onRename = contractsViewModel::rename,
                    onSetArea = contractsViewModel::setArea,
                    onCreateArea = contractsViewModel::createArea,
                    onRenameArea = contractsViewModel::renameArea,
                    onDeleteArea = contractsViewModel::deleteArea,
                    onConfirm = contractsViewModel::confirm,
                    onSetInterval = contractsViewModel::setInterval,
                    onRemovePayee = contractsViewModel::removePayee,
                    onRemoveTargetAccount = contractsViewModel::removeTargetAccount,
                    onSetAmountRange = contractsViewModel::setAmountRange,
                    onMerge = contractsViewModel::merge,
                    onCancel = contractsViewModel::cancel,
                    onRevokeCancellation = contractsViewModel::revokeCancellation,
                    onSetReserve = contractsViewModel::setReserve,
                    news = contractsViewModel.news.collectAsStateWithLifecycle().value,
                    onMarkNewsRead = contractsViewModel::markNewsRead,
                    contractTransactions = { contract, modifier ->
                        ContractTransactionList(contractsViewModel, contract, modifier, transactionActions)
                    }
                )
            }
        )
    }

    override fun contribFeatureCalled(feature: ContribFeature, tag: Serializable?) {
        if (feature == ContribFeature.BUDGET && tag == TAG_ADD_BUDGET) {
            recordUsage(feature)
            startActivity(Intent(this, BudgetEdit::class.java))
        } else {
            super.contribFeatureCalled(feature, tag)
        }
    }

    override fun dispatchCommand(command: Int, tag: Any?): Boolean =
        NextRouter.route(this, command, tag) || super.dispatchCommand(command, tag)
}
