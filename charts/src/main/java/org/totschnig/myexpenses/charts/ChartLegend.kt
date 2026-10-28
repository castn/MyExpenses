package org.totschnig.myexpenses.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp

/** How a series is drawn, shown before its name in a [ChartLegend] */
enum class LegendMark {
    /** A filled bar or column */
    Bar,

    /** A thin line, e.g. the mark of a comparison */
    Line,
}

/**
 * @param color of the mark, by default the one [mark] has in the charts: primary for bars,
 * onSurface for lines
 */
data class LegendItem(val name: String, val mark: LegendMark, val color: Color = Color.Unspecified)

/**
 * Names the series of a chart that shows more than one. The names are in the colors of text, the
 * mark before each carries the color of its series.
 */
@Composable
fun ChartLegend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = item.color.takeOrElse {
                    when (item.mark) {
                        LegendMark.Bar -> MaterialTheme.colorScheme.primary
                        LegendMark.Line -> MaterialTheme.colorScheme.onSurface
                    }
                }
                when (item.mark) {
                    LegendMark.Bar -> Box(Modifier.size(12.dp).background(color, RoundedCornerShape(4.dp)))
                    LegendMark.Line -> Box(Modifier.size(width = 2.dp, height = 16.dp).background(color))
                }
                Text(
                    item.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
