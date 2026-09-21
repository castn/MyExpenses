package org.totschnig.myexpenses.next.contracts

import android.app.Application
import android.database.Cursor
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.totschnig.myexpenses.adapter.TransactionPagingSource
import org.totschnig.myexpenses.db2.tagMapFlow
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.balance.BalancePeriod
import org.totschnig.myexpenses.next.balance.BalanceTransaction
import org.totschnig.myexpenses.next.balance.BalanceUiState
import org.totschnig.myexpenses.next.balance.MonthlyBalance
import org.totschnig.myexpenses.next.balance.SalaryChoice
import org.totschnig.myexpenses.provider.DataBaseAccount.Companion.HOME_AGGREGATE_ID
import org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_SPLIT_PART
import org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_VOID
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_ACCOUNT_LABEL
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_AMOUNT_HOME_EQUIVALENT
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_ICON
import org.totschnig.myexpenses.provider.KEY_PATH
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_STATUS
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import org.totschnig.myexpenses.provider.KEY_TRANSFER_ACCOUNT
import org.totschnig.myexpenses.provider.KEY_TRANSFER_PEER
import org.totschnig.myexpenses.provider.STATUS_ARCHIVE
import org.totschnig.myexpenses.provider.STATUS_UNCOMMITTED
import org.totschnig.myexpenses.provider.TransactionProvider.TRANSACTIONS_URI
import org.totschnig.myexpenses.provider.filter.Criterion
import org.totschnig.myexpenses.provider.filter.TransactionIdCriterion
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.util.epoch2LocalDate
import org.totschnig.myexpenses.util.toEpoch
import org.totschnig.myexpenses.viewmodel.ContentResolvingAndroidViewModel
import org.totschnig.myexpenses.viewmodel.data.PageAccount
import org.totschnig.myexpenses.viewmodel.data.Transaction2

/**
 * Detects contracts in the debits of all accounts that are not excluded from totals.
 * Amounts are converted into the home currency, so that contracts of all accounts can be summed up.
 *
 * Transactions are only analysed after the user agreed. Decisions of the user (consent, dismissed
 * contracts, custom names, categories) are stored in the data store, which is part of the backup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContractsViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    val homeCurrency: CurrencyUnit
        get() = currencyContext.homeCurrencyUnit

    private val settings: Flow<ContractSettings> by lazy {
        dataStore.data.map { it.toContractSettings() }
    }

    /** Transactions to analyse, null while loading or without consent */
    private val transactions: Flow<List<ContractTransaction>?> by lazy {
        settings.map { it.consent == true }.distinctUntilChanged().flatMapLatest { hasConsent ->
            if (hasConsent) contentResolver.observeQuery(
                uri = TRANSACTIONS_URI,
                projection = PROJECTION,
                selection = SELECTION,
                selectionArgs = arrayOf(
                    LocalDate.now().minusMonths(HISTORY_MONTHS).toEpoch().toString()
                ),
                sortOrder = "$KEY_DATE ASC",
                notifyForDescendants = true
            )
                .mapToList { it.toContractTransaction() }
                .map<List<ContractTransaction>, List<ContractTransaction>?> { it }
                .onStart { emit(null) }
            else flowOf(null)
        }
    }

    /** The analysis actions work on, e.g. to create the rule of a suggestion */
    private val latestAnalysis = MutableStateFlow<ContractAnalysis?>(null)

    /**
     * Confirmed contracts from the rules of the user and suggestions for the other payments,
     * null while loading or without consent
     */
    private val analysis: Flow<ContractAnalysis?> by lazy {
        combine(transactions, settings.map { it.rules }.distinctUntilChanged()) { transactions, rules ->
            transactions?.let { ContractAnalysis.of(it, rules, LocalDate.now()) }
        }
            .onEach {
                latestAnalysis.value = it
                if (it != null) applyAutomaticDecisions(it)
            }
            .flowOn(Dispatchers.Default)
            // Contracts, incomes and the balance all need the analysis, it should run only once
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    }

    /**
     * Once: takes over the decisions stored by signature before there were rules.
     * Then: confirms suggestions that are certain enough, stores news and lifts the cancellation
     * of contracts that were paid again.
     * Changes of the rules lead to a new analysis.
     */
    private suspend fun applyAutomaticDecisions(analysis: ContractAnalysis) {
        dataStore.edit { preferences ->
            val settings = preferences.toContractSettings()
            var rules = settings.rules
            if (!settings.rulesMigrated) {
                // Only an analysis without rules shows the contracts the old decisions refer to
                if (rules.isEmpty()) {
                    val (migrated, salary) = migrateToRules(settings, analysis)
                    rules = migrated
                    preferences.writeSalary(salary)
                    preferences.remove(KEY_DISMISSED)
                    preferences.remove(KEY_NAMES)
                    preferences.remove(KEY_AREAS)
                }
                preferences[KEY_RULES_MIGRATED] = true
            }
            val today = LocalDate.now()
            val detection = analysis.detectNews(today)
            val confirmations = analysis.automaticConfirmations(rules)
            val updated = (rules + confirmations.map { it.second }).reactivating(detection.reactivated)
            if (updated != settings.rules) {
                preferences[KEY_RULES] = serializeRules(updated)
            }
            val stored = preferences[KEY_NEWS]?.let(::parseNews) ?: emptyList()
            // The first analysis confirms all contracts found so far, which are nothing new to the user
            val newContracts = if (settings.rulesMigrated) newContractNews(confirmations, today) else emptyList()
            val news = stored.settling(analysis).adding(detection.news + newContracts, today)
            if (news != stored) {
                preferences[KEY_NEWS] = serializeNews(news)
            }
        }
    }

    /** News about confirmed contracts, newest first, see [ContractNews] */
    val news: StateFlow<List<ContractNews>> by lazy {
        dataStore.data.map { preferences -> preferences[KEY_NEWS]?.let(::parseNews) ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun markNewsRead(ids: Set<String>) {
        edit { preferences ->
            preferences[KEY_NEWS]?.let(::parseNews)?.let { news ->
                preferences[KEY_NEWS] = serializeNews(news.map { if (it.id in ids) it.copy(isRead = true) else it })
            }
        }
    }

    private fun stateOf(direction: ContractDirection) =
        combine(settings, analysis) { settings, analysis ->
            when (settings.consent) {
                null -> ContractsUiState.AskConsent
                false -> ContractsUiState.Declined
                true -> analysis?.let {
                    buildContractsState(
                        it.contracts.filter { contract -> contract.direction == direction },
                        settings,
                        it.dismissed.filter { contract -> contract.direction == direction }
                    )
                } ?: ContractsUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContractsUiState.Loading)

    /** Contracts, i.e. recurring debits */
    val state: StateFlow<ContractsUiState> by lazy { stateOf(ContractDirection.EXPENSE) }

    /** Recurring credits, e.g. salary */
    val incomeState: StateFlow<ContractsUiState> by lazy { stateOf(ContractDirection.INCOME) }

    /** Ids of cash, bank and credit card accounts, set from the account list */
    private val dailyAccountIds = MutableStateFlow<Set<Long>?>(null)

    fun setDailyAccountIds(ids: Set<Long>) {
        dailyAccountIds.value = ids
    }

    /**
     * What came in and went out of the daily accounts since the last salary, including the
     * contract debits still to come until the next one
     */
    val balance: StateFlow<BalanceUiState> by lazy {
        combine(state, incomeState, dailyAccountIds) { contracts, incomes, daily -> Triple(contracts, incomes, daily) }
            .flatMapLatest { (contracts, incomes, daily) ->
                when {
                    contracts == ContractsUiState.AskConsent -> flowOf(BalanceUiState.AskConsent)
                    contracts == ContractsUiState.Declined -> flowOf(BalanceUiState.Declined)
                    contracts !is ContractsUiState.Ready || incomes !is ContractsUiState.Ready || daily == null ->
                        flowOf(BalanceUiState.Loading)

                    else -> {
                        val today = LocalDate.now()
                        val salaries = incomes.salaries
                        val period = BalancePeriod.of(salaries.firstOrNull(), today)
                        periodTransactions(period).map {
                            BalanceUiState.Ready(
                                MonthlyBalance.compute(
                                    period, it, daily,
                                    // Payments of cancelled contracts, e.g. the last one, are still contract payments
                                    contracts.active + contracts.ended + contracts.cancelled,
                                    salaries
                                ),
                                today
                            )
                        }
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BalanceUiState.Loading)
    }

    private fun periodTransactions(period: BalancePeriod): Flow<List<BalanceTransaction>> =
        contentResolver.observeQuery(
            uri = TRANSACTIONS_URI,
            projection = BALANCE_PROJECTION,
            selection = BALANCE_SELECTION,
            selectionArgs = arrayOf(period.start.startOfDayEpoch().toString(), period.end.startOfDayEpoch().toString()),
            notifyForDescendants = true
        ).mapToList {
            BalanceTransaction(
                id = it.getLong(KEY_ROWID),
                date = epoch2LocalDate(it.getLong(KEY_DATE)),
                amount = it.getLong(KEY_AMOUNT_HOME_EQUIVALENT),
                accountId = it.getLong(KEY_ACCOUNTID),
                transferAccountId = it.getLongOrNull(KEY_TRANSFER_ACCOUNT)
            )
        }

    private fun LocalDate.startOfDayEpoch() = atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

    /** Fixed filter of [contractTransactions], never persisted, so that it cannot affect the account screens */
    private val contractFilter = MutableStateFlow<Criterion?>(null)

    private val tags: StateFlow<Map<String, Pair<String, Int?>>> by lazy {
        contentResolver.tagMapFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    }

    /**
     * The debits a contract was detected from, as in the transaction list of an account.
     * They are loaded from the aggregate of all accounts, since a contract can span several accounts.
     * Which contract is set with [showTransactionsOf].
     */
    val contractTransactions: Flow<PagingData<Transaction2>> by lazy {
        val allAccounts = PageAccount(
            id = HOME_AGGREGATE_ID,
            currencyUnit = homeCurrency,
            label = "",
            accountGrouping = AccountGrouping.NONE
        )
        Pager(PagingConfig(pageSize = 50)) {
            TransactionPagingSource(
                getApplication(),
                allAccounts,
                contractFilter,
                tags,
                currencyContext,
                viewModelScope,
                prefHandler
            )
        }.flow.cachedIn(viewModelScope)
    }

    fun showTransactionsOf(contract: Contract) {
        showTransactions(contract.displayName, contract.transactions.map { it.id })
    }

    /** Shows the given transactions in [contractTransactions] */
    fun showTransactions(label: String, ids: List<Long>) {
        contractFilter.value = TransactionIdCriterion(label, ids)
    }

    /**
     * Incomes chosen as salary are confirmed, so that the choice refers to their rules
     */
    fun setSalary(choice: SalaryChoice) {
        updateRules { rules, analysis, preferences ->
            var updated = rules
            val salary = if (choice is SalaryChoice.Fixed) SalaryChoice.Fixed(
                choice.signatures.mapTo(HashSet()) { signature ->
                    val suggestion = analysis.suggestions.find { it.signature == signature }
                    if (suggestion == null) signature
                    else updated.withRuleFor(suggestion, analysis.transactions).let { (all, rule) ->
                        updated = all
                        rule.key
                    }
                }
            ) else choice
            preferences.writeSalary(salary)
            updated
        }
    }

    fun setConsent(consent: Boolean) {
        edit { it[KEY_CONSENT] = consent }
    }

    /** Confirms a suggestion */
    fun confirm(contract: Contract) {
        changeRule(contract) { it }
    }

    /** Declares the payments of [contract] as no contract */
    fun dismiss(contract: Contract) {
        updateRules { rules, analysis, _ -> rules.dismissing(contract, analysis.transactions) }
    }

    fun restore(contract: Contract) {
        updateRules { rules, _, preferences ->
            // Dismissed before there were rules
            preferences[KEY_DISMISSED]?.let { preferences[KEY_DISMISSED] = it - contract.signature }
            rules.restoring(contract)
        }
    }

    /**
     * Confirms a suggestion
     *
     * @param name blank to go back to the detected name
     */
    fun rename(contract: Contract, name: String) {
        val trimmed = name.trim()
        changeRule(contract) { it.copy(name = trimmed.takeIf { name -> name.isNotEmpty() && name != contract.name }) }
    }

    /** Confirms a suggestion */
    fun setArea(contract: Contract, choice: AreaChoice) {
        changeRule(contract) {
            it.copy(
                areaKey = when (choice) {
                    AreaChoice.Automatic -> null
                    is AreaChoice.Fixed -> choice.area?.key ?: ContractSettings.AREA_NONE
                }
            )
        }
    }

    /**
     * The user cancelled the contract (or an income was discontinued): it is not expected anymore,
     * but stays visible
     */
    fun cancel(contract: Contract) {
        changeRule(contract) {
            it.copy(
                cancelledOn = LocalDate.now(),
                snapshot = ContractRule.Snapshot(contract.name, contract.lastAmount, contract.lastDate)
            )
        }
    }

    fun revokeCancellation(contract: Contract) {
        changeRule(contract) { it.copy(cancelledOn = null, snapshot = null) }
    }

    fun setInterval(contract: Contract, interval: ContractInterval) {
        changeRule(contract) { it.copy(interval = interval) }
    }

    /** Takes back that the payments of [payeeId] belong to [contract], e.g. after a wrong merge */
    fun removePayee(contract: Contract, payeeId: Long) {
        changeRule(contract) { it.copy(payeeIds = it.payeeIds - payeeId) }
    }

    /**
     * @param range absolute amounts in minor units, null for all amounts
     */
    fun setAmountRange(contract: Contract, range: LongRange?) {
        changeRule(contract) { it.copy(amountRange = range) }
    }

    /** Joins [other] into [contract], see [merging] */
    fun merge(contract: Contract, other: Contract) {
        updateRules { rules, analysis, preferences ->
            val (merged, rule) = rules.merging(contract, other, analysis.transactions)
            preferences[KEY_SALARIES]?.takeIf { other.signature in it }?.let {
                preferences[KEY_SALARIES] = it - other.signature + rule.key
            }
            merged
        }
    }

    /** What the analysis knows about a payment, see [PaymentContractInfo] */
    fun paymentInfo(transactionId: Long): Flow<PaymentContractInfo> =
        analysis.map { it?.infoFor(transactionId) ?: PaymentContractInfo.Unavailable }

    /** Declares a payment as part of a contract paid every [interval] */
    fun markAsContract(transactionId: Long, interval: ContractInterval) {
        updateRules { rules, analysis, _ -> rules.marking(transactionId, interval, analysis.transactions, LocalDate.now()) }
    }

    /** Changes the rule of [contract], which confirms a suggestion */
    private fun changeRule(contract: Contract, change: (ContractRule) -> ContractRule) {
        updateRules { rules, analysis, _ -> rules.withRuleFor(contract, analysis.transactions, change).first }
    }

    /**
     * Changes the rules based on the latest analysis. Does nothing before there is one,
     * since actions can only be triggered on contracts shown from it.
     */
    private fun updateRules(
        change: (List<ContractRule>, ContractAnalysis, MutablePreferences) -> List<ContractRule>,
    ) {
        val analysis = latestAnalysis.value ?: return
        edit { preferences ->
            val rules = preferences[KEY_RULES]?.let(::parseRules) ?: emptyList()
            preferences[KEY_RULES] = serializeRules(change(rules, analysis, preferences))
        }
    }

    private fun MutablePreferences.writeSalary(choice: SalaryChoice) {
        remove(KEY_SALARY)
        remove(KEY_SALARIES)
        when (choice) {
            SalaryChoice.Automatic -> {}
            SalaryChoice.None -> this[KEY_SALARY] = SALARY_NONE
            is SalaryChoice.Fixed -> this[KEY_SALARIES] = choice.signatures
        }
    }

    /**
     * @return the new category, so that a contract can be put into it right away
     */
    fun createArea(name: String): CustomArea {
        val area = CustomArea(UUID.randomUUID().toString(), name.trim())
        edit { preferences ->
            preferences[KEY_CUSTOM_AREAS] = serializeCustomAreas(customAreas(preferences) + area)
        }
        return area
    }

    fun renameArea(area: CustomArea, name: String) {
        edit { preferences ->
            preferences[KEY_CUSTOM_AREAS] = serializeCustomAreas(
                customAreas(preferences).map { if (it.id == area.id) it.copy(name = name.trim()) else it }
            )
        }
    }

    /**
     * Contracts in the deleted category go back to their suggested category
     */
    fun deleteArea(area: CustomArea) {
        edit { preferences ->
            preferences[KEY_CUSTOM_AREAS] = serializeCustomAreas(customAreas(preferences).filter { it.id != area.id })
            preferences[KEY_AREAS]?.let(::parseMap)?.let { areas ->
                preferences[KEY_AREAS] = serializeMap(areas.filterValues { it != area.key })
            }
            preferences[KEY_RULES]?.let(::parseRules)?.let { rules ->
                preferences[KEY_RULES] = serializeRules(
                    rules.map { if (it.areaKey == area.key) it.copy(areaKey = null) else it }
                )
            }
        }
    }

    private fun customAreas(preferences: MutablePreferences) =
        preferences[KEY_CUSTOM_AREAS]?.let(::parseCustomAreas) ?: emptyList()

    private fun edit(block: (MutablePreferences) -> Unit) {
        viewModelScope.launch { dataStore.edit(block) }
    }

    private fun Cursor.toContractTransaction() = ContractTransaction(
        id = getLong(KEY_ROWID),
        date = epoch2LocalDate(getLong(KEY_DATE)),
        amount = getLong(KEY_AMOUNT_HOME_EQUIVALENT),
        accountId = getLong(KEY_ACCOUNTID),
        accountLabel = getStringOrNull(KEY_ACCOUNT_LABEL),
        payeeId = getLongOrNull(KEY_PAYEEID),
        payeeName = getStringOrNull(KEY_PAYEE_NAME),
        comment = getStringOrNull(KEY_COMMENT),
        categoryPath = getStringOrNull(KEY_PATH),
        categoryIcon = getStringOrNull(KEY_ICON),
        templateId = getLongOrNull(KEY_TEMPLATEID)
    )

    companion object {
        private val BALANCE_PROJECTION = arrayOf(
            KEY_ROWID, KEY_DATE, KEY_AMOUNT_HOME_EQUIVALENT, KEY_ACCOUNTID, KEY_TRANSFER_ACCOUNT
        )

        /** All transactions of the period, transfers included, split parts not, since their parents count */
        private val BALANCE_SELECTION = "$KEY_DATE >= ? AND $KEY_DATE < ? AND $WHERE_NOT_SPLIT_PART" +
                " AND $KEY_STATUS NOT IN ($STATUS_UNCOMMITTED, $STATUS_ARCHIVE) AND $WHERE_NOT_VOID"

        private val KEY_CONSENT = booleanPreferencesKey("next_contracts_consent")
        private val KEY_DISMISSED = stringSetPreferencesKey("next_contracts_dismissed")
        /** JSON object mapping signatures to custom names */
        private val KEY_NAMES = stringPreferencesKey("next_contracts_names")
        /** JSON object mapping signatures to the key of a [ContractArea] or [ContractSettings.AREA_NONE] */
        private val KEY_AREAS = stringPreferencesKey("next_contracts_areas")
        /** JSON array of the categories created by the user */
        private val KEY_CUSTOM_AREAS = stringPreferencesKey("next_contracts_custom_areas")
        /** [SALARY_NONE] for the calendar month */
        private val KEY_SALARY = stringPreferencesKey("next_contracts_salary")
        /** Signatures of the incomes chosen as salaries, missing for the automatic choice */
        private val KEY_SALARIES = stringSetPreferencesKey("next_contracts_salaries")
        /** JSON array of [ContractRule]s */
        private val KEY_RULES = stringPreferencesKey("next_contracts_rules")
        private val KEY_RULES_MIGRATED = booleanPreferencesKey("next_contracts_rules_migrated")
        /** JSON array of [ContractNews] */
        private val KEY_NEWS = stringPreferencesKey("next_contracts_news")

        /** News that cannot be read, e.g. of a type from a later version, are left out */
        private fun parseNews(json: String): List<ContractNews> = try {
            JSONArray(json).let { array ->
                (0 until array.length()).mapNotNull { i ->
                    try {
                        array.getJSONObject(i).toNews()
                    } catch (_: JSONException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    } catch (_: DateTimeException) {
                        null
                    }
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }

        private fun JSONObject.toNews() = ContractNews(
            id = getString("id"),
            type = ContractNews.Type.valueOf(getString("type")),
            ruleId = getString("rule"),
            transactionId = getLong("transaction"),
            date = LocalDate.parse(getString("date")),
            createdAt = LocalDate.parse(getString("created")),
            contractName = getString("name"),
            isIncome = getBoolean("income"),
            amount = getLong("amount"),
            previousAmount = if (has("previous")) getLong("previous") else null,
            cancelledOn = if (has("cancelled")) LocalDate.parse(getString("cancelled")) else null,
            interval = if (has("interval")) ContractInterval.valueOf(getString("interval")) else null,
            isRead = optBoolean("read")
        )

        private fun serializeNews(news: List<ContractNews>) = JSONArray(
            news.map { item ->
                JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.name)
                    put("rule", item.ruleId)
                    put("transaction", item.transactionId)
                    put("date", item.date.toString())
                    put("created", item.createdAt.toString())
                    put("name", item.contractName)
                    put("income", item.isIncome)
                    put("amount", item.amount)
                    item.previousAmount?.let { put("previous", it) }
                    item.cancelledOn?.let { put("cancelled", it.toString()) }
                    item.interval?.let { put("interval", it.name) }
                    put("read", item.isRead)
                }
            }
        ).toString()

        private fun Preferences.toContractSettings() = ContractSettings(
            consent = this[KEY_CONSENT],
            dismissed = this[KEY_DISMISSED] ?: emptySet(),
            names = this[KEY_NAMES]?.let(::parseMap) ?: emptyMap(),
            areas = this[KEY_AREAS]?.let(::parseMap) ?: emptyMap(),
            customAreas = this[KEY_CUSTOM_AREAS]?.let(::parseCustomAreas) ?: emptyList(),
            salary = when {
                this[KEY_SALARY] == SALARY_NONE -> SalaryChoice.None
                this[KEY_SALARIES].isNullOrEmpty() -> SalaryChoice.Automatic
                else -> SalaryChoice.Fixed(this[KEY_SALARIES]!!)
            },
            rules = this[KEY_RULES]?.let(::parseRules) ?: emptyList(),
            rulesMigrated = this[KEY_RULES_MIGRATED] ?: false
        )

        /** Rules that cannot be read, e.g. from a later version, are left out */
        private fun parseRules(json: String): List<ContractRule> = try {
            JSONArray(json).let { array ->
                (0 until array.length()).mapNotNull { i ->
                    try {
                        array.getJSONObject(i).toRule()
                    } catch (_: JSONException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    } catch (_: DateTimeException) {
                        null
                    }
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }

        private fun JSONObject.toRule() = ContractRule(
            id = getString("id"),
            kind = ContractRule.Kind.valueOf(getString("kind")),
            direction = ContractDirection.valueOf(getString("direction")),
            payeeIds = optJSONArray("payees")?.let { payees -> (0 until payees.length()).mapTo(HashSet()) { payees.getLong(it) } }
                ?: emptySet(),
            templateId = if (has("template")) getLong("template") else null,
            amountRange = if (has("min") && has("max")) getLong("min")..getLong("max") else null,
            interval = ContractInterval.valueOf(getString("interval")),
            name = if (has("name")) getString("name") else null,
            areaKey = if (has("area")) getString("area") else null,
            cancelledOn = if (has("cancelled")) LocalDate.parse(getString("cancelled")) else null,
            snapshot = if (has("snapshotName")) ContractRule.Snapshot(
                getString("snapshotName"),
                getLong("snapshotAmount"),
                LocalDate.parse(getString("snapshotDate"))
            ) else null
        )

        private fun serializeRules(rules: List<ContractRule>) = JSONArray(
            rules.map { rule ->
                JSONObject().apply {
                    put("id", rule.id)
                    put("kind", rule.kind.name)
                    put("direction", rule.direction.name)
                    put("payees", JSONArray(rule.payeeIds.toList()))
                    rule.templateId?.let { put("template", it) }
                    rule.amountRange?.let {
                        put("min", it.first)
                        put("max", it.last)
                    }
                    put("interval", rule.interval.name)
                    rule.name?.let { put("name", it) }
                    rule.areaKey?.let { put("area", it) }
                    rule.cancelledOn?.let { put("cancelled", it.toString()) }
                    rule.snapshot?.let {
                        put("snapshotName", it.name)
                        put("snapshotAmount", it.lastAmount)
                        put("snapshotDate", it.lastDate.toString())
                    }
                }
            }
        ).toString()
        private const val SALARY_NONE = "NONE"

        private fun parseMap(json: String): Map<String, String> = try {
            JSONObject(json).let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
        } catch (_: JSONException) {
            emptyMap()
        }

        private fun serializeMap(map: Map<String, String>) = JSONObject(map).toString()

        private fun parseCustomAreas(json: String): List<CustomArea> = try {
            JSONArray(json).let { array ->
                (0 until array.length()).map { i ->
                    array.getJSONObject(i).let { CustomArea(it.getString("id"), it.getString("name")) }
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }

        private fun serializeCustomAreas(areas: List<CustomArea>) = JSONArray(
            areas.map { JSONObject(mapOf("id" to it.id, "name" to it.name)) }
        ).toString()

        /** A bit more than two years, so that yearly contracts have been debited at least twice */
        const val HISTORY_MONTHS = 26L

        private val PROJECTION = arrayOf(
            KEY_ROWID,
            KEY_DATE,
            KEY_AMOUNT_HOME_EQUIVALENT,
            KEY_ACCOUNTID,
            KEY_ACCOUNT_LABEL,
            KEY_PAYEEID,
            KEY_PAYEE_NAME,
            KEY_COMMENT,
            KEY_PATH,
            KEY_ICON,
            KEY_TEMPLATEID
        )

        /**
         * Debits and credits that are no transfers. Split transactions are taken as a whole, because the payee
         * is stored with the parent. Archived transactions are left out (they are split parts of the archive).
         */
        private val SELECTION = "$KEY_AMOUNT != 0 AND $KEY_TRANSFER_PEER IS NULL AND $WHERE_NOT_SPLIT_PART" +
                " AND $KEY_STATUS NOT IN ($STATUS_UNCOMMITTED, $STATUS_ARCHIVE) AND $WHERE_NOT_VOID" +
                " AND $KEY_DATE >= ?"
    }
}
