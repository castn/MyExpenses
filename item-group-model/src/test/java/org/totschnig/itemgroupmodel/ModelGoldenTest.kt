package org.totschnig.itemgroupmodel

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rechnet wie die Referenz in Python (src/pgm/export.py): Wahrscheinlichkeiten mit Toleranz,
 * Reihenfolge der besten Klassen und Kette des Ladens exakt.
 */
class ModelGoldenTest {

    private fun resource(name: String) = javaClass.getResourceAsStream("/item_groups/$name")!!.readBytes()

    private fun unescape(s: String) = s.replace("\\t", "\t").replace("\\n", "\n").replace("\\\\", "\\")

    private fun check(model: ProductGroupModel, tsv: String) {
        val rows = resource(tsv).decodeToString().lines().filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue(rows.isNotEmpty())
        for (line in rows) {
            val (rawName, rawStore, chain, margin, top) = line.split('\t')
            val name = unescape(rawName)
            val store = unescape(rawStore).ifEmpty { null }
            assertEquals(chain.ifEmpty { null }, model.chainOf(store), "Kette: $store")
            val p = model.probabilities(name, store)
            if (top.isEmpty()) {
                assertNull(p, "keine Merkmale erwartet: $name")
                continue
            }
            val expected = top.split(',').map { it.split('=').let { (c, v) -> c to v.toFloat() } }
            for ((cls, prob) in expected) {
                val actual = p!![model.classes.indexOf(cls)]
                assertTrue(abs(actual - prob) <= 1e-5f, "$name / $cls: $actual statt $prob")
            }
            if (margin.toFloat() >= 1e-4f) {
                assertEquals(expected.map { it.first }, model.topK(name, store, expected.size).map { it.first }, name)
            }
        }
    }

    @Test
    fun tinyModel() {
        val model = ProductGroupModel.load(resource("tiny.pgm"))
        assertEquals("tiny-1", model.version)
        assertEquals(listOf("produce", "produce", "dairy", "dairy", "household", "alcohol"), model.groups)
        check(model, "tiny.tsv")
    }

    @Test
    fun classifyUsesThresholds() {
        val model = ProductGroupModel.load(resource("tiny.pgm"))
        // Das Zufallsmodell ist nie sicher: Wahrscheinlichkeiten um 1/6
        assertNull(model.classify("Bio Vollmilch", "REWE"))
        assertNull(model.classify("🍓"))
        assertTrue(model.topK("Bio Vollmilch", k = 6).size == 6)
    }

    @Test
    fun corruptFileIsRejected() {
        val data = resource("tiny.pgm")
        data[100] = (data[100].toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException> { ProductGroupModel.load(data) }
    }
}
