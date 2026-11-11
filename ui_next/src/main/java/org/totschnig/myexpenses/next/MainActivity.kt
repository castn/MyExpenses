package org.totschnig.myexpenses.next

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.compose.collectAsLazyPagingItems
import java.math.RoundingMode
import java.util.Optional
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BaseMyExpenses
import org.totschnig.myexpenses.activity.PriceHistory
import org.totschnig.myexpenses.compose.accounts.AccountEvent
import org.totschnig.myexpenses.compose.accounts.AccountEventHandler
import org.totschnig.myexpenses.compose.accounts.PortfolioSetupDialog
import org.totschnig.myexpenses.compose.filter.FilterCard
import org.totschnig.myexpenses.compose.filter.FilterDialog
import org.totschnig.myexpenses.compose.filter.FilterHandler
import org.totschnig.myexpenses.compose.filter.TYPE_COMPLEX
import org.totschnig.myexpenses.compose.main.AppEvent
import org.totschnig.myexpenses.compose.main.AppEventHandler
import org.totschnig.myexpenses.compose.transactions.Action
import org.totschnig.myexpenses.compose.transactions.ImportTradesDialog
import org.totschnig.myexpenses.compose.transactions.RenderType
import org.totschnig.myexpenses.compose.transactions.TradeEvent
import org.totschnig.myexpenses.compose.transactions.TradeList
import org.totschnig.myexpenses.compose.transactions.TradeScreen
import org.totschnig.myexpenses.compose.transactions.TransactionEvent
import org.totschnig.myexpenses.compose.transactions.TransactionEventHandler
import org.totschnig.myexpenses.designsystem.ActionChipRow
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.ContribPayload
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.onboarding.NextOnboarding
import org.totschnig.myexpenses.next.search.SearchResult
import org.totschnig.myexpenses.next.settings.NextSettings
import org.totschnig.myexpenses.next.settings.SettingsGroup
import org.totschnig.myexpenses.next.transactions.AppFixedTransactions
import org.totschnig.myexpenses.next.transactions.FixedTransactionsViewModel
import org.totschnig.myexpenses.next.transactions.TransactionActions
import org.totschnig.myexpenses.next.transactions.TransactionEditing
import org.totschnig.myexpenses.next.transactions.toShown
import org.totschnig.myexpenses.preference.isOnboardingCompleted
import org.totschnig.myexpenses.provider.KEY_COMMODITY
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.util.ads.AdHandlerV2
import org.totschnig.myexpenses.util.crashreporting.CrashHandler.Companion.report
import org.totschnig.myexpenses.viewmodel.CurrencyViewModel
import org.totschnig.myexpenses.viewmodel.MainViewModel
import org.totschnig.myexpenses.viewmodel.SumInfo
import org.totschnig.myexpenses.viewmodel.data.AggregateAccount
import org.totschnig.myexpenses.viewmodel.data.BaseAccount
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.HeaderData
import org.totschnig.myexpenses.viewmodel.data.PageAccount
import org.totschnig.myexpenses.viewmodel.data.Transaction2
import timber.log.Timber

/**
 * The main screen of the app, started by the launcher, widgets, notifications and after a restore
 * (see `MainScreenEntry` in :myExpenses).
 *
 * It still builds on [BaseMyExpenses] for the transaction list of an account, the actions on
 * transactions, the context menu and the commands of the menus, until those are ported to the
 * new UI. Here it adds:
 * - the main screen of the new UI ([NextMainScreen]) in its theme ([NextTheme])
 * - the dialogs for portfolios and trades
 * - navigation to screens of the new UI ([NextRouter])
 */
class MainActivity : BaseMyExpenses<MainViewModel>() {

    private val currencyViewModel: CurrencyViewModel by viewModels()

    private lateinit var adHandler: AdHandlerV2

    private var showPortfolioSetup by mutableStateOf(false)
    private var portfolioToEditId by mutableStateOf<Long?>(null)

    /** Actions on transactions shown outside of the list of an account, e.g. those of a contract */
    private val transactionEvents = object : TransactionEventHandler {
        override fun invoke(event: TransactionEvent, transaction: Transaction2) {
            handleTransactionEvent(event, transaction, isCurrentPage = true)
        }
    }

    /** The ledger of the process, shared with other screens, see [NextData] */
    private val nextData get() = NextData.of(this)

    // The features get the ledger, the facts of other features and the home currency from NextData,
    // see the README of the ledger package, never from another view model.
    private val features by lazy { NextFeatures(this, nextData) }

    private val fixedTransactionsViewModel by viewModelsOf {
        FixedTransactionsViewModel(application, injector.currencyContext(), prefHandler)
    }

    // Changes transactions from their details, see [TransactionEditActions]
    private val transactionEditViewModel get() = features.transactions.viewModel

    override fun handleRootWindowInsets() {}

    override val currentAccount: BaseAccount?
        get() = viewModel.accountList.value.find { it.id == selectedAccountId }

    @get:Composable
    override val transactionListWindowInsets: WindowInsets
        get() = WindowInsets()

    override val accountCount: Int
        get() = viewModel.accountDataV2.value?.getOrNull()?.size ?: 0

    override fun finishActionMode() {
        viewModel.selectionState.value = emptyList()
    }

    private val shouldShowAds
        get() = !adHandlerFactory.isAdDisabled && adHandlerFactory.isInitialized

    private fun maybeRequestNewInterstitial() {
        if (shouldShowAds) {
            runCatching { adHandler.maybeRequestNewInterstitial(this) }.onFailure { report(it) }
        }
    }

    override fun onEditTransactionResult(isOk: Boolean) {
        if (isOk) {
            val adHandled = shouldShowAds && adHandler.onEditTransactionResult(this)
            if (!adHandled) {
                reviewManager.onEditTransactionResult(this)
            }
        }
    }

    override fun editAccount(account: FullAccount) {
        if (account.isPortfolio) {
            portfolioToEditId = account.id
        } else {
            super.editAccount(account)
        }
    }

    override fun injectDependencies() {
        injector.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        with(injector) {
            inject(viewModel)
            inject(currencyViewModel)
        }
        if (!prefHandler.isOnboardingCompleted) {
            // Fresh install: the first start of the new UI, which opens this screen again once done
            startActivity(Intent(this, NextOnboarding::class.java))
            finish()
            return
        }

        adHandler = adHandlerFactory.createV2()
        maybeRequestNewInterstitial()

        // Automatic decisions for new transactions, while the app is shown, see NextData
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { nextData.applyAutomaticDecisions() }
        }

        setContent {
            NextTheme {
                val result = viewModel.accountDataV2.collectAsStateWithLifecycle().value
                val availableFilters =
                    viewModel.availableGroupFilters.collectAsStateWithLifecycle().value
                when {
                    result?.isFailure == true -> LoadingFailure(result.exceptionOrNull()!!)

                    result == null || availableFilters == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    else -> Main(result.getOrThrow())
                }
            }
        }
    }

    @Composable
    private fun LoadingFailure(throwable: Throwable) {
        val (message, forceQuit) = throwable.processDataLoadingFailure()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensionResource(R.dimen.padding_main_screen)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message)
            // Spacing by hand, since Arrangement.Center takes precedence over Arrangement.spacedBy
            Spacer(modifier = Modifier.height(4.dp))
            if (!forceQuit) {
                Button(onClick = { dispatchCommand(R.id.SAFE_MODE_COMMAND, null) }) {
                    Text(stringResource(R.string.safe_mode))
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Button(onClick = { dispatchCommand(R.id.QUIT_COMMAND, null) }) {
                Text(stringResource(R.string.button_label_close))
            }
        }
    }

    @Composable
    private fun Main(accounts: List<FullAccount>) {
        val selectedAccountIdFromState = viewModel.selectedAccountId.collectAsState().value
        LaunchedEffect(
            viewModel.accountList.collectAsState().value.isNotEmpty(),
            selectedAccountIdFromState,
            // reloading when filter changes, because aggregate accounts have same id (0)
            viewModel.activeFilter.collectAsState().value
        ) {
            currentAccount?.let { account ->
                sumInfo.value = SumInfo.EMPTY
                viewModel.sumInfo(account.toPageAccount(this@MainActivity)).collect {
                    sumInfo.value = it
                }
            }
        }
        val banks = viewModel.banks.collectAsState()
        var isNavigationVisible by rememberSaveable { mutableStateOf(false) }
        var showImportTrades by rememberSaveable { mutableStateOf(false) }
        val currencies by currencyViewModel.currencyUnits.collectAsState(emptyList())

        TradeEditing(accounts, currencies)
        PortfolioSetup(accounts, currencies)

        val onAppEvent = object : AppEventHandler {
            override fun invoke(event: AppEvent) {
                when (event) {
                    AppEvent.CreateAccount -> createAccount()

                    AppEvent.CreatePortfolio -> showPortfolioSetup = true

                    is AppEvent.CreateTransaction -> if (preCreateRowCheckForSealed()) {
                        when (event.action) {
                            Action.Scan -> contribFeatureRequested(ContribFeature.OCR)

                            else -> createRow(
                                event.action.type,
                                event.transferEnabled,
                                event.action == Action.Income || event.action == Action.Deposit
                            )
                        }
                    }

                    is AppEvent.SetAccountGrouping -> viewModel.setGrouping(event.newGrouping)

                    is AppEvent.SetTransactionGrouping -> viewModel.persistGroupingV2(
                        event.grouping
                    )

                    is AppEvent.SetTransactionSort -> viewModel.persistSortV2(event.transactionSort)

                    AppEvent.PrintBalanceSheet -> printBalanceSheet()

                    is AppEvent.ContextMenuItemClicked -> onContextItemClicked(event.itemId)

                    is AppEvent.MenuItemClicked -> when (event.itemId) {
                        R.id.IMPORT_TRADES_COMMAND -> showImportTrades = true
                        else -> dispatchCommand(event.itemId, event.tag)
                    }

                    // The new UI sorts the accounts on its own screen
                    AppEvent.Sort -> Unit

                    is AppEvent.CopyToClipBoard -> copyToClipboard(event.text)

                    AppEvent.ToggleNavigation -> isNavigationVisible = !isNavigationVisible

                    is AppEvent.SaveTrade -> (currentAccount as? FullAccount)?.let {
                        viewModel.saveTrade(it, event.intent)
                    }

                    is AppEvent.ShowUpgrade -> contribFeatureRequested(event.feature)
                }
            }
        }

        val onAccountEvent = object : AccountEventHandler {
            override fun invoke(event: AccountEvent, account: FullAccount) {
                when (event) {
                    is AccountEvent.Delete -> confirmAccountDelete(account)

                    is AccountEvent.Edit -> editAccount(account)

                    is AccountEvent.SetFlag -> viewModel.setFlag(account.id, event.flagId)

                    is AccountEvent.ToggleDynamicExchangeRate -> toggleDynamicExchangeRate(account)

                    is AccountEvent.ToggleExcludeFromTotals -> toggleExcludeFromTotals(account)

                    is AccountEvent.ToggleSealed -> toggleAccountSealed(account)

                    is AccountEvent.ViewPriceHistory -> startActivity(
                        Intent(this@MainActivity, PriceHistory::class.java).apply {
                            putExtra(KEY_COMMODITY, event.commodity)
                            putExtra(KEY_CURRENCY, account.currencyUnit.code)
                        }
                    )
                }
            }
        }

        MainScreen(
            accounts = accounts,
            currencies = currencies,
            selectedAccountId = selectedAccountIdFromState,
            onAppEvent = onAppEvent,
            onAccountEvent = onAccountEvent,
            bankIcon = { modifier, id ->
                banks.value.find { it.id == id }?.let { bank ->
                    bankingFeature.bankIconRenderer?.invoke(modifier, bank)
                }
            },
            isNavigationVisible = isNavigationVisible
        )

        if (showImportTrades) {
            (currentAccount as? FullAccount)?.let { fullAccount ->
                ImportTradesDialog(
                    onDismiss = { showImportTrades = false },
                    onImport = { intents, onProgress ->
                        viewModel.saveTrades(fullAccount, intents, onProgress = onProgress)
                    },
                    portfolio = fullAccount,
                    roundingMode = viewModel.getRoundingMode(fullAccount.id)
                        .collectAsState(RoundingMode.HALF_UP).value,
                    assets = currencies,
                    fundingAccounts = accounts
                        .filter {
                            !it.isPortfolio &&
                                it.currencyUnit.code == fullAccount.currencyUnit.code &&
                                it.id != fullAccount.id
                        }
                        .map { it.id to it.labelV2(this@MainActivity) }
                )
            }
        }
    }

    /** Editing a trade of a portfolio, see [editTrade] */
    @Composable
    private fun TradeEditing(accounts: List<FullAccount>, currencies: List<CurrencyUnit>) {
        val tradeToEdit by viewModel.tradeToEdit.collectAsStateWithLifecycle()
        val trade = tradeToEdit ?: return
        val fullAccount = accounts.find { it.id == trade.portfolioId } ?: return
        TradeScreen(
            onDismiss = { viewModel.clearTradeToEdit() },
            onSave = { intent, stayOpen ->
                viewModel.saveTrade(fullAccount, intent)
                if (stayOpen) {
                    report(
                        IllegalStateException(
                            "did not expect stayOpen to be true when editing trade"
                        )
                    )
                }
            },
            portfolio = fullAccount,
            roundingMode = viewModel.getRoundingMode(
                fullAccount.id
            ).collectAsState(RoundingMode.HALF_UP).value,
            onRoundingModeChange = { viewModel.setRoundingMode(fullAccount.id, it) },
            reportingCurrency = fullAccount.currencyUnit,
            assets = currencies,
            fundingAccounts = accounts
                .filter {
                    !it.isPortfolio && !it.sealed &&
                        it.currencyUnit.code == fullAccount.currencyUnit.code
                }
                .map { it.id to it.labelV2(this@MainActivity) },
            targetPortfolios = accounts
                .filter { it.isPortfolio && it.id != fullAccount.id }
                .map { it.id to it.labelV2(this@MainActivity) },
            initialTrade = trade,
            canAddMultipleCostLegs = viewModel.canAddMultipleCostLegs,
            onShowUpgrade = { contribFeatureRequested(ContribFeature.PORTFOLIO) },
            onLookupMatchingTransactions = { accountId, total, date, isBuy ->
                viewModel.findMatchingTransactions(
                    accountId,
                    total,
                    date,
                    fullAccount.currencyUnit,
                    isBuy
                )
            }
        )
    }

    /** Creating a portfolio, or editing one, see [editAccount] */
    @Composable
    private fun PortfolioSetup(accounts: List<FullAccount>, currencies: List<CurrencyUnit>) {
        if (!showPortfolioSetup && portfolioToEditId == null) return
        val accountTypes by viewModel.accountTypes.collectAsState(emptyList())
        val portfolioToEdit = portfolioToEditId?.let { id -> accounts.find { it.id == id } }
        PortfolioSetupDialog(
            onDismiss = {
                showPortfolioSetup = false
                portfolioToEditId = null
            },
            onDeleteAsset = { confirmAccountDelete(it) },
            onConfirm = { label, currency, color, exchangeRate, dynamicExchangeRates, type ->
                val editedId = portfolioToEditId
                if (editedId != null) {
                    viewModel.updatePortfolio(
                        editedId,
                        label,
                        currency,
                        color,
                        exchangeRate,
                        dynamicExchangeRates,
                        type
                    )
                } else {
                    viewModel.createPortfolio(
                        label,
                        currency,
                        color,
                        exchangeRate,
                        dynamicExchangeRates,
                        type
                    )
                }
                showPortfolioSetup = false
                portfolioToEditId = null
            },
            availableCurrencies = currencies.filter { it.commodityType == CommodityType.FIAT },
            availableAccountTypes = accountTypes,
            homeCurrency = currencyContext.homeCurrencyUnit,
            initialPortfolio = portfolioToEdit
        )
    }

    @Composable
    private fun MainScreen(
        accounts: List<FullAccount>,
        currencies: List<CurrencyUnit>,
        selectedAccountId: Long,
        onAppEvent: AppEventHandler,
        onAccountEvent: AccountEventHandler,
        bankIcon: @Composable (Modifier, Long) -> Unit,
        isNavigationVisible: Boolean
    ) {
        val transactionActions =
            remember(accounts.size) { TransactionActions(transactionEvents, accounts.size) }
        val fixedTransactions =
            remember(transactionActions) {
                AppFixedTransactions(fixedTransactionsViewModel, transactionActions)
            }
        // What the new UI adds to the details of a transaction, in the accounts and in lists of
        // other screens
        val transactionDetails: @Composable (ShownTransaction) -> Unit = { transaction ->
            features.all.forEach { it.TransactionSections(transaction) }
            ActionChipRow { features.all.forEach { it.TransactionChips(transaction) } }
        }
        val rows = features.all.mapNotNull { it.rowDecorations() }
        val editState by transactionEditViewModel.state.collectAsStateWithLifecycle()
        CompositionLocalProvider(*features.all.flatMap { it.provided() }.toTypedArray()) {
            NextMainScreen(
                viewModel = viewModel,
                accounts = accounts,
                allCurrencies = currencies,
                selectedAccountId = selectedAccountId,
                onAppEvent = onAppEvent,
                onAccountEvent = onAccountEvent,
                onPrepareContextMenuItem = ::isContextMenuItemVisible,
                onPrepareMenuItem = { itemId -> currentAccount.isMenuItemVisible(itemId) },
                bankIcon = bankIcon,
                adView = { isLoadedState -> adHandler.Banner(isLoadedState) },
                isNavigationVisible = isNavigationVisible,
                isCurrencyUsed = { currencyViewModel.isCurrencyUsed(it) },
                onCreateAsset = { code, symbol, fractionDigits, label, type ->
                    currencyViewModel.createAsset(code, symbol, fractionDigits, label, type)
                },
                // Portfolios still with the classic page
                pageContent = { pageAccount, isCurrent -> PortfolioPage(pageAccount, isCurrent) },
                transactionList = { pageAccount, search ->
                    // Day groups with the balance at the end of each day only make sense when
                    // sorted by date
                    val byDate = pageAccount.sortBy == KEY_DATE
                    val grouping = if (byDate) Grouping.DAY else Grouping.NONE
                    Page(
                        pageAccount.copy(grouping = grouping),
                        accounts.size,
                        true,
                        v2 = true,
                        withFilterUi = false,
                        transactionList = { content ->
                            if (content.isFiltered) {
                                (content.headerData as? HeaderData)?.let { headerData ->
                                    val items = content.lazyPagingItems
                                    SearchResult(
                                        headerData,
                                        count = items.itemCount.takeIf {
                                            items.loadState.append.endOfPaginationReached
                                        },
                                        onReset = search::reset
                                    )
                                }
                            }
                            NextTransactionList(
                                content,
                                Modifier.weight(1f),
                                detailsContent = { transactionDetails(it.toShown()) },
                                suggestedCategory =
                                    rows.firstNotNullOfOrNull { it.suggestedCategory } ?: { null },
                                categoryChip =
                                    rows.firstNotNullOfOrNull { it.categoryChip } ?: { null },
                                tagChips = rows.firstNotNullOfOrNull { it.tagChips } ?: { null },
                                isMarker = rows.firstNotNullOfOrNull { it.isMarker } ?: { false },
                                payeeImage = rows.firstNotNullOfOrNull {
                                    it.payeeImage
                                } ?: { null },
                                payeeImageDialog = rows.firstNotNullOfOrNull {
                                    it.payeeImageDialog
                                },
                                editing = TransactionEditing(editState, transactionEditViewModel)
                            )
                        }
                    )
                },
                fixedTransactions = fixedTransactions,
                transactionDetails = transactionDetails,
                onInsightSettings = ::openInsightSettings,
                features = features.all
            )
        }
    }

    /** The trades of a portfolio, with the filter of its page */
    @Composable
    private fun PortfolioPage(account: PageAccount, isCurrentPage: Boolean) {
        val coroutineScope = rememberCoroutineScope()
        val preferredSearchType = viewModel.preferredSearchType.flow.collectAsState(
            TYPE_COMPLEX
        ).value
        if (showFilterDialog && isCurrentPage) {
            Timber.d("showFilterDialog for page ${account.label}")
            FilterDialog(
                account = account,
                sumInfo = sumInfo.value,
                // we are only interested in the current value, since as soon as we persist new
                // value, the dialog is dismissed
                // noinspection StateFlowValueCalledInComposition
                criterion = currentFilter.whereFilter.value,
                initialPreferredSearchType = preferredSearchType,
                onDismissRequest = {
                    showFilterDialog = false
                },
                onConfirmRequest = { preferredSearchType, criterion ->
                    coroutineScope.launch {
                        viewModel.preferredSearchType.set(preferredSearchType)
                        currentFilter.persist(criterion)
                        showFilterDialog = false
                    }
                }
            )
        }
        val renderType by viewModel.renderer.collectAsState(initial = RenderType.New)
        val lazyPagingItems = viewModel.getTrades(account).collectAsLazyPagingItems()
        val isProcessingFilter = remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            val persistence =
                remember(account.id) { viewModel.filterPersistence.getValue(account.id) }
            val filter = persistence.whereFilter.collectAsState(null)
            filter.value?.let { filter ->
                FilterHandler(account, "confirmFilterDirect_${account.id}", { oldValue, newValue ->
                    if (newValue != null && oldValue != null) {
                        lifecycleScope.launch {
                            persistence.replaceCriterion(oldValue, newValue)
                        }
                    }
                }) {
                    FilterCard(
                        filter,
                        editFilter = { handleEdit(it) },
                        clearAllFilter = { confirmClearFilter() },
                        clearFilter = {
                            if (isProcessingFilter.value) {
                                Timber.d("double click: ignoring filter clear request")
                            } else {
                                isProcessingFilter.value = true
                                lifecycleScope.launch {
                                    try {
                                        persistence.removeCriterion(it)
                                    } finally {
                                        isProcessingFilter.value = false
                                    }
                                }
                            }
                        }
                    )
                }
            }
            TradeList(
                trades = lazyPagingItems,
                modifier = Modifier.weight(1f),
                renderType = renderType,
                onEvent = if (account.sealed) {
                    null
                } else {
                    { event, trade ->
                        when (event) {
                            TradeEvent.Edit -> lifecycleScope.launch {
                                if (checkSealed(listOf(trade.id))) {
                                    viewModel.editTrade(trade)
                                }
                            }

                            TradeEvent.Delete -> lifecycleScope.launch {
                                // Trades are usually unreconciled upon creation
                                delete(listOf(trade.id to CrStatus.UNRECONCILED))
                            }
                        }
                    }
                }
            )
        }
    }

    private fun openInsightSettings() {
        startActivity(NextSettings.intent(this, SettingsGroup.INSIGHTS))
    }

    override suspend fun accountForNewTransaction() = Optional.ofNullable(
        when (val account = currentAccount) {
            is FullAccount -> account

            is AggregateAccount -> viewModel.accountDataV2.value?.getOrNull()
                ?.filter { it.currency == account.currency }
                ?.maxByOrNull { it.lastUsed }

            else -> null
        }
    )

    override fun editTrade(transactionId: Long) {
        viewModel.loadTrade(transactionId)
    }

    // Calls from BaseMyExpenses, which still updates the menu of the old main screen
    override fun invalidateOptionsMenu() {}

    override fun handleIntent(intent: Intent) {
        viewModel.handleIntent(intent)
        showTransactionFromIntent(intent)
    }

    override fun onTransactionSaved(transactionId: Long) {
        features.all.forEach { it.onTransactionSaved(transactionId) }
    }

    override fun contribFeatureCalled(feature: ContribFeature, tag: ContribPayload?) {
        if (features.all.none {
                it.onContribFeatureCalled(feature, tag)
            }
        ) {
            super.contribFeatureCalled(feature, tag)
        }
    }

    override fun dispatchCommand(command: Int, tag: Any?): Boolean =
        NextRouter.route(this, command, tag) || super.dispatchCommand(command, tag)
}

/**
 * A view model of the activity created by [create], e.g. with the facts it reads. [create] gets the
 * saved state of the view model, for what has to survive when Android ends the process.
 */
internal inline fun <reified VM : ViewModel> ComponentActivity.viewModelsOf(
    noinline create: (SavedStateHandle) -> VM
) = viewModels<VM> { viewModelFactory { initializer { create(createSavedStateHandle()) } } }
