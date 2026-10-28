package org.totschnig.myexpenses.next.data.tips

import org.junit.Assert.assertEquals
import org.junit.Test

class TipDecisionsTest {

    @Test
    fun writesAndReadsFactsByTip() {
        val map = mapOf("prices" to setOf("netflix:1799", "strom:9800"), "fees" to emptySet())
        assertEquals(map, TipDecisions.parse(TipDecisions.serialize(map)))
    }

    @Test
    fun readsMissingOrBrokenJsonAsEmpty() {
        assertEquals(emptyMap<String, Set<String>>(), TipDecisions.parse(null))
        assertEquals(emptyMap<String, Set<String>>(), TipDecisions.parse("broken"))
    }
}
