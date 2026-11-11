package org.totschnig.myexpenses.next.core.common

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonCodecTest {

    @Test
    fun leavesOutElementsThatCannotBeRead() {
        val json = """[{"name":"a"},{"other":1},{"name":"b"}]"""
        assertEquals(listOf("a", "b"), parseJsonArray(json) { it.getString("name") })
    }

    @Test
    fun leavesOutElementsOfUnknownTypes() {
        val json = """[{"kind":"CONTRACT"},{"kind":"FROM_A_LATER_VERSION"}]"""
        assertEquals(listOf(Kind.CONTRACT), parseJsonArray(json) { Kind.valueOf(it.getString("kind")) })
    }

    @Test
    fun readsBrokenJsonAsEmpty() {
        assertEquals(emptyList<String>(), parseJsonArray("not json") { it.getString("name") })
        assertEquals(emptyList<String>(), parseJsonArray("{}") { it.getString("name") })
    }

    @Test
    fun writesWhatItReads() {
        val names = listOf("a", "b")
        val json = names.toJsonArray { JSONObject().put("name", it) }
        assertEquals(names, parseJsonArray(json) { it.getString("name") })
    }

    @Test
    fun writingKeepsWhatCouldNotBeRead() {
        val previous = """[{"kind":"CONTRACT"},{"kind":"FROM_A_LATER_VERSION","x":1},7]"""
        val read: (JSONObject) -> Kind = { Kind.valueOf(it.getString("kind")) }
        val kinds = parseJsonArray(previous, read)
        val json = (kinds + Kind.CONTRACT).toJsonArray(previous, read) {
            JSONObject().put("kind", it.name)
        }
        val written = JSONArray(json)
        assertEquals(4, written.length())
        assertEquals("CONTRACT", written.getJSONObject(1).getString("kind"))
        assertEquals("FROM_A_LATER_VERSION", written.getJSONObject(2).getString("kind"))
        assertEquals(1, written.getJSONObject(2).getInt("x"))
        assertEquals(7, written.getInt(3))
    }

    @Test
    fun writingWithoutPreviousWritesOnlyTheItems() {
        val read: (JSONObject) -> String = { it.getString("name") }
        val json = listOf("a").toJsonArray(null, read) { JSONObject().put("name", it) }
        assertEquals(listOf("a"), parseJsonArray(json) { it.getString("name") })
    }

    @Test
    fun readsSetsOfValues() {
        assertEquals(setOf(1L, 2L), JSONArray(listOf(1, 2, 2)).longs())
        assertEquals(setOf("x", "y"), JSONArray(listOf("x", "y")).strings())
    }

    private enum class Kind { CONTRACT }
}
