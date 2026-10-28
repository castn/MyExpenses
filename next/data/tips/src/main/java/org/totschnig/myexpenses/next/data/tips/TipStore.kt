package org.totschnig.myexpenses.next.data.tips

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.next.data.tips.TipDecisions.Companion.adding

/**
 * Which tips the user dismissed or has seen, see [TipDecisions], stored in the data store, which
 * is part of the backup
 */
class TipStore(private val dataStore: DataStore<Preferences>) {

    val decisions: Flow<TipDecisions> = dataStore.data.map { preferences ->
        TipDecisions(
            dismissed = TipDecisions.parse(preferences[KEY_DISMISSED]),
            seen = TipDecisions.parse(preferences[KEY_SEEN])
        )
    }.distinctUntilChanged()

    /** Hides the tip until something new comes up, e.g. another price increase */
    suspend fun dismiss(tip: SavingTip) = change(KEY_DISMISSED) { it.adding(listOf(tip)) }

    suspend fun restore(tip: SavingTip) = change(KEY_DISMISSED) { it - tip.id }

    suspend fun markSeen(tips: List<SavingTip>) = change(KEY_SEEN) { it.adding(tips) }

    private suspend fun change(key: Preferences.Key<String>, change: (Map<String, Set<String>>) -> Map<String, Set<String>>) {
        dataStore.edit { it[key] = TipDecisions.serialize(change(TipDecisions.parse(it[key]))) }
    }

    private companion object {
        /** JSON object mapping ids of [SavingTip]s the user dismissed to their facts at that time */
        val KEY_DISMISSED = stringPreferencesKey("next_saving_tips_dismissed")

        /** JSON object mapping ids of [SavingTip]s the user has seen to their facts at that time */
        val KEY_SEEN = stringPreferencesKey("next_saving_tips_seen")
    }
}
