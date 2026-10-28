package org.totschnig.myexpenses.charts.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ViewportTest {

    private val width = 400f
    private val height = 300f

    private fun Viewport.zoom(factor: Float, x: Float = 0f, y: Float = 0f, panX: Float = 0f, panY: Float = 0f) =
        transformed(factor, x, y, panX, panY, width, height)

    @Test
    fun keepsTheFocusInPlace() {
        val zoomed = Viewport().zoom(2f, x = 100f, y = 150f)
        assertEquals(2f, zoomed.scale)
        assertEquals(100f, zoomed.toChartX(100f), 0.01f)
        assertEquals(150f, zoomed.toChartY(150f), 0.01f)
        assertEquals(50f, zoomed.toChartX(0f), 0.01f)
    }

    @Test
    fun staysWithinItsLimits() {
        assertEquals(1f, Viewport().zoom(0.5f).scale)
        assertEquals(Viewport.MAX_SCALE, Viewport().zoom(100f).scale)
    }

    @Test
    fun cannotBeMovedOutOfTheView() {
        val zoomed = Viewport().zoom(2f)
        assertEquals(Viewport(2f, 0f, 0f), zoomed.zoom(1f, panX = 50f, panY = 50f))
        assertEquals(Viewport(2f, -400f, -300f), zoomed.zoom(1f, panX = -1000f, panY = -1000f))
        assertEquals(Viewport(), Viewport().zoom(1f, panX = -20f))
    }

    @Test
    fun zoomingOutShowsTheWholeChartAgain() {
        val zoomed = Viewport().zoom(3f, x = 300f, y = 200f)
        val back = zoomed.zoom(1 / 3f, x = 10f, y = 10f)
        assertEquals(Viewport(), back)
        assertFalse(back.isZoomed)
    }
}
