package org.totschnig.myexpenses.next.data.contracts

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore

class ContractStoreTest {

    private val rule = ContractRule(
        "r",
        ContractRule.Kind.CONTRACT,
        ContractDirection.EXPENSE,
        setOf(1),
        interval = ContractInterval.MONTHLY
    )

    @Test
    fun keepsRulesSalaryAreasAndNewsOfOneEditTogether() = withTestDataStore { dataStore ->
        val store = ContractStore(dataStore)
        val news = ContractNews(
            id = "n", type = ContractNews.Type.NEW_CONTRACT, ruleId = "r", transactionId = 1,
            date = LocalDate.of(
                2026,
                10,
                6
            ),
            createdAt = LocalDate.of(2026, 10, 6), contractName = "Strom",
            isIncome = false, amount = 4500
        )
        store.edit { editor ->
            editor.rules = listOf(rule)
            editor.setSalary(SalaryChoice.Fixed(setOf("p:3")))
            editor.customAreas = listOf(CustomArea("a", "Hund"))
            editor.news = listOf(news)
            editor.markRulesMigrated(dropOld = true)
        }
        val settings = store.settings.first()
        assertEquals(listOf(rule), settings.rules)
        assertEquals(SalaryChoice.Fixed(setOf("p:3")), settings.salary)
        assertEquals(listOf(CustomArea("a", "Hund")), settings.customAreas)
        assertTrue(settings.rulesMigrated)
        assertEquals(listOf(news), store.news.first())
    }

    @Test
    fun replacesSalaryOfMergedContract() = withTestDataStore { dataStore ->
        val store = ContractStore(dataStore)
        store.edit { it.setSalary(SalaryChoice.Fixed(setOf("old", "other"))) }
        store.edit { it.replaceSalary("old", "new") }
        assertEquals(SalaryChoice.Fixed(setOf("new", "other")), store.settings.first().salary)
    }

    @Test
    fun rulesNewsAndAreasOfALaterVersionSurviveEditing() = withTestDataStore { dataStore ->
        val keys = listOf("rules", "news", "custom_areas")
            .map { stringPreferencesKey("next_contracts_$it") }
        val later = """[{"id":"later","kind":"LATER","type":"LATER"}]"""
        dataStore.edit { preferences -> keys.forEach { preferences[it] = later } }
        val store = ContractStore(dataStore)
        store.edit { editor ->
            editor.rules = editor.rules + rule
            editor.news = editor.news
            editor.customAreas = editor.customAreas
        }
        assertEquals(listOf(rule), store.settings.first().rules)
        val stored = dataStore.data.first()
        assertEquals(2, JSONArray(stored[keys[0]]).length())
        keys.forEach { key ->
            val array = JSONArray(stored[key])
            assertEquals("later", array.getJSONObject(array.length() - 1).getString("id"))
        }
    }
}
