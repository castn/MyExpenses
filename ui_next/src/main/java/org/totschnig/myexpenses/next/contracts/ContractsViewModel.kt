package org.totschnig.myexpenses.next.contracts

import android.app.Application
import android.database.Cursor
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import java.time.LocalDate

/**
 * Detects contracts in the debits of all accounts that are not excluded from totals.
 * Amounts are converted into the home currency, so that contracts of all accounts can be summed up.
 *
 * Transactions are only analysed after the user agreed. The decision is stored in the data store,
 * which is part of the backup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContractsViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    val homeCurrency: CurrencyUnit
        get() = currencyContext.homeCurrencyUnit

    val state: StateFlow<ContractsUiState> by lazy {
        dataStore.data.map { it[KEY_CONSENT] }.distinctUntilChanged().flatMapLatest { consent ->
            when (consent) {
                null -> flowOf(ContractsUiState.AskConsent)
                false -> flowOf(ContractsUiState.Declined)
                true -> contentResolver.observeQuery(
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
                    .map<List<ContractTransaction>, ContractsUiState> {
                        ContractsUiState.Ready(ContractDetector().detect(it))
                    }
                    .onStart { emit(ContractsUiState.Loading) }
                    .flowOn(Dispatchers.Default)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContractsUiState.Loading)
    }

    fun setConsent(consent: Boolean) {
        viewModelScope.launch { dataStore.edit { it[KEY_CONSENT] = consent } }
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
