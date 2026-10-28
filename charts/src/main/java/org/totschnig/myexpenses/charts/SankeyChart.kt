package org.totschnig.myexpenses.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import org.totschnig.myexpenses.charts.layout.FlowLink
import org.totschnig.myexpenses.charts.layout.SankeyHit
import org.totschnig.myexpenses.charts.layout.SankeyLayout
import org.totschnig.myexpenses.charts.layout.Viewport

/**
 * A node of a [SankeyChart], e.g. a source of income or a category
 *
 * @param column from 0 at the start
 * @param valueLabel the formatted value, shown below the label when there is room
 * @param description what the node stands for, read out
 */
data class SankeyNode(
    val label: String,
    val valueLabel: String,
    val column: Int,
    val color: ChartColor,
    val description: String = "$label: $valueLabel",
)

/**
 * A flow between two nodes of a [SankeyChart], by their index, from an earlier to a later column
 *
 * @param value the thickness, negative values are not allowed
 */
data class SankeyFlow(val source: Int, val target: Int, val value: Float)

/**
 * How values flow from left to right through columns of nodes, e.g. from incomes over the account
 * to categories and payees. The height of a node and the thickness of a band show the value. A
 * band has the color of its target, or of its source if the target is [ChartColor.Neutral].
 *
 * It can be zoomed with two fingers or the buttons below it and moved while zoomed, a double
 * tap shows it whole again. The
 * nodes and bands are zoomed, but nodes keep their width and labels their size: a node is named
 * once it is high enough for its label, so zooming in names the smaller ones. Tapping a node or band passes it to
 * [onSelect], tapping beside them passes null; the [selected] one and what it connects are
 * highlighted. Each node is read out with its [SankeyNode.description] and can be selected that
 * way too.
 *
 * Mirrored for right-to-left: then the flow goes from right to left. It fills the size [modifier]
 * gives it together with the zoom buttons below, which must have a bounded height.
 */
@Composable
fun SankeyChart(
    nodes: List<SankeyNode>,
    flows: List<SankeyFlow>,
    modifier: Modifier = Modifier,
    selected: SankeyHit? = null,
    onSelect: (SankeyHit?) -> Unit = {},
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val measurer = rememberTextMeasurer()
    val surface = MaterialTheme.colorScheme.surface
    val nameStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        // A halo, so that a label stays readable on the bands
        shadow = Shadow(surface, blurRadius = with(density) { 3.dp.toPx() })
    )
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        shadow = nameStyle.shadow,
        fontFeatureSettings = "tnum"
    )
    val colors = nodes.map { ChartPalette.color(it.color) }
    val neutral = ChartPalette.color(ChartColor.Neutral)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var viewport by remember(nodes) { mutableStateOf(Viewport()) }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    Column(modifier) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { viewSize = it.toSize() }
        ) {
            val width = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val layout = remember(nodes, flows, width, heightPx) {
                with(density) {
                    SankeyLayout(
                        columns = nodes.map { it.column },
                        links = flows.map { FlowLink(it.source, it.target, it.value.toDouble()) },
                        width = width,
                        height = heightPx,
                        nodeWidth = NodeWidth.toPx(),
                        nodeGap = NodeGap.toPx(),
                        trailing = (width * 0.25f).coerceAtMost(LastLabels.toPx())
                    )
                }
            }
            val currentLayout by rememberUpdatedState(layout)
            // In right-to-left the chart is drawn mirrored, so taps are mirrored back
            fun startX(x: Float) = if (isRtl) width - x else x
            // Nodes keep their width when zoomed, only their place and height change
            val nodeWidth = with(density) { NodeWidth.toPx() }
            fun nodeStart(index: Int) = layout.nodes[index].x * viewport.scale + viewport.offsetX
            val highlighted = remember(selected, flows) { highlightOf(selected, flows) }

            Canvas(
                Modifier
                    .matchParentSize()
                    .pointerInput(width, heightPx) {
                        // Unlike detectTransformGestures, one finger is left to a scrolling parent
                        // as long as the chart is not zoomed
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                if (fingers >= 2 || viewport.isZoomed) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        val centroid = event.calculateCentroid()
                                        viewport = viewport.transformed(
                                            zoom, startX(centroid.x), centroid.y,
                                            if (isRtl) -pan.x else pan.x, pan.y, width, heightPx
                                        )
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(width, heightPx) {
                        detectTapGestures(
                            onDoubleTap = { viewport = Viewport() },
                            onTap = { position ->
                                val x = viewport.toChartX(startX(position.x))
                                val y = viewport.toChartY(position.y)
                                currentOnSelect(currentLayout.hit(x, y, TapSlop.toPx() / viewport.scale, nodeWidth / viewport.scale))
                            }
                        )
                    }
            ) {
                fun mirror(x: Float) = if (isRtl) size.width - x else x
                fun sy(y: Float) = y * viewport.scale + viewport.offsetY

                layout.bands.forEachIndexed { index, band ->
                    val flow = flows[index]
                    val color = if (nodes[flow.target].color == ChartColor.Neutral) colors[flow.source] else colors[flow.target]
                    val alpha = when {
                        highlighted == null -> 0.4f
                        index in highlighted.flows -> 0.7f
                        else -> 0.12f
                    }
                    drawBand(
                        mirror(nodeStart(flow.source) + nodeWidth), sy(band.sourceTop),
                        mirror(nodeStart(flow.target)), sy(band.targetTop),
                        band.thickness * viewport.scale, color.copy(alpha = alpha)
                    )
                }
                layout.nodes.forEachIndexed { index, box ->
                    val alpha = if (highlighted == null || index in highlighted.nodes) 1f else 0.3f
                    drawRoundRect(
                        color = (if (nodes[index].color == ChartColor.Neutral) neutral else colors[index]).copy(alpha = alpha),
                        topLeft = Offset(minOf(mirror(nodeStart(index)), mirror(nodeStart(index) + nodeWidth)), sy(box.top)),
                        size = Size(nodeWidth, (box.height * viewport.scale).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius(NodeRadius.toPx())
                    )
                }
                val padding = LabelPadding.toPx()
                layout.nodes.forEachIndexed { index, box ->
                    // The label is centered on the part of the node that is shown
                    val top = sy(box.top).coerceAtLeast(0f)
                    val bottom = sy(box.bottom).coerceAtMost(size.height)
                    val shownHeight = bottom - top
                    if (shownHeight <= 0) return@forEachIndexed
                    val space = ((layout.labelSpace(index, 0f) + box.width) * viewport.scale - nodeWidth - 2 * padding).roundToInt()
                    if (space <= 0) return@forEachIndexed
                    val node = nodes[index]
                    val name = measureLine(measurer, node.label, nameStyle, space)
                    if (shownHeight < name.size.height * 0.8f) return@forEachIndexed
                    val value = measureLine(measurer, node.valueLabel, valueStyle, space)
                        .takeIf { shownHeight >= name.size.height + it.size.height }
                    val textHeight = name.size.height + (value?.size?.height ?: 0)
                    var y = top + (shownHeight - textHeight) / 2
                    val labelStart = nodeStart(index) + nodeWidth + padding
                    fun drawLine(line: TextLayoutResult) {
                        val x = if (isRtl) mirror(labelStart) - line.size.width else labelStart
                        drawText(line, topLeft = Offset(x, y))
                        y += line.size.height
                    }
                    drawLine(name)
                    value?.let(::drawLine)
                }
            }
            // For screen readers: each node where it is drawn
            layout.nodes.forEachIndexed { index, box ->
                val start = nodeStart(index)
                val top = viewport.offsetY + box.top * viewport.scale
                with(density) {
                    Box(
                        Modifier
                            .offset { IntOffset((if (isRtl) width - start - nodeWidth else start).roundToInt(), top.roundToInt()) }
                            .size(nodeWidth.toDp(), (box.height * viewport.scale).coerceAtLeast(1f).toDp())
                            .semantics {
                                contentDescription = nodes[index].description
                                this.selected = selected == SankeyHit.Node(index)
                                onClick { currentOnSelect(SankeyHit.Node(index)); true }
                            }
                    )
                }
            }
        }
        // For those who cannot pinch: zooming around the middle in steps
        fun zoom(factor: Float) {
            viewport = viewport.transformed(factor, viewSize.width / 2, viewSize.height / 2, 0f, 0f, viewSize.width, viewSize.height)
        }
        Row(
            Modifier
                .align(Alignment.End)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZoomButton("−", stringResource(R.string.chart_zoom_out), enabled = viewport.isZoomed) { zoom(1 / ZoomStep) }
            ZoomButton("+", stringResource(R.string.chart_zoom_in), enabled = viewport.scale < Viewport.MAX_SCALE) { zoom(ZoomStep) }
        }
    }
}

@Composable
private fun ZoomButton(sign: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label }
    ) {
        Text(sign, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** What to highlight for a selection: the selected flow with its nodes, or a node with its flows */
private class Highlight(val nodes: Set<Int>, val flows: Set<Int>)

private fun highlightOf(selected: SankeyHit?, flows: List<SankeyFlow>): Highlight? = when (selected) {
    null -> null
    is SankeyHit.Link -> flows.getOrNull(selected.index)?.let { Highlight(setOf(it.source, it.target), setOf(selected.index)) }
    is SankeyHit.Node -> {
        val connected = flows.indices.filter { flows[it].source == selected.index || flows[it].target == selected.index }
        Highlight(connected.flatMap { listOf(flows[it].source, flows[it].target) }.toSet() + selected.index, connected.toSet())
    }
}

private fun measureLine(measurer: androidx.compose.ui.text.TextMeasurer, text: String, style: TextStyle, width: Int) =
    measurer.measure(
        text, style, overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false,
        constraints = Constraints(maxWidth = width)
    )

/** A band whose edges leave and enter horizontally, between x positions on screen in any order */
private fun DrawScope.drawBand(sourceX: Float, sourceTop: Float, targetX: Float, targetTop: Float, thickness: Float, color: Color) {
    val middle = (sourceX + targetX) / 2
    val path = Path().apply {
        moveTo(sourceX, sourceTop)
        cubicTo(middle, sourceTop, middle, targetTop, targetX, targetTop)
        lineTo(targetX, targetTop + thickness)
        cubicTo(middle, targetTop + thickness, middle, sourceTop + thickness, sourceX, sourceTop + thickness)
        close()
    }
    drawPath(path, color)
}

/** How much a tap on a zoom button zooms in or out */
private const val ZoomStep = 2f

/** Thin bars, so that the bands carry the picture */
private val NodeWidth = 8.dp
private val NodeGap = 8.dp
private val NodeRadius = 2.dp
private val LabelPadding = 4.dp

/** At most this much room after the last column for its labels */
private val LastLabels = 112.dp

/** Taps this close to a thin band or node still hit it */
private val TapSlop = 12.dp

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SankeyChartPreview() {
    MaterialTheme {
        var selected by remember { mutableStateOf<SankeyHit?>(null) }
        SankeyChart(
            SankeyExample.nodes, SankeyExample.flows, Modifier.padding(16.dp).fillMaxWidth().height(360.dp),
            selected = selected, onSelect = { selected = it }
        )
    }
}

/** An example for the preview */
private object SankeyExample {
    val nodes = listOf(
        SankeyNode("Salary", "3,000 €", 0, ChartColor.Series(ChartPalette.GREEN)),
        SankeyNode("Refunds", "200 €", 0, ChartColor.Series(ChartPalette.GREEN)),
        SankeyNode("Me", "3,200 €", 1, ChartColor.Neutral),
        SankeyNode("Housing", "1,200 €", 2, ChartColor.Series(0)),
        SankeyNode("Groceries", "600 €", 2, ChartColor.Series(1)),
        SankeyNode("Eating out", "250 €", 2, ChartColor.Series(2)),
        SankeyNode("Left", "1,150 €", 2, ChartColor.Neutral),
        SankeyNode("Landlord", "1,200 €", 3, ChartColor.Series(0)),
        SankeyNode("REWE", "400 €", 3, ChartColor.Series(1)),
        SankeyNode("Aldi", "200 €", 3, ChartColor.Series(1)),
        SankeyNode("McDonald's", "186 €", 3, ChartColor.Series(2)),
        SankeyNode("Others", "64 €", 3, ChartColor.Series(2)),
    )

    val flows = listOf(
        SankeyFlow(0, 2, 3000f), SankeyFlow(1, 2, 200f),
        SankeyFlow(2, 3, 1200f), SankeyFlow(2, 4, 600f), SankeyFlow(2, 5, 250f), SankeyFlow(2, 6, 1150f),
        SankeyFlow(3, 7, 1200f), SankeyFlow(4, 8, 400f), SankeyFlow(4, 9, 200f), SankeyFlow(5, 10, 186f), SankeyFlow(5, 11, 64f),
    )
}
