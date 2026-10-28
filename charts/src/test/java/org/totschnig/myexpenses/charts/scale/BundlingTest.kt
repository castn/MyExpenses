package org.totschnig.myexpenses.charts.scale

import org.junit.Assert.assertEquals
import org.junit.Test

class BundlingTest {

    private fun bundle(values: List<Int>, limit: Int = MAX_ENTRIES) =
        largestWithRest(values.map { "$it" to it }, { it.second.toDouble() }, limit) { rest ->
            "rest" to rest.sumOf { it.second }
        }

    @Test
    fun fewEntriesAreOnlySorted() {
        assertEquals(listOf("3" to 3, "2" to 2, "1" to 1), bundle(listOf(1, 3, 2)))
    }

    @Test
    fun exactlyTheLimitIsNotBundled() {
        assertEquals(MAX_ENTRIES, bundle((1..MAX_ENTRIES).toList()).size)
        assertEquals("1" to 1, bundle((1..MAX_ENTRIES).toList()).last())
    }

    @Test
    fun theSmallestAreSummedUp() {
        assertEquals(
            listOf("9" to 9, "8" to 8, "rest" to 7 + 5 + 1),
            bundle(listOf(5, 9, 1, 8, 7), limit = 3)
        )
    }

    @Test
    fun empty() {
        assertEquals(emptyList<Pair<String, Int>>(), bundle(emptyList()))
    }
}
