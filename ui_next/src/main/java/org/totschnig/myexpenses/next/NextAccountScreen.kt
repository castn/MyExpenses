package org.totschnig.myexpenses.next

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.StartScreen
import org.totschnig.myexpenses.compose.ColoredAmountText
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.compose.OverFlowMenu
import org.totschnig.myexpenses.compose.TEST_TAG_CAB
import org.totschnig.myexpenses.compose.TEST_TAG_FAB_TRANSACTIONS
import org.totschnig.myexpenses.compose.TooltipIconButton
import org.totschnig.myexpenses.compose.accounts.AccountEventHandler
import org.totschnig.myexpenses.compose.accounts.AccountIndicator
import org.totschnig.myexpenses.compose.accounts.AccountSummaryV2
import org.totschnig.myexpenses.compose.main.AppEvent
import org.totschnig.myexpenses.compose.main.AppEventHandler
import org.totschnig.myexpenses.compose.main.FloatingActionButtonMenu
import org.totschnig.myexpenses.compose.main.balanceForType
import org.totschnig.myexpenses.compose.main.getBalanceContentDescription
import org.totschnig.myexpenses.compose.main.icon
import org.totschnig.myexpenses.compose.main.parseMenu
import org.totschnig.myexpenses.compose.main.validatedBalanceType
import org.totschnig.myexpenses.compose.transactions.Action
import org.totschnig.myexpenses.compose.transactions.ActionMenu
import org.totschnig.myexpenses.compose.transactions.FabStyle
import org.totschnig.myexpenses.compose.transactions.TradeScreen
import org.totschnig.myexpenses.compose.transactions.ViewOptionsMenu
import org.totschnig.myexpenses.dialog.MenuItem
import org.totschnig.myexpenses.model.BalanceType
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.preference.PreferenceState
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.util.enumValueOrDefault
import org.totschnig.myexpenses.viewmodel.MyExpensesV2ViewModel
import org.totschnig.myexpenses.viewmodel.data.BaseAccount
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.PageAccount
import java.math.RoundingMode

/**
 * Account page of the new design, showing the transactions of the selected account.
 *
 * Reuses the functionality of the classic TransactionScreen: the menus, the selection mode,
 * the FAB for new transactions and the trade dialog for portfolios. Transactions are shown with
 * [transactionList], portfolios still with the classic page ([pageContent]).
 * Unlike the classic screen it has no tabs for switching between accounts, that is done in the overview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextAccountScreen(
    viewModel: MyExpensesV2ViewModel,
    accounts: List<FullAccount>,
    selectedAccountId: Long,
    visibleActionItems: Int,
    onEvent: AppEventHandler,
    onAccountEvent: AccountEventHandler,
    onPrepareContextMenuItem: (Int) -> Boolean,
    onPrepareMenuItem: (Int) -> Boolean,
    pageContent: @Composable (PageAccount, Boolean) -> Unit,
    /** The redesigned transaction list for the given account */
    transactionList: @Composable (PageAccount) -> Unit,
    allCurrencies: List<CurrencyUnit>,
    isCurrencyUsed: suspend (String) -> Boolean,
    onCreateAsset: suspend (code: String, symbol: String, fractionDigits: Int, label: String?, commodityType: CommodityType) -> CurrencyUnit?,
    windowInsets: WindowInsets,
    bankIcon: (@Composable (Modifier, Long) -> Unit)? = null,
    /** Back to the overview, null if both are shown side by side */
    onBack: (() -> Unit)? = null,
) {
    LaunchedEffect(Unit) {
        viewModel.setLastVisited(StartScreen.Transactions)
    }

    // accountList also contains the aggregate accounts, but is restricted by the account filter of the classic screen
    val accountList = viewModel.accountList.collectAsState(emptyList()).value
    val currentAccount: BaseAccount = accountList.find { it.id == selectedAccountId }
        ?: accounts.find { it.id == selectedAccountId }
        ?: accountList.firstOrNull()
        ?: return
    val isPortfolio = (currentAccount as? FullAccount)?.isPortfolio == true
    val accountColor = Color(currentAccount.color(LocalResources.current))
    val context = LocalContext.current

    var showTradeScreen by rememberSaveable { mutableStateOf<Action?>(null) }

    Scaffold(
        contentWindowInsets = windowInsets,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            Crossfade(
                targetState = viewModel.selectionState.value.isNotEmpty(),
                label = "TopBarTransition"
            ) { selectionMode ->
                if (selectionMode) {
                    SelectionTopBar(
                        viewModel = viewModel,
                        currentAccount = currentAccount,
                        windowInsets = windowInsets,
                        onEvent = onEvent,
                        onPrepareContextMenuItem = onPrepareContextMenuItem
                    )
                } else {
                    TopAppBar(
                        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        navigationIcon = {
                            if (onBack != null) {
                                TooltipIconButton(
                                    tooltip = stringResource(R.string.menu_close),
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    onClick = onBack
                                )
                            }
                        },
                        title = {
                            Text(
                                text = currentAccount.labelV2(context),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        actions = {
                            AccountActions(
                                viewModel = viewModel,
                                currentAccount = currentAccount,
                                selectedAccountId = selectedAccountId,
                                visibleActionItems = visibleActionItems,
                                onEvent = onEvent,
                                onPrepareMenuItem = onPrepareMenuItem
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if ((currentAccount as? FullAccount)?.sealed == true) {
                FloatingActionButton(
                    onClick = { },
                    modifier = Modifier
                        .testTag(TEST_TAG_FAB_TRANSACTIONS)
                        .semantics { disabled() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Icon(Icons.Default.Lock, stringResource(R.string.account_closed))
                }
            } else {
                val scope = rememberCoroutineScope()
                val staticAction = if (isPortfolio) null else
                    viewModel.defaultAction.collectAsState("LastVisited").value
                        .takeIf { it != "LastVisited" }
                        ?.let { enumValueOrDefault(it, Action.Expense) }
                val lastAction = if (isPortfolio) viewModel.lastActionPortfolio else viewModel.lastAction
                FloatingActionButtonMenu(
                    modifier = Modifier.testTag(TEST_TAG_FAB_TRANSACTIONS),
                    primaryAction = staticAction ?: lastAction.flow.collectAsState(Action.Expense).value,
                    isStandard = viewModel.fabStyle.collectAsState(FabStyle.Standard).value == FabStyle.Standard,
                    containerColor = accountColor,
                    actions = if (isPortfolio) Action.PORTFOLIO_ACTIONS else Action.STANDARD_ACTIONS
                ) { action ->
                    if (staticAction == null) {
                        scope.launch { lastAction.set(action) }
                    }
                    if (action in Action.PORTFOLIO_ACTIONS) {
                        showTradeScreen = action
                    } else {
                        onEvent(
                            AppEvent.CreateTransaction(
                                action = action,
                                transferEnabled = accounts.size > 1
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            BalanceHeader(
                account = currentAccount,
                bankIcon = bankIcon,
                onDisplayBalanceTypeChange = if (isPortfolio) null else viewModel::persistBalanceType,
                onCopyBalance = { onEvent(AppEvent.CopyToClipBoard(it)) },
                onSetNewBalance = if (isPortfolio) null else {
                    { onEvent(AppEvent.MenuItemClicked(R.id.NEW_BALANCE_COMMAND)) }
                },
                onAccountEvent = onAccountEvent,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Box(Modifier.weight(1f)) {
                val pageAccount = remember(currentAccount) { currentAccount.toPageAccount(context) }
                if (pageAccount.isPortfolio) pageContent(pageAccount, true) else transactionList(pageAccount)
            }
        }
    }

    showTradeScreen?.let { tradeAction ->
        TradeScreen(
            onDismiss = { showTradeScreen = null },
            onSave = { intent, stayOpen ->
                onEvent(AppEvent.SaveTrade(intent))
                if (!stayOpen) showTradeScreen = null
            },
            portfolio = currentAccount as FullAccount,
            roundingMode = viewModel.getRoundingMode(currentAccount.id)
                .collectAsState(RoundingMode.HALF_UP).value,
            onRoundingModeChange = { viewModel.setRoundingMode(currentAccount.id, it) },
            reportingCurrency = currentAccount.currencyUnit,
            assets = allCurrencies,
            fundingAccounts = accounts
                .filter {
                    !it.isPortfolio && !it.sealed &&
                            it.currencyUnit.code == currentAccount.currencyUnit.code &&
                            it.id != currentAccount.id
                }
                .map { it.id to it.labelV2(context) },
            targetPortfolios = accounts
                .filter { it.isPortfolio && it.id != currentAccount.id }
                .map { it.id to it.labelV2(context) },
            initialAction = tradeAction,
            onCreateAsset = onCreateAsset,
            isCurrencyUsed = isCurrencyUsed,
            onLookupMatchingTransactions = { accountId, total, date, isBuy ->
                viewModel.findMatchingTransactions(
                    accountId,
                    total,
                    date,
                    currentAccount.currencyUnit,
                    isBuy
                )
            }
        )
    }
}

/**
 * Top bar while transactions are selected: number and sum of the selection, and the actions for it
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    viewModel: MyExpensesV2ViewModel,
    currentAccount: BaseAccount,
    windowInsets: WindowInsets,
    onEvent: AppEventHandler,
    onPrepareContextMenuItem: (Int) -> Boolean,
) {
    BackHandler {
        viewModel.clearSelection()
    }
    val context = LocalContext.current
    TopAppBar(
        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        navigationIcon = {
            TooltipIconButton(
                tooltip = stringResource(R.string.menu_close),
                imageVector = Icons.AutoMirrored.Filled.ArrowBack
            ) { viewModel.clearSelection() }
        },
        title = {
            ColoredAmountText(
                modifier = Modifier.testTag(TEST_TAG_CAB),
                prefix = "${viewModel.selectionState.value.size}  (Σ: ",
                amount = viewModel.selectedTransactionSum,
                currency = currentAccount.currencyUnit,
                postfix = ")",
                colorFix = false
            )
        },
        actions = {
            OverFlowMenu(
                menu = remember(viewModel.selectionState.value.size) {
                    parseMenu(
                        context = context,
                        menuRes = R.menu.transactionlist_context,
                        onPrepareMenuItem = onPrepareContextMenuItem
                    ) {
                        onEvent(AppEvent.ContextMenuItemClicked(it))
                    }
                }
            )
        }
    )
}

/**
 * The configurable account actions: the first [visibleActionItems] as icons, the rest in the overflow menu
 */
@Composable
private fun AccountActions(
    viewModel: MyExpensesV2ViewModel,
    currentAccount: BaseAccount,
    selectedAccountId: Long,
    visibleActionItems: Int,
    onEvent: AppEventHandler,
    onPrepareMenuItem: (Int) -> Boolean,
) {
    @Composable
    fun isChecked(menuItem: MenuItem): Boolean = when (menuItem) {
        MenuItem.Search -> viewModel.filterPersistence.getValue(selectedAccountId)
            .whereFilter
            .collectAsState(null).value != null

        MenuItem.ShowStatusHandle -> viewModel.showStatusHandle.flow
            .collectAsState(initial = false).value

        else -> false
    }

    val menuConfig = viewModel.transactionMenuAccessor.statefulFlow
        .collectAsStateWithLifecycle(PreferenceState.Loading).value
    if (menuConfig !is PreferenceState.Loaded) return

    val filteredItems = menuConfig.value.filter { onPrepareMenuItem(it.id) }
    // no need to show overflow menu if there is only one item
    val quickItems = if (filteredItems.size > 1) filteredItems.take(visibleActionItems) else filteredItems
    val overflowItems = filteredItems - quickItems.toSet()

    quickItems.forEach { menuItem ->
        if (menuItem == MenuItem.Tune) {
            ViewOptionsMenu(currentAccount = currentAccount, onEvent = onEvent)
        } else {
            val isChecked = if (menuItem.isCheckable) isChecked(menuItem) else null
            TooltipIconButton(menuItem, isChecked == true) {
                onEvent(AppEvent.MenuItemClicked(menuItem.id, isChecked?.not()))
            }
        }
    }

    ActionMenu(
        currentAccount = currentAccount,
        items = overflowItems,
        onEvent = onEvent,
        isChecked = { isChecked(it) }
    )
}

/**
 * Balance of the account in bold above the list. Tapping it expands the summary of the account
 * together with the actions for copying the balance and setting a new one.
 */
@Composable
private fun BalanceHeader(
    account: BaseAccount,
    bankIcon: (@Composable (Modifier, Long) -> Unit)?,
    onDisplayBalanceTypeChange: ((BalanceType) -> Unit)?,
    onCopyBalance: (String) -> Unit,
    onSetNewBalance: (() -> Unit)?,
    onAccountEvent: AccountEventHandler,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)
    val balanceType = account.validatedBalanceType
    val displayBalance = if ((account as? FullAccount)?.isPortfolio == true)
        account.effectiveBalance else account.balanceForType

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = stringResource(R.string.content_description_show_balance_details)) {
                    isExpanded = !isExpanded
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColoredAmountText(
                amount = displayBalance,
                currency = account.currencyUnit,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                softWrap = false
            )
            if (balanceType != BalanceType.CURRENT) {
                // Only non-default balance types are worth pointing out
                Icon(
                    imageVector = balanceType.icon,
                    contentDescription = stringResource(account.getBalanceContentDescription(balanceType)),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(16.dp),
                    tint = when (balanceType) {
                        BalanceType.CLEARED -> colorResource(R.color.CLEARED)
                        BalanceType.RECONCILED -> colorResource(R.color.RECONCILED)
                        else -> colorResource(R.color.UNRECONCILED)
                    }
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(isExpanded) {
            val currencyFormatter = LocalCurrencyFormatter.current
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    if (account is FullAccount) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccountIndicator(account, bankIcon)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(account.getBalanceContentDescription(balanceType)),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        AccountSummaryV2(account, onDisplayBalanceTypeChange, onAccountEvent)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            onCopyBalance(currencyFormatter.convAmount(displayBalance, account.currencyUnit))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_text))
                        }
                        if (account is FullAccount && onSetNewBalance != null) {
                            TextButton(onClick = onSetNewBalance) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.new_balance))
                            }
                        }
                    }
                }
            }
        }
    }
}
