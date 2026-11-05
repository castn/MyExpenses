package org.totschnig.myexpenses.next.data.receipts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the user decided about the model for item groups and where it came from, in the data store.
 * The model itself is a file of [ItemGroupModelStore].
 */
class ItemGroupModelSetupStore(private val dataStore: DataStore<Preferences>) {

    enum class Source(internal val value: String) {
        /** Kept up to date, see [claimUpdateCheck] */
        DOWNLOAD("download"),

        /** Picked by the user, not updated */
        FILE("file")
    }

    /** The user said no to setting up a model, it is not offered again */
    val declined: Flow<Boolean> = dataStore.data.map { it[KEY_DECLINED] == true }.distinctUntilChanged()

    /** Where the installed model came from, null if none was installed */
    val source: Flow<Source?> = dataStore.data
        .map { preferences -> Source.entries.firstOrNull { it.value == preferences[KEY_SOURCE] } }
        .distinctUntilChanged()

    suspend fun decline() {
        dataStore.edit { it[KEY_DECLINED] = true }
    }

    /** A model from [source] was installed at [now] */
    suspend fun installed(source: Source, now: Long) {
        dataStore.edit {
            it[KEY_SOURCE] = source.value
            it[KEY_CHECKED] = now
        }
    }

    /**
     * Whether to look for a newer release of a downloaded model at [now]: at most once in
     * [UPDATE_INTERVAL], which starts again with the answer true
     */
    suspend fun claimUpdateCheck(now: Long): Boolean {
        var due = false
        dataStore.edit {
            due = it[KEY_SOURCE] == Source.DOWNLOAD.value && now - (it[KEY_CHECKED] ?: 0) >= UPDATE_INTERVAL
            if (due) it[KEY_CHECKED] = now
        }
        return due
    }

    companion object {
        const val UPDATE_INTERVAL = 7L * 24 * 60 * 60 * 1000

        private val KEY_DECLINED = booleanPreferencesKey("next_item_group_model_declined")

        /** [Source.value] */
        private val KEY_SOURCE = stringPreferencesKey("next_item_group_model_source")
        private val KEY_CHECKED = longPreferencesKey("next_item_group_model_checked")
    }
}
