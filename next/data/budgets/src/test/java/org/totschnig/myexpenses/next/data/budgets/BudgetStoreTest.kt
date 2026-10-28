package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore

class BudgetStoreTest {

    private val full = Pot(
        id = "goal",
        name = "Neues Spiel",
        kind = PotKind.GOAL,
        amount = 7000,
        categoryIds = setOf(1, 2),
        tagIds = setOf(3),
        payeeIds = setOf(4),
        contractRuleIds = setOf("rule"),
        rollover = true,
        start = LocalDate.of(2026, 10, 1),
        end = LocalDate.of(2026, 12, 24),
        reserveRuleIds = setOf("reserve"),
        savingAccountIds = setOf(5),
        itemGroups = setOf("sweets_snacks"),
        maxSharePercent = 8,
        icon = "gamepad"
    )

    private val plain = Pot(id = "running", name = "Ausgehen", kind = PotKind.RUNNING, amount = 15000)

    @Test
    fun codecKeepsAllFields() {
        assertEquals(listOf(full, plain), parsePots(serializePots(listOf(full, plain))))
    }

    @Test
    fun unknownKindIsLeftOut() {
        val json = """[{"id":"a","name":"A","kind":"LATER"},{"id":"b","name":"B","kind":"RUNNING"}]"""
        assertEquals(listOf(Pot(id = "b", name = "B", kind = PotKind.RUNNING)), parsePots(json))
    }

    @Test
    fun savesReplacesReordersAndDeletes() = withTestDataStore { dataStore ->
        val store = BudgetStore(dataStore)
        store.save(plain)
        store.save(full)
        assertEquals(listOf(plain, full), store.pots.first())
        val renamed = plain.copy(name = "Restaurants & Bars")
        store.save(renamed)
        assertEquals(listOf(renamed, full), store.pots.first())
        store.reorder(listOf(full.id, renamed.id))
        assertEquals(listOf(full, renamed), store.pots.first())
        store.delete(full.id)
        assertEquals(listOf(renamed), store.pots.first())
    }
}
