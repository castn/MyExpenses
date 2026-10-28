package org.totschnig.myexpenses.next.data.tags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The decisions of the user about tags, stored in the data store, which is part of the backup:
 * the meanings of tags, the rules learned per counterpart and the suggestions the user said no to
 */
class TagStore(private val dataStore: DataStore<Preferences>) {

    /** What the user said no to, so that it is not suggested again */
    enum class Rejected(key: String) {
        /** [Refund.key]s */
        REFUNDS("next_refunds_rejected"),

        /** [Trip.key]s */
        TRIPS("next_trips_rejected"),

        /** [TagSuggestion.key]s */
        SUGGESTIONS("next_tag_suggestions_rejected");

        internal val preferenceKey = stringPreferencesKey(key)
    }

    /** Meanings of the tags by id, tags without entry are themes, see [TagMeaning] */
    val meanings: Flow<Map<Long, TagMeaning>> =
        dataStore.data.map { TagMeaning.decode(it[KEY_MEANINGS]) }.distinctUntilChanged()

    val rules: Flow<List<TagRule>> = dataStore.data.map { TagRule.decode(it[KEY_RULES]) }.distinctUntilChanged()

    fun rejected(kind: Rejected): Flow<Set<String>> =
        dataStore.data.map { decodeKeys(it[kind.preferenceKey]) }.distinctUntilChanged()

    /** @param meaning null for a theme */
    suspend fun setMeaning(tagId: Long, meaning: TagMeaning?) {
        dataStore.edit { preferences ->
            val meanings = TagMeaning.decode(preferences[KEY_MEANINGS]).toMutableMap()
            if (meaning == null) meanings.remove(tagId) else meanings[tagId] = meaning
            preferences[KEY_MEANINGS] = TagMeaning.encode(meanings)
        }
    }

    /** Adds [rule], replacing one for the same counterpart and tag */
    suspend fun addRule(rule: TagRule) {
        dataStore.edit { preferences ->
            val rules = TagRule.decode(preferences[KEY_RULES])
                .filterNot { it.counterpart == rule.counterpart && it.tagId == rule.tagId }
            preferences[KEY_RULES] = TagRule.encode(rules + rule)
        }
    }

    suspend fun removeRule(rule: TagRule) {
        dataStore.edit { preferences ->
            preferences[KEY_RULES] = TagRule.encode(TagRule.decode(preferences[KEY_RULES]) - rule)
        }
    }

    /** Suggests again what the user said no to */
    suspend fun clearRejected() {
        dataStore.edit { preferences -> Rejected.entries.forEach { preferences.remove(it.preferenceKey) } }
    }

    suspend fun reject(kind: Rejected, key: String) {
        dataStore.edit { it[kind.preferenceKey] = encodeKeys(decodeKeys(it[kind.preferenceKey]) + key) }
    }

    private companion object {
        /** See [TagMeaning.encode] */
        val KEY_MEANINGS = stringPreferencesKey("next_tag_meanings")

        /** See [TagRule.encode] */
        val KEY_RULES = stringPreferencesKey("next_tag_rules")

        /** Keys separated by commas */
        fun decodeKeys(text: String?) = text.orEmpty().split(',').filterTo(HashSet()) { it.isNotEmpty() }

        fun encodeKeys(keys: Set<String>) = keys.sorted().joinToString(",")
    }
}
