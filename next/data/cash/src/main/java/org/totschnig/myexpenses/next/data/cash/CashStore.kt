package org.totschnig.myexpenses.next.data.cash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The [CashAccount] of the user, stored in the data store, which is part of the backup */
class CashStore(private val dataStore: DataStore<Preferences>) {

    /** Null if there is none */
    val account: Flow<CashAccount?> = dataStore.data.map { preferences ->
        val id = preferences[KEY_ACCOUNT_ID]
        val since = preferences[KEY_SINCE]
        if (id != null && since != null) CashAccount(id, LocalDate.ofEpochDay(since)) else null
    }.distinctUntilChanged()

    suspend fun set(account: CashAccount?) {
        dataStore.edit {
            if (account != null) {
                it[KEY_ACCOUNT_ID] = account.id
                it[KEY_SINCE] = account.since.toEpochDay()
            } else {
                it.remove(KEY_ACCOUNT_ID)
                it.remove(KEY_SINCE)
            }
        }
    }

    private companion object {
        val KEY_ACCOUNT_ID = longPreferencesKey("next_cash_account_id")

        /** Epoch day */
        val KEY_SINCE = longPreferencesKey("next_cash_since")
    }
}
