package org.totschnig.myexpenses.next.data.categories

import kotlinx.coroutines.flow.first
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
}
