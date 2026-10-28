package org.totschnig.myexpenses.next.data.guidance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which hints the user closed and how often a hint was shown after saving, stored in the data
 * store, which is part of the backup
 */
class HintStore(private val dataStore: DataStore<Preferences>) {

    /** Topics the user closed */
    val dismissed: Flow<Set<HintTopic>> = dataStore.data.map { preferences ->
        topicsOf(preferences[KEY_DISMISSED])
    }.distinctUntilChanged()

    /** Topics not to tell about after saving: closed, or shown [HintTopic.MAX_AFTER_SAVE] times */
    val hiddenAfterSave: Flow<Set<HintTopic>> = dataStore.data.map { preferences ->
        topicsOf(preferences[KEY_DISMISSED]) + HintTopic.entries.filter { topic ->
            (preferences[savedKey(topic)] ?: 0) >= HintTopic.MAX_AFTER_SAVE
        }
    }.distinctUntilChanged()

    suspend fun dismiss(topic: HintTopic) {
        dataStore.edit { it[KEY_DISMISSED] = it[KEY_DISMISSED].orEmpty() + topic.name }
    }

    /** Counts a hint shown after saving */
    suspend fun shownAfterSave(topic: HintTopic) {
        dataStore.edit { it[savedKey(topic)] = (it[savedKey(topic)] ?: 0) + 1 }
    }

    /** Shows all hints again */
    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_DISMISSED)
            HintTopic.entries.forEach { preferences.remove(savedKey(it)) }
        }
    }

    private companion object {
        /** Names of the [HintTopic]s the user closed */
        val KEY_DISMISSED = stringSetPreferencesKey("next_hints_dismissed")

        /** How often a hint of the topic was shown after saving */
        fun savedKey(topic: HintTopic) = intPreferencesKey("next_hints_saved_${topic.name}")

        /** Names no longer known are left out */
        fun topicsOf(names: Set<String>?): Set<HintTopic> =
            names.orEmpty().mapNotNullTo(HashSet()) { name -> HintTopic.entries.find { it.name == name } }
    }
}
