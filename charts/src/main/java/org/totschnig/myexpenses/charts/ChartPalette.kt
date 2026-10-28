package org.totschnig.myexpenses.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The color of a mark in a chart that tells things apart, e.g. categories. Colors follow the thing,
 * not its rank: a thing keeps its color when others are left out.
 */
sealed interface ChartColor {
    /** One of the [ChartPalette.SERIES_COUNT] colors of series, by [slot] from 0 */
    data class Series(val slot: Int) : ChartColor {
        init {
            require(slot in 0 until ChartPalette.SERIES_COUNT) { "No series color $slot" }
        }
    }

    /** For what is no series of its own, e.g. a rest or a total */
    data object Neutral : ChartColor
}

/**
 * Colors for series, checked to be told apart with color vision deficiencies when next to each
 * other in this order, on the light and the dark surface. Three of the light ones are lighter than
 * 3:1 against the surface, so charts with them name their marks directly. More things than slots
 * are summed up into a rest, never given a generated color.
 */
object ChartPalette {
    const val SERIES_COUNT = 8

    /** Slot of green, e.g. for money that comes in */
    const val GREEN = 5

    private val light = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF6250D6), Color(0xFFE34948),
    )
    private val dark = listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9), Color(0xFFE66767),
    )

    /**
     * The slots in the order to give them to things of one kind, without [GREEN]: checked in
     * this order as well
     */
    val withoutGreen: List<Int> = listOf(0, 1, 2, 3, 4, 6, 7)

    @Composable
    @ReadOnlyComposable
    fun color(color: ChartColor): Color = when (color) {
        is ChartColor.Series -> {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            (if (isDark) dark else light)[color.slot]
        }
        ChartColor.Neutral -> MaterialTheme.colorScheme.outline
    }
}
