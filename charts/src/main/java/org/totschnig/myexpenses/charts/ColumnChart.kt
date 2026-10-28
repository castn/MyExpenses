package org.totschnig.myexpenses.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.charts.scale.ValueAxis
import java.text.NumberFormat

/**
 * One column of a [ColumnChart]
 *
 * @param label below the column, short, e.g. "Nov"
 * @param value the height, negative values are drawn as zero
 * @param valueLabel the formatted value, shown when the column is selected
 * @param description what the column stands for, e.g. "November 2025", shown with the value
 */
data class ChartColumn(
    val label: String,
    val value: Float,
    val valueLabel: String,
    val description: String = label,
)

/**
 * A value for each of a few periods, e.g. the months of a year, as columns from one baseline. One
 * series in one color, so it needs no legend: the title above names it. Gridlines at round values
 * let the heights be read off, the axis labels come from [axisLabel]. Tapping a column shows its
 * value above the chart, the last column is shown at first. The values are also read out per
 * column, so the chart is never the only way to get them.
 *
 * @param axisLabel the text of a gridline, short, e.g. "150" for amounts, without currency
 */
@Composable
fun ColumnChart(
    columns: List<ChartColumn>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    axisLabel: (Double) -> String = defaultAxisLabel(),
) {
    var selected by remember(columns.size) { mutableIntStateOf(columns.lastIndex) }
    val axis = remember(columns) { ValueAxis.of(0.0, columns.maxOfOrNull { it.value.toDouble() } ?: 0.0) }
    val color = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val baselineColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Digits of equal width, so that the labels of the gridlines align
        fontFeatureSettings = "tnum"
    )
    val measurer = rememberTextMeasurer()
    val tickLabels = remember(axis, axisLabel, labelStyle) {
        axis.ticks.map { measurer.measure(axisLabel(it), labelStyle) }
    }
    val density = LocalDensity.current
    val gutter = with(density) { (tickLabels.maxOf { it.size.width }).toDp() } + AxisGap
    Column(modifier.fillMaxWidth()) {
        columns.getOrNull(selected)?.let { column ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    column.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(column.valueLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(top = 12.dp)
        ) {
            Canvas(Modifier.matchParentSize()) {
                val isRtl = layoutDirection == LayoutDirection.Rtl
                // Drawn from the start, mirrored for right-to-left
                fun x(start: Float) = if (isRtl) size.width - start else start
                val gutterPx = gutter.toPx()
                val top = tickLabels.first().size.height / 2f
                val plotHeight = size.height - top
                fun y(value: Double) = size.height - axis.fraction(value) * plotHeight

                axis.ticks.forEachIndexed { index, tick ->
                    val lineY = y(tick)
                    drawLine(
                        color = if (tick == 0.0) baselineColor else gridColor,
                        start = Offset(x(gutterPx), lineY),
                        end = Offset(x(size.width), lineY),
                        strokeWidth = 1f
                    )
                    val label = tickLabels[index]
                    val labelEnd = gutterPx - AxisGap.toPx()
                    val labelX = if (isRtl) size.width - labelEnd else labelEnd - label.size.width
                    drawText(label, topLeft = Offset(labelX, lineY - label.size.height / 2f))
                }

                val slot = (size.width - gutterPx) / columns.size
                val columnWidth = ColumnWidth.toPx().coerceAtMost(slot * 0.6f)
                val radius = CornerRadius(ColumnRadius.toPx())
                columns.forEachIndexed { index, column ->
                    val columnTop = y(column.value.toDouble())
                    if (columnTop >= size.height) return@forEachIndexed
                    val center = x(gutterPx + slot * (index + 0.5f))
                    val path = Path().apply {
                        // Rounded at the data end, square at the baseline
                        addRoundRect(
                            RoundRect(
                                left = center - columnWidth / 2,
                                top = columnTop,
                                right = center + columnWidth / 2,
                                bottom = size.height,
                                topLeftCornerRadius = radius,
                                topRightCornerRadius = radius,
                            )
                        )
                    }
                    drawPath(path, if (index == selected) color else color.copy(alpha = 0.45f))
                }
            }
            Row(
                Modifier
                    .matchParentSize()
                    .padding(start = gutter)
            ) {
                columns.forEachIndexed { index, column ->
                    // The whole slot reacts to taps, larger than the column itself
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selected = index }
                            .semantics {
                                contentDescription = "${column.description}: ${column.valueLabel}"
                                this.selected = index == selected
                            }
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = gutter, top = 4.dp)
        ) {
            columns.forEach { column ->
                Text(
                    column.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Numbers in the style of the locale, with at most two decimals */
@Composable
fun defaultAxisLabel(): (Double) -> String = remember {
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    return@remember { value: Double -> format.format(value) }
}

/** Thin, never filling its slot, so that the gaps between the columns are air */
private val ColumnWidth = 16.dp
private val ColumnRadius = 4.dp

/** Between the labels of the gridlines and the plot */
private val AxisGap = 8.dp

@Preview(showBackground = true)
@Composable
private fun ColumnChartPreview() {
    MaterialTheme {
        ColumnChart(
            listOf("Jul" to 120f, "Aug" to 340f, "Sep" to 0f, "Oct" to 210f).map { (month, value) ->
                ChartColumn(month, value, "${value.toInt()},00 €", "$month 2026")
            },
            Modifier.padding(16.dp)
        )
    }
}
