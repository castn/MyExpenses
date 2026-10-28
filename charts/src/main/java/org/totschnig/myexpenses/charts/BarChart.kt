package org.totschnig.myexpenses.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.charts.scale.MAX_ENTRIES
import org.totschnig.myexpenses.charts.scale.largestWithRest

/**
 * One bar of a [BarChart]
 *
 * @param label before the bar, short, e.g. the name of a category
 * @param value the length, negative values are drawn as zero
 * @param valueLabel the formatted value, shown at the end of the bar
 * @param description what the bar stands for, read out with the value
 * @param comparison the value of the same thing at another time, e.g. the month before, drawn as
 * a mark across the bar
 * @param comparisonLabel the formatted [comparison], read out after the value
 */
data class ChartBar(
    val label: String,
    val value: Float,
    val valueLabel: String,
    val description: String = label,
    val comparison: Float? = null,
    val comparisonLabel: String? = null,
)

/**
 * Parts of a whole as horizontal bars from one baseline, largest first, each with its value at
 * the end. Better to compare than the slices of a pie. One series in one color; a [comparison]
 * [ChartBar.comparison] is drawn as a mark, and then a legend names both.
 *
 * At most [MAX_ENTRIES] bars, so that the chart stays readable: bundle the smallest into one with
 * [largestWithRest].
 *
 * @param valueName what the bars show, e.g. "October", named in the legend
 * @param comparisonName what the marks show, e.g. "September", named in the legend
 * @param onClick of a bar, by index, e.g. to open its details, none if null
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    valueName: String? = null,
    comparisonName: String? = null,
    onClick: ((Int) -> Unit)? = null,
) {
    require(bars.size <= MAX_ENTRIES) { "At most $MAX_ENTRIES bars, bundle the rest with largestWithRest" }
    val max = bars.maxOfOrNull { maxOf(it.value, it.comparison ?: 0f) }?.takeIf { it > 0f } ?: 1f
    val valueStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val valueWidth = remember(bars, valueStyle) {
        with(density) { (bars.maxOfOrNull { measurer.measure(it.valueLabel, valueStyle).size.width } ?: 0).toDp() }
    }
    val hasComparison = bars.any { it.comparison != null }
    Column(modifier.fillMaxWidth()) {
        if (hasComparison && valueName != null && comparisonName != null) {
            ChartLegend(
                listOf(LegendItem(valueName, LegendMark.Bar), LegendItem(comparisonName, LegendMark.Line)),
                Modifier.padding(bottom = 8.dp)
            )
        }
        bars.forEachIndexed { index, bar ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RowHeight)
                    .then(if (onClick != null) Modifier.clickable { onClick(index) } else Modifier)
                    .semantics(mergeDescendants = true) {
                        contentDescription = buildString {
                            append("${bar.description}: ${bar.valueLabel}")
                            if (bar.comparisonLabel != null && comparisonName != null) {
                                append(", $comparisonName: ${bar.comparisonLabel}")
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    bar.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(LabelShare)
                        .padding(end = 12.dp)
                )
                BoxWithConstraints(Modifier.weight(1f - LabelShare)) {
                    val plot = (maxWidth - valueWidth - ValueGap).coerceAtLeast(0.dp)
                    val length = plot * (bar.value.coerceAtLeast(0f) / max)
                    val comparison = bar.comparison?.let { plot * (it.coerceAtLeast(0f) / max) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // As long as the bar or the mark, so that the value never covers the mark
                        Box(
                            Modifier.width(maxOf(length, comparison ?: 0.dp)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                Modifier
                                    .width(length)
                                    .height(BarThickness)
                                    // Rounded at the data end, square at the baseline
                                    .clip(BarShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            if (comparison != null) {
                                Box(
                                    Modifier
                                        .offset(x = (comparison - MarkWidth / 2).coerceAtLeast(0.dp))
                                        .width(MarkWidth)
                                        .height(MarkHeight)
                                        .background(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                        Text(
                            bar.valueLabel,
                            style = valueStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = ValueGap)
                        )
                    }
                }
            }
        }
    }
}

/** Large enough to tap */
private val RowHeight = 40.dp
private val BarThickness = 16.dp
private val BarShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
private val MarkWidth = 2.dp
private val MarkHeight = 24.dp
private val ValueGap = 8.dp

/** Of the width, for the labels before the bars */
private const val LabelShare = 0.35f

@Preview(showBackground = true)
@Composable
private fun BarChartPreview() {
    MaterialTheme {
        BarChart(
            listOf(
                ChartBar("Wohnen", 950f, "950 €", comparison = 950f, comparisonLabel = "950 €"),
                ChartBar("Lebensmittel", 420f, "420 €", comparison = 510f, comparisonLabel = "510 €"),
                ChartBar("Mobilität", 180f, "180 €", comparison = 90f, comparisonLabel = "90 €"),
                ChartBar("Others", 75f, "75 €"),
            ),
            Modifier.padding(16.dp),
            valueName = "October",
            comparisonName = "September"
        )
    }
}
