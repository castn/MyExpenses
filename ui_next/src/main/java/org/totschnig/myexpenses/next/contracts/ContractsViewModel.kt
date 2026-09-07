package org.totschnig.myexpenses.next.contracts

import android.app.Application
import android.database.Cursor
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.totschnig.myexpenses.model.CurrencyUnit
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
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.util.epoch2LocalDate
import org.totschnig.myexpenses.util.toEpoch
import org.totschnig.myexpenses.viewmodel.ContentResolvingAndroidViewModel
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/**
 * Detects contracts in the debits of all accounts that are not excluded from totals.
 * Amounts are converted into the home currency, so that contracts of all accounts can be summed up.
 *
 * Transactions are only analysed after the user agreed. Decisions of the user (consent, dismissed
 * contracts, custom names) are stored in the data store, which is part of the backup.
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
                names = preferences[KEY_NAMES]?.let(::parseNames) ?: emptyMap()
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
            val names = preferences[KEY_NAMES]?.let(::parseNames) ?: emptyMap()
            val trimmed = name.trim()
            preferences[KEY_NAMES] = serializeNames(
                if (trimmed.isEmpty() || trimmed == contract.name) names - contract.signature
                else names + (contract.signature to trimmed)
            )
        }
    }

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

        private fun parseNames(json: String): Map<String, String> = try {
            JSONObject(json).let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
        } catch (_: JSONException) {
            emptyMap()
        }

        private fun serializeNames(names: Map<String, String>) = JSONObject(names).toString()

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
