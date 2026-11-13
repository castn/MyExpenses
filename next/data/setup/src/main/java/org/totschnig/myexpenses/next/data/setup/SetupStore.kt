package org.totschnig.myexpenses.next.data.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDate
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
            pace = Pace(preferences[KEY_PACE_DECISIONS] ?: 0, preferences[KEY_PACE_MILLIS] ?: 0),
            reminder = SetupReminder(
                opened = preferences[KEY_OPENED] ?: false,
                dismissals = preferences[KEY_DISMISSALS] ?: 0,
                hiddenUntil = preferences[KEY_HIDDEN_UNTIL]?.let(LocalDate::ofEpochDay),
                decisionsWhenDismissed = preferences[KEY_DISMISSED_DECISIONS] ?: 0
            )
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

    /** The setup was opened, from now on its card on the overview is small */
    suspend fun markOpened() {
        dataStore.edit { it[KEY_OPENED] = true }
    }

    /** The card on the overview was put away on [today] with [decisions] open, see [SetupReminder.dismissed] */
    suspend fun dismiss(today: LocalDate, decisions: Int) {
        dataStore.edit {
            val reminder = SetupReminder(
                dismissals = it[KEY_DISMISSALS] ?: 0
            ).dismissed(today, decisions)
            it[KEY_DISMISSALS] = reminder.dismissals
            reminder.hiddenUntil?.let { until -> it[KEY_HIDDEN_UNTIL] = until.toEpochDay() }
                ?: it.remove(KEY_HIDDEN_UNTIL)
            it[KEY_DISMISSED_DECISIONS] = decisions
        }
    }

    /** A decision took [millis], see [Pace.with] */
    suspend fun recordDecision(millis: Long) {
        dataStore.edit {
            val pace = Pace(it[KEY_PACE_DECISIONS] ?: 0, it[KEY_PACE_MILLIS] ?: 0).with(millis)
            it[KEY_PACE_DECISIONS] = pace.decisions
            it[KEY_PACE_MILLIS] = pace.millis
        }
    }

    /**
     * Offers the setup again where the user left it, e.g. from the settings: no longer closed, and
     * its card is shown right away, counting the times it is put away from the start
     */
    suspend fun resume() {
        dataStore.edit {
            it.remove(KEY_CLOSED)
            it.remove(KEY_DISMISSALS)
            it.remove(KEY_HIDDEN_UNTIL)
            it.remove(KEY_DISMISSED_DECISIONS)
        }
    }

    /** Offers the setup again from the start, its card included; the measured pace is kept */
    suspend fun reset() {
        dataStore.edit {
            it.remove(KEY_FINISHED)
            it.remove(KEY_SKIPPED)
            it.remove(KEY_CLOSED)
            it.remove(KEY_OPENED)
            it.remove(KEY_DISMISSALS)
            it.remove(KEY_HIDDEN_UNTIL)
            it.remove(KEY_DISMISSED_DECISIONS)
        }
    }

    private companion object {
        /** Names of the [SetupStep]s */
        val KEY_FINISHED = stringSetPreferencesKey("next_setup_finished")
        val KEY_SKIPPED = stringSetPreferencesKey("next_setup_skipped")
        val KEY_CLOSED = booleanPreferencesKey("next_setup_closed")
        val KEY_PACE_DECISIONS = intPreferencesKey("next_setup_pace_decisions")
        val KEY_PACE_MILLIS = longPreferencesKey("next_setup_pace_millis")
        val KEY_OPENED = booleanPreferencesKey("next_setup_opened")
        val KEY_DISMISSALS = intPreferencesKey("next_setup_dismissals")

        /** Epoch day */
        val KEY_HIDDEN_UNTIL = longPreferencesKey("next_setup_hidden_until")
        val KEY_DISMISSED_DECISIONS = intPreferencesKey("next_setup_dismissed_decisions")

        /** Names no longer known are left out */
        fun stepsOf(names: Set<String>?): Set<SetupStep> =
            names.orEmpty().mapNotNullTo(HashSet()) { name ->
                SetupStep.entries.find { it.name == name }
            }
    }
}
