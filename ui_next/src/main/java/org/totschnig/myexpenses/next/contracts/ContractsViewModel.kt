package org.totschnig.myexpenses.next.contracts

import android.app.Application
import android.database.Cursor
import androidx.datastore.preferences.core.MutablePreferences
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
import java.time.LocalDate
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.totschnig.myexpenses.adapter.TransactionPagingSource
import org.totschnig.myexpenses.db2.tagMapFlow
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.model.CurrencyUnit
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
        dataStore.data.map { preferences ->
            ContractSettings(
                consent = preferences[KEY_CONSENT],
                dismissed = preferences[KEY_DISMISSED] ?: emptySet(),
                names = preferences[KEY_NAMES]?.let(::parseMap) ?: emptyMap(),
                areas = preferences[KEY_AREAS]?.let(::parseMap) ?: emptyMap(),
                customAreas = preferences[KEY_CUSTOM_AREAS]?.let(::parseCustomAreas) ?: emptyList()
            )
        }
    }

    /** Detected contracts, null while loading or without consent */
    private val detected: Flow<List<Contract>?> by lazy {
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
                .map<List<ContractTransaction>, List<Contract>?> { ContractDetector().detect(it) }
                .onStart { emit(null) }
                .flowOn(Dispatchers.Default)
            else flowOf(null)
        }
    }

    val state: StateFlow<ContractsUiState> by lazy {
        combine(settings, detected) { settings, contracts ->
            when (settings.consent) {
                null -> ContractsUiState.AskConsent
                false -> ContractsUiState.Declined
                true -> contracts?.let { buildContractsState(it, settings) } ?: ContractsUiState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContractsUiState.Loading)
    }

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
        contractFilter.value = TransactionIdCriterion(contract.displayName, contract.transactions.map { it.id })
    }

    fun setConsent(consent: Boolean) {
        edit { it[KEY_CONSENT] = consent }
    }

    fun dismiss(contract: Contract) {
        edit { it[KEY_DISMISSED] = (it[KEY_DISMISSED] ?: emptySet()) + contract.signature }
    }

    fun restore(contract: Contract) {
        edit { it[KEY_DISMISSED] = (it[KEY_DISMISSED] ?: emptySet()) - contract.signature }
    }

    /**
     * @param name blank to go back to the detected name
     */
    fun rename(contract: Contract, name: String) {
        edit { preferences ->
            val names = preferences[KEY_NAMES]?.let(::parseMap) ?: emptyMap()
            val trimmed = name.trim()
            preferences[KEY_NAMES] = serializeMap(
                if (trimmed.isEmpty() || trimmed == contract.name) names - contract.signature
                else names + (contract.signature to trimmed)
            )
        }
    }

    fun setArea(contract: Contract, choice: AreaChoice) {
        edit { preferences ->
            val areas = preferences[KEY_AREAS]?.let(::parseMap) ?: emptyMap()
            preferences[KEY_AREAS] = serializeMap(
                when (choice) {
                    AreaChoice.Automatic -> areas - contract.signature
                    is AreaChoice.Fixed -> areas + (contract.signature to (choice.area?.key ?: ContractSettings.AREA_NONE))
                }
            )
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
        private val KEY_CONSENT = booleanPreferencesKey("next_contracts_consent")
        private val KEY_DISMISSED = stringSetPreferencesKey("next_contracts_dismissed")
        /** JSON object mapping signatures to custom names */
        private val KEY_NAMES = stringPreferencesKey("next_contracts_names")
        /** JSON object mapping signatures to the key of a [ContractArea] or [ContractSettings.AREA_NONE] */
        private val KEY_AREAS = stringPreferencesKey("next_contracts_areas")
        /** JSON array of the categories created by the user */
        private val KEY_CUSTOM_AREAS = stringPreferencesKey("next_contracts_custom_areas")

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
         * Debits that are no transfers. Split transactions are taken as a whole, because the payee
         * is stored with the parent. Archived transactions are left out (they are split parts of the archive).
         */
        private val SELECTION = "$KEY_AMOUNT < 0 AND $KEY_TRANSFER_PEER IS NULL AND $WHERE_NOT_SPLIT_PART" +
                " AND $KEY_STATUS NOT IN ($STATUS_UNCOMMITTED, $STATUS_ARCHIVE) AND $WHERE_NOT_VOID" +
                " AND $KEY_DATE >= ?"
    }
}
