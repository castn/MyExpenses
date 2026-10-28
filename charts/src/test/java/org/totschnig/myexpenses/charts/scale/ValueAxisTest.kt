package org.totschnig.myexpenses.charts.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueAxisTest {

    private fun ticks(min: Double, max: Double, maxTicks: Int = ValueAxis.MAX_TICKS) =
        ValueAxis.of(min, max, maxTicks).ticks

    @Test
    fun roundStepsFromZero() {
        assertEquals(listOf(0.0, 50.0, 100.0, 150.0), ticks(0.0, 101.0))
        assertEquals(listOf(0.0, 100.0, 200.0, 300.0), ticks(0.0, 287.0))
        assertEquals(listOf(0.0, 2500.0, 5000.0), ticks(0.0, 4321.0, maxTicks = 3))
    }

    @Test
    fun maxOnATickNeedsNoExtraTick() {
        assertEquals(listOf(0.0, 100.0, 200.0), ticks(0.0, 200.0, maxTicks = 3))
    }

    @Test
    fun alwaysIncludesZero() {
        assertEquals(0.0, ticks(80.0, 120.0).first(), 0.0)
        assertEquals(0.0, ticks(-120.0, -80.0).last(), 0.0)
    }

    @Test
    fun negativeAndPositive() {
        assertEquals(listOf(-50.0, 0.0, 50.0, 100.0), ticks(-30.0, 90.0))
    }

    @Test
    fun neverMoreThanMaxTicks() {
        listOf(0.9, 7.0, 13.0, 99.0, 101.0, 999.0, 1001.0, 123456.0).forEach { max ->
            (3..6).forEach { maxTicks ->
                val ticks = ticks(-max / 3, max, maxTicks)
                assertTrue("$max/$maxTicks: $ticks", ticks.size <= maxTicks)
                assertTrue("$max/$maxTicks: $ticks", ticks.last() >= max && ticks.first() <= -max / 3)
            }
        }
    }

    @Test
    fun negativeAndPositiveNeedThreeTicks() {
        assertEquals(listOf(-100.0, 0.0, 100.0), ticks(-30.0, 90.0, maxTicks = 2))
    }

    @Test
    fun smallValues() {
        assertEquals(listOf(0.0, 0.2, 0.4), ticks(0.0, 0.4, maxTicks = 3))
    }

    @Test
    fun noValuesGiveAUnitAxis() {
        assertEquals(listOf(0.0, 1.0), ticks(0.0, 0.0))
    }

    @Test
    fun fraction() {
        val axis = ValueAxis.of(0.0, 101.0)
        assertEquals(0f, axis.fraction(0.0))
        assertEquals(0.5f, axis.fraction(75.0))
        assertEquals(1f, axis.fraction(1000.0))
        assertEquals(0f, axis.fraction(-5.0))
    }
}
