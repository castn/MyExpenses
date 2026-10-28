package org.totschnig.myexpenses.charts.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SankeyLayoutTest {

    // 0: income A, 1: income B → 2: me → 3: food, 4: home → 5: REWE, 6: rest of food
    private val columns = listOf(0, 0, 1, 2, 2, 3, 3)
    private val links = listOf(
        FlowLink(0, 2, 60.0),
        FlowLink(1, 2, 40.0),
        FlowLink(2, 3, 30.0),
        FlowLink(2, 4, 70.0),
        FlowLink(3, 5, 20.0),
        FlowLink(3, 6, 10.0),
    )

    private fun layout(gap: Float = 10f) = SankeyLayout(columns, links, width = 400f, height = 210f, nodeWidth = 10f, nodeGap = gap, trailing = 100f)

    @Test
    fun scalesAllColumnsAlikeSoThatTheFullestFits() {
        val layout = layout()
        // Two columns hold 100 with one gap: (210 - 10) / 100
        assertEquals(120f, layout.nodes[0].height, 0.01f)
        assertEquals(80f, layout.nodes[1].height, 0.01f)
        assertEquals(200f, layout.nodes[2].height, 0.01f)
        assertEquals(40f, layout.nodes[5].height, 0.01f)
        assertEquals(60f, layout.bands[2].thickness, 0.01f)
    }

    @Test
    fun centersColumnsAndKeepsTheirOrder() {
        val layout = layout()
        assertEquals(0f, layout.nodes[0].top, 0.01f)
        assertEquals(130f, layout.nodes[1].top, 0.01f)
        assertEquals(5f, layout.nodes[2].top, 0.01f)
        // 20 + 10 in a column that could hold 100: centered
        val used = layout.nodes[6].bottom - layout.nodes[5].top
        assertEquals((210f - used) / 2, layout.nodes[5].top, 0.01f)
    }

    @Test
    fun placesColumnsEvenlyBeforeTheTrailingSpace() {
        val layout = layout()
        assertEquals(listOf(0f, 0f, 96.67f, 193.33f, 193.33f, 290f, 290f), layout.nodes.map { Math.round(it.x * 100) / 100f })
        assertEquals(100f, layout.labelSpace(5, padding = 0f), 0.01f)
        assertEquals(96.67f - 10f - 8f, layout.labelSpace(0, padding = 4f), 0.01f)
    }

    @Test
    fun bandsLeaveInTheOrderOfTheirTargets() {
        val layout = layout()
        val me = layout.nodes[2]
        assertEquals(me.top, layout.bands[0].targetTop, 0.01f)
        assertEquals(me.top + 120f, layout.bands[1].targetTop, 0.01f)
        assertEquals(me.top, layout.bands[2].sourceTop, 0.01f)
        assertEquals(me.top + 60f, layout.bands[3].sourceTop, 0.01f)
        assertEquals(layout.nodes[3].top, layout.bands[2].targetTop, 0.01f)
    }

    @Test
    fun bandsAreCurvesFromSourceToTarget() {
        val band = LinkBand(sourceX = 0f, sourceTop = 0f, targetX = 100f, targetTop = 100f, thickness = 10f)
        assertEquals(0f, band.topAt(0f), 0.1f)
        assertEquals(50f, band.topAt(50f), 0.1f)
        assertEquals(100f, band.topAt(100f), 0.1f)
        assertTrue(band.topAt(25f) < 25f)
    }

    @Test
    fun narrowsGapsOfCrowdedColumns() {
        val many = SankeyLayout(listOf(0) + List(11) { 1 }, List(11) { FlowLink(0, it + 1, 1.0) }, 100f, 100f, 4f, nodeGap = 20f)
        // At most half of the height for 10 gaps
        assertEquals(5f, many.nodes[2].top - many.nodes[1].bottom, 0.01f)
        assertEquals(50f, many.nodes.drop(1).sumOf { it.height.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun hitsNodesBeforeBandsAndThinnerBandsFirst() {
        val layout = layout()
        assertEquals(SankeyHit.Node(2), layout.hit(layout.nodes[2].x + 5f, 100f, slop = 4f))
        val band = layout.bands[4]
        val x = (band.sourceX + band.targetX) / 2
        assertEquals(SankeyHit.Link(4), layout.hit(x, band.topAt(x) + band.thickness / 2, slop = 4f))
        assertNull(layout.hit(x, 209f, slop = 4f))
    }

    @Test
    fun thinBandsCanBeHitWithinTheSlop() {
        val layout = SankeyLayout(listOf(0, 1), listOf(FlowLink(0, 1, 1.0)), 100f, 1f, 10f, 0f)
        assertEquals(SankeyHit.Link(0), layout.hit(50f, 4f, slop = 12f))
    }
}
