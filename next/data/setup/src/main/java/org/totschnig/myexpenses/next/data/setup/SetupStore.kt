package org.totschnig.myexpenses.next.data.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The [SetupProgress] of the user, stored in the data store, which is part of the backup
 */
class SetupStore(private val dataStore: DataStore<Preferences>) {

    val progress: Flow<SetupProgress> = dataStore.data.map { preferences ->
        SetupProgress(
            finished = stepsOf(preferences[KEY_FINISHED]),
            skipped = stepsOf(preferences[KEY_SKIPPED]),
            closed = preferences[KEY_CLOSED] ?: false,
            pace = Pace(preferences[KEY_PACE_DECISIONS] ?: 0, preferences[KEY_PACE_MILLIS] ?: 0)
        )
    }.distinctUntilChanged()

    /** The user went through [step] to the end */
    suspend fun finish(step: SetupStep) {
        dataStore.edit {
            it[KEY_FINISHED] = it[KEY_FINISHED].orEmpty() + step.name
            it[KEY_SKIPPED] = it[KEY_SKIPPED].orEmpty() - step.name
        }
    }

    /** The user skipped [step], which stays open */
    suspend fun skip(step: SetupStep) {
        dataStore.edit { it[KEY_SKIPPED] = it[KEY_SKIPPED].orEmpty() + step.name }
    }

    /** The user finished or skipped the whole setup */
    suspend fun close() {
        dataStore.edit { it[KEY_CLOSED] = true }
    }

    /** A decision took [millis], see [Pace.with] */
    suspend fun recordDecision(millis: Long) {
        dataStore.edit {
            val pace = Pace(it[KEY_PACE_DECISIONS] ?: 0, it[KEY_PACE_MILLIS] ?: 0).with(millis)
            it[KEY_PACE_DECISIONS] = pace.decisions
            it[KEY_PACE_MILLIS] = pace.millis
        }
    }

    /** Offers the setup again from the start; the measured pace is kept */
    suspend fun reset() {
        dataStore.edit {
            it.remove(KEY_FINISHED)
            it.remove(KEY_SKIPPED)
            it.remove(KEY_CLOSED)
        }
    }

    private companion object {
        /** Names of the [SetupStep]s */
        val KEY_FINISHED = stringSetPreferencesKey("next_setup_finished")
        val KEY_SKIPPED = stringSetPreferencesKey("next_setup_skipped")
        val KEY_CLOSED = booleanPreferencesKey("next_setup_closed")
        val KEY_PACE_DECISIONS = intPreferencesKey("next_setup_pace_decisions")
        val KEY_PACE_MILLIS = longPreferencesKey("next_setup_pace_millis")

        /** Names no longer known are left out */
        fun stepsOf(names: Set<String>?): Set<SetupStep> =
            names.orEmpty().mapNotNullTo(HashSet()) { name ->
                SetupStep.entries.find { it.name == name }
            }
    }
}
