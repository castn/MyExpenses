package org.totschnig.myexpenses.charts.layout

import kotlin.math.abs

/**
 * A flow of [value] from node [source] to node [target], by their index; the source must be in a
 * column before the target
 */
data class FlowLink(val source: Int, val target: Int, val value: Double)

/** A node of a [SankeyLayout]: a bar from [top] to [bottom] at [x], [width] wide */
data class NodeBox(val x: Float, val top: Float, val bottom: Float, val width: Float) {
    val height: Float get() = bottom - top
}

/**
 * A link of a [SankeyLayout]: a band [thickness] high from the right edge of its source at
 * [sourceX], starting at [sourceTop], to the left edge of its target at [targetX], ending at
 * [targetTop]. Its edges are cubic curves with both control points halfway, so the band leaves
 * and enters the nodes horizontally.
 */
data class LinkBand(
    val sourceX: Float,
    val sourceTop: Float,
    val targetX: Float,
    val targetTop: Float,
    val thickness: Float,
) {
    /** How far along the band [x] is, from 0 at the source to 1 at the target */
    private fun parameterAt(x: Float): Float {
        // x(t) of the curve grows with t, so halving finds it
        var low = 0f
        var high = 1f
        repeat(24) {
            val t = (low + high) / 2
            if (xAt(t) < x) low = t else high = t
        }
        return (low + high) / 2
    }

    private fun xAt(t: Float): Float {
        val middle = (sourceX + targetX) / 2
        val u = 1 - t
        return u * u * u * sourceX + 3 * u * t * (u + t) * middle + t * t * t * targetX
    }

    /** The upper edge of the band at [x], between [sourceX] and [targetX] */
    fun topAt(x: Float): Float {
        val t = parameterAt(x)
        return sourceTop + (targetTop - sourceTop) * t * t * (3 - 2 * t)
    }
}

/**
 * Where the nodes and links of a Sankey diagram go, from left to right by column. Each node is as
 * high as the larger of what flows in and out of it, with the same scale for all columns, so that
 * the height of a node and the thickness of a band show the value. The nodes of a column keep
 * their order from top to bottom and are centered vertically. At a node, the bands are ordered
 * like the nodes at their other end, so they do not cross.
 *
 * @param columns the column of each node, starting at 0
 * @param width of the diagram; the last column ends [trailing] before it, room for its labels
 * @param nodeGap between the nodes of a column, smaller if a column has too many nodes for it
 */
class SankeyLayout(
    columns: List<Int>,
    val links: List<FlowLink>,
    val width: Float,
    val height: Float,
    nodeWidth: Float,
    nodeGap: Float,
    trailing: Float = 0f,
) {
    val nodes: List<NodeBox>
    val bands: List<LinkBand>

    /** Index of the last column */
    val lastColumn: Int = columns.maxOrNull() ?: 0

    private val columnOf = columns

    init {
        links.forEach {
            require(columns[it.source] < columns[it.target]) { "A link must go to a later column: $it" }
            require(it.value >= 0) { "A link cannot be negative: $it" }
        }
        val values = columns.indices.map { node ->
            maxOf(links.filter { it.source == node }.sumOf { it.value }, links.filter { it.target == node }.sumOf { it.value })
        }
        val byColumn = columns.indices.groupBy { columns[it] }
        val gaps = byColumn.mapValues { (_, nodes) ->
            if (nodes.size < 2) 0f else minOf(nodeGap, height / 2 / (nodes.size - 1))
        }
        val scale = byColumn.minOfOrNull { (column, nodes) ->
            val sum = nodes.sumOf { values[it] }
            if (sum <= 0) Double.MAX_VALUE else (height - gaps.getValue(column) * (nodes.size - 1)) / sum
        }?.takeIf { it != Double.MAX_VALUE } ?: 0.0
        val step = if (lastColumn == 0) 0f else (width - trailing - nodeWidth) / lastColumn
        val boxes = arrayOfNulls<NodeBox>(columns.size)
        byColumn.forEach { (column, nodes) ->
            val gap = gaps.getValue(column)
            val used = nodes.sumOf { values[it] * scale }.toFloat() + gap * (nodes.size - 1)
            var top = (height - used) / 2
            nodes.forEach { node ->
                val bottom = top + (values[node] * scale).toFloat()
                boxes[node] = NodeBox(column * step, top, bottom, nodeWidth)
                top = bottom + gap
            }
        }
        nodes = boxes.map { it!! }
        val sourceTops = FloatArray(links.size)
        val targetTops = FloatArray(links.size)
        columns.indices.forEach { node ->
            var top = nodes[node].top
            links.indices.filter { links[it].source == node }.sortedBy { nodes[links[it].target].top }.forEach {
                sourceTops[it] = top
                top += (links[it].value * scale).toFloat()
            }
            top = nodes[node].top
            links.indices.filter { links[it].target == node }.sortedBy { nodes[links[it].source].top }.forEach {
                targetTops[it] = top
                top += (links[it].value * scale).toFloat()
            }
        }
        bands = links.mapIndexed { index, link ->
            val source = nodes[link.source]
            LinkBand(
                sourceX = source.x + source.width,
                sourceTop = sourceTops[index],
                targetX = nodes[link.target].x,
                targetTop = targetTops[index],
                thickness = (link.value * scale).toFloat()
            )
        }
    }

    /**
     * How wide the label right of node [node] can be: up to the next column, or to the end of the
     * diagram for the last one, less [padding] on both sides
     */
    fun labelSpace(node: Int, padding: Float): Float {
        val box = nodes[node]
        val end = if (columnOf[node] == lastColumn) width
        else nodes[columnOf.indexOfFirst { it == columnOf[node] + 1 }].x
        return (end - box.x - box.width - 2 * padding).coerceAtLeast(0f)
    }

    /**
     * What is at [x], [y]: a node, else the thinnest band there, else nothing
     *
     * @param nodeWidth how wide nodes are drawn, e.g. narrower when zoomed
     */
    fun hit(x: Float, y: Float, slop: Float, nodeWidth: Float? = null): SankeyHit? {
        nodes.indices
            .filter { node -> nodes[node].let { x in it.x - slop..it.x + (nodeWidth ?: it.width) + slop && y in it.top - slop..it.bottom + slop } }
            .minByOrNull { node -> abs(y - (nodes[node].top + nodes[node].bottom) / 2) }
            ?.let { return SankeyHit.Node(it) }
        return bands.indices
            .filter { index ->
                val band = bands[index]
                if (x !in band.sourceX..band.targetX) return@filter false
                val top = band.topAt(x)
                val extra = ((slop * 2 - band.thickness) / 2).coerceAtLeast(0f)
                y in top - extra..top + band.thickness + extra
            }
            .minByOrNull { bands[it].thickness }
            ?.let { SankeyHit.Link(it) }
    }
}

/** What a tap on a [SankeyLayout] hit */
sealed interface SankeyHit {
    data class Node(val index: Int) : SankeyHit
    data class Link(val index: Int) : SankeyHit
}
