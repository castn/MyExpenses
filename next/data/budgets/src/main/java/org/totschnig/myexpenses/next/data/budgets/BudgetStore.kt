package org.totschnig.myexpenses.next.data.budgets

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import org.totschnig.myexpenses.next.core.common.longs
import org.totschnig.myexpenses.next.core.common.parseJsonArray
import org.totschnig.myexpenses.next.core.common.strings
import org.totschnig.myexpenses.next.core.common.toJsonArray

/**
 * The pots of the user, stored in the data store, which is part of the backup. The budgets of the
 * classic screens are not touched.
 */
class BudgetStore(private val dataStore: DataStore<Preferences>) {

    /** In the order the user sees them */
    val pots: Flow<List<Pot>> = dataStore.data.map { preferences ->
        preferences[KEY_POTS]?.let(::parsePots) ?: emptyList()
    }

    /** Adds [pot], or replaces the pot with its id in its place */
    suspend fun save(pot: Pot) = editPots { pots ->
        if (pots.any { it.id == pot.id }) pots.map { if (it.id == pot.id) pot else it } else pots + pot
    }

    suspend fun delete(id: String) = editPots { pots -> pots.filter { it.id != id } }

    /** Puts the pots in the order of [ids]; pots not listed keep their place at the end */
    suspend fun reorder(ids: List<String>) = editPots { pots ->
        pots.sortedBy { pot -> ids.indexOf(pot.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
    }

    /** Whether the user took over or declined the budgets of the classic screens, see [LegacyBudgets] */
    val legacyHandled: Flow<Boolean> = dataStore.data.map { it[KEY_LEGACY_HANDLED] == true }

    suspend fun setLegacyHandled() {
        dataStore.edit { it[KEY_LEGACY_HANDLED] = true }
    }

    /** Adds [pots] after the existing ones and marks the budgets of the classic screens as handled */
    suspend fun addFromLegacy(pots: List<Pot>) {
        dataStore.edit { preferences ->
            val existing = preferences[KEY_POTS]?.let(::parsePots) ?: emptyList()
            preferences[KEY_POTS] = serializePots(existing + pots)
            preferences[KEY_LEGACY_HANDLED] = true
        }
    }

    /** Whether the user closed the invitation to a first budget in the overview */
    val inviteDismissed: Flow<Boolean> = dataStore.data.map { it[KEY_INVITE_DISMISSED] == true }

    suspend fun dismissInvite() {
        dataStore.edit { it[KEY_INVITE_DISMISSED] = true }
    }

    private suspend fun editPots(block: (List<Pot>) -> List<Pot>) {
        dataStore.edit { preferences ->
            val pots = preferences[KEY_POTS]?.let(::parsePots) ?: emptyList()
            preferences[KEY_POTS] = serializePots(block(pots))
        }
    }

    private companion object {
        /** JSON array of [Pot]s */
        val KEY_POTS = stringPreferencesKey("next_budget_pots")
        val KEY_LEGACY_HANDLED = booleanPreferencesKey("next_budget_legacy_handled")
        val KEY_INVITE_DISMISSED = booleanPreferencesKey("next_budget_invite_dismissed")
    }
}

/*
 * How pots are stored as JSON. What cannot be read, e.g. a kind from a later version, is left out.
 */

fun parsePots(json: String): List<Pot> = parseJsonArray(json) {
    with(it) {
        Pot(
            id = getString("id"),
            name = getString("name"),
            kind = PotKind.valueOf(getString("kind")),
            amount = optLong("amount"),
            categoryIds = optJSONArray("categories")?.longs() ?: emptySet(),
            tagIds = optJSONArray("tags")?.longs() ?: emptySet(),
            payeeIds = optJSONArray("payees")?.longs() ?: emptySet(),
            contractRuleIds = optJSONArray("contracts")?.strings() ?: emptySet(),
            rollover = optBoolean("rollover"),
            start = if (has("start")) LocalDate.parse(getString("start")) else null,
            end = if (has("end")) LocalDate.parse(getString("end")) else null,
            reserveRuleIds = optJSONArray("reserves")?.strings() ?: emptySet(),
            savingAccountIds = optJSONArray("savingAccounts")?.longs() ?: emptySet(),
            itemGroups = optJSONArray("itemGroups")?.strings() ?: emptySet(),
            maxSharePercent = if (has("maxShare")) getInt("maxShare") else null,
            icon = if (has("icon")) getString("icon") else null
        )
    }
}

fun serializePots(pots: List<Pot>) = pots.toJsonArray { pot ->
    JSONObject().apply {
        put("id", pot.id)
        put("name", pot.name)
        put("kind", pot.kind.name)
        put("amount", pot.amount)
        putSet("categories", pot.categoryIds)
        putSet("tags", pot.tagIds)
        putSet("payees", pot.payeeIds)
        putSet("contracts", pot.contractRuleIds)
        if (pot.rollover) put("rollover", true)
        pot.start?.let { put("start", it.toString()) }
        pot.end?.let { put("end", it.toString()) }
        putSet("reserves", pot.reserveRuleIds)
        putSet("savingAccounts", pot.savingAccountIds)
        putSet("itemGroups", pot.itemGroups)
        pot.maxSharePercent?.let { put("maxShare", it) }
        pot.icon?.let { put("icon", it) }
    }
}

private fun JSONObject.putSet(name: String, values: Set<*>) {
    if (values.isNotEmpty()) put(name, JSONArray(values.toList()))
}
