package org.totschnig.myexpenses.next.data.categories

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryCodecsTest {

    @Test
    fun writesAndReadsRules() {
        val rules = listOf(
            CategoryRule("r1", setOf(1, 2), 10, setOf("rewe")),
            CategoryRule("r2", setOf(3), 11)
        )
        assertEquals(rules, parseRules(serializeRules(rules)))
        assertEquals(emptyList<CategoryRule>(), parseRules("broken"))
    }

    @Test
    fun writesAndReadsCategoriesByTransaction() {
        val map = mapOf(100L to 10L, 101L to 11L)
        assertEquals(map, parseIdMap(serializeIdMap(map)))
    }

    @Test
    fun leavesOutEntriesThatAreNoTransaction() {
        assertEquals(mapOf(100L to 10L), parseIdMap("""{"100":10,"x":11}"""))
        assertEquals(emptyMap<Long, Long>(), parseIdMap("broken"))
    }
}
