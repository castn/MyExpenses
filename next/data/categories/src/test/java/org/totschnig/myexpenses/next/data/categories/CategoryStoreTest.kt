package org.totschnig.myexpenses.next.data.categories

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore

class CategoryStoreTest {

    @Test
    fun storesWhatTheUpdateMakesOfTheDecisions() = withTestDataStore { dataStore ->
        val store = CategoryStore(dataStore)
        assertEquals(CategoryDecisions(), store.decisions.first())
        store.update { it.copy(firstNewId = 100, rules = listOf(CategoryRule("r", setOf(1), 10))) }
        store.update { it.rejecting(transactionId = 7, categoryId = 10) }
        store.update { it.copy(firstNewId = null, backlogAnswered = true) }
        assertEquals(
            CategoryDecisions(
                rules = listOf(CategoryRule("r", setOf(1), 10)),
                firstNewId = null,
                backlogAnswered = true,
                rejected = mapOf(7L to 10L)
            ),
            store.decisions.first()
        )
    }

    @Test
    fun ruleThatCannotBeReadSurvivesUpdate() = withTestDataStore { dataStore ->
        val key = stringPreferencesKey("next_category_rules")
        val later = """[{"id":"later","category":10,"payeesFromALaterVersion":[1]}]"""
        dataStore.edit { it[key] = later }
        val store = CategoryStore(dataStore)
        val rule = CategoryRule("r", setOf(1), 10)
        store.update { it.copy(rules = listOf(rule)) }
        assertEquals(listOf(rule), store.decisions.first().rules)
        val stored = JSONArray(dataStore.data.first()[key])
        assertEquals("later", stored.getJSONObject(1).getString("id"))
    }
}
