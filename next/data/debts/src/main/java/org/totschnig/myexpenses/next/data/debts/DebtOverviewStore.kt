package org.totschnig.myexpenses.next.data.debts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How the user wants the overview of debts, see [debtOverview] */
data class DebtOverviewSettings(val sort: DebtSort = DebtSort.Payee, val showAll: Boolean = false)

/** The settings of the overview of debts, stored in the data store, which is part of the backup */
class DebtOverviewStore(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<DebtOverviewSettings> = dataStore.data.map { preferences ->
        DebtOverviewSettings(
            sort = preferences[SORT]?.let { name -> DebtSort.entries.find { it.name == name } }
                ?: DebtSort.Payee,
            showAll = preferences[SHOW_ALL] ?: false
        )
    }

    suspend fun setSort(sort: DebtSort) {
        dataStore.edit { it[SORT] = sort.name }
    }

    suspend fun setShowAll(showAll: Boolean) {
        dataStore.edit { it[SHOW_ALL] = showAll }
    }

    private companion object {
        val SORT = stringPreferencesKey("next_debts_sort")
        val SHOW_ALL = booleanPreferencesKey("next_debts_show_all")
    }
}
