package org.totschnig.myexpenses.next.data.categories

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The [CategoryDecisions] of the user, stored in the data store, which is part of the backup
 */
class CategoryStore(private val dataStore: DataStore<Preferences>) {

    val decisions: Flow<CategoryDecisions> = dataStore.data.map { it.toDecisions() }.distinctUntilChanged()

    /** Replaces the decisions with what [change] makes of them */
    suspend fun update(change: (CategoryDecisions) -> CategoryDecisions) {
        dataStore.edit { preferences ->
            val old = preferences.toDecisions()
            preferences.write(old, change(old))
        }
    }

    private companion object {
        /** JSON array of [CategoryRule]s */
        val KEY_RULES = stringPreferencesKey("next_category_rules")
        val KEY_FIRST_NEW_ID = longPreferencesKey("next_category_first_new_id")
        val KEY_BACKLOG_ANSWERED = booleanPreferencesKey("next_category_backlog_answered")
        /** JSON object mapping transaction ids to the category rejected */
        val KEY_REJECTED = stringPreferencesKey("next_category_rejected")
        /** JSON object mapping transaction ids to the category applied without asking */
        val KEY_AUTOMATIC = stringPreferencesKey("next_category_automatic")

        fun Preferences.toDecisions() = CategoryDecisions(
            rules = this[KEY_RULES]?.let(::parseRules) ?: emptyList(),
            firstNewId = this[KEY_FIRST_NEW_ID],
            backlogAnswered = this[KEY_BACKLOG_ANSWERED] ?: false,
            rejected = this[KEY_REJECTED]?.let(::parseIdMap) ?: emptyMap(),
            automatic = this[KEY_AUTOMATIC]?.let(::parseIdMap) ?: emptyMap()
        )

        /** Only what changed is written */
        fun MutablePreferences.write(old: CategoryDecisions, new: CategoryDecisions) {
            if (new.rules != old.rules) this[KEY_RULES] = serializeRules(new.rules)
            if (new.firstNewId != old.firstNewId) {
                new.firstNewId?.let { this[KEY_FIRST_NEW_ID] = it } ?: remove(KEY_FIRST_NEW_ID)
            }
            if (new.backlogAnswered != old.backlogAnswered) this[KEY_BACKLOG_ANSWERED] = new.backlogAnswered
            if (new.rejected != old.rejected) this[KEY_REJECTED] = serializeIdMap(new.rejected)
            if (new.automatic != old.automatic) this[KEY_AUTOMATIC] = serializeIdMap(new.automatic)
        }
    }
}
