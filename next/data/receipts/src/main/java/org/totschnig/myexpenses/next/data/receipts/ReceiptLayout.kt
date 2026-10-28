package org.totschnig.myexpenses.next.data.receipts

import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.RecognizedLine

/**
 * Rebuilds the rows of a receipt from the positions of the recognized lines, independent of the
 * language: recognition often returns the name column and the price column as separate blocks,
 * and photos of receipts are tilted and the paper is bent.
 *
 * A row starts with an anchor, a segment on the left; segments further right are attached to the
 * row they overlap most, prices column by column in the order of the rows.
 */
internal object ReceiptLayout {

    /** Segments starting left of this share of the width start a row */
    private const val ANCHOR_SHARE = 0.45
    private const val MIN_OVERLAP = 0.25
    /** Minimal distance between first and last word to measure the tilt of a row */
    private const val MIN_SLOPE_DISTANCE = 300
    private const val SLOPE_NEIGHBOURS = 2
    private const val MAX_SLOPE = 0.15
    private const val SAME_LINE_OVERLAP = 0.6
    private const val SAME_LINE_LOOKBACK = 3

    /**
     * [reference] is the first word of the anchor, [slope] the tilt of the row on the photo,
     * so that segments far to the right are compared with the height of the row at their position.
     */
    class Row(val anchor: RecognizedLine, val reference: Box, val slope: Double) {
        val parts = mutableListOf(anchor)
        val text: String
            get() = parts.sortedBy { it.box!!.left }.joinToString(" ") { it.text.trim() }
        val box: Box
            get() = parts.map { it.box!! }.reduce(::union)

        fun overlapWith(segment: Box): Double {
            val shift = (slope * (segment.left - reference.left)).toInt()
            return overlap(segment, reference.copy(top = reference.top + shift, bottom = reference.bottom + shift))
        }
    }


    /** Tilt from the first to the last word, null if the words are too close for a reliable value */
    private fun slopeOf(line: RecognizedLine): Double? {
        val words = line.elements.mapNotNull { it.box }
        if (words.size < 2) return null
        val first = words.minBy { it.left }
        val last = words.maxBy { it.left }
        val distance = last.left - first.left
        if (distance < MIN_SLOPE_DISTANCE) return null
        return ((last.top + last.bottom) - (first.top + first.bottom)) / 2.0 / distance
    }

    /**
     * Rows of the receipt from the [lines] of recognition, top to bottom
     *
     * @param isPrice whether a segment is a price, prices are aligned column by column
     * @param isLoneSign whether a segment is only the sign between quantity and price, which does
     * not start a row
     */
    fun rows(lines: List<RecognizedLine>, isPrice: (String) -> Boolean, isLoneSign: (String) -> Boolean): List<Row> {
        if (lines.isEmpty()) return emptyList()
        val left = lines.minOf { it.box!!.left }
        val right = lines.maxOf { it.box!!.right }
        val anchorLimit = left + (right - left) * ANCHOR_SHARE
        // A lone "x" between quantity and price does not start a row
        val (anchors, others) = lines.sortedBy { it.box!!.top }
            .partition { it.box!!.left < anchorLimit && !isLoneSign(it.text) }
        val lineGroups = sameLines(anchors)
        // Paper is often bent, so the tilt is taken from the neighbouring rows
        val slopes = lineGroups.map { group -> group.firstNotNullOfOrNull(::slopeOf) }
        val overall = slopes.filterNotNull().median() ?: 0.0
        val rows = lineGroups.mapIndexed { i, group ->
            val nearby = slopes.subList(maxOf(0, i - SLOPE_NEIGHBOURS), minOf(slopes.size, i + SLOPE_NEIGHBOURS + 1))
            val slope = (nearby.filterNotNull().median() ?: overall).coerceIn(-MAX_SLOPE, MAX_SLOPE)
            val anchor = group.first()
            Row(anchor, anchor.elements.firstOrNull()?.box ?: anchor.box!!, slope).apply { parts += group.drop(1) }
        }
        val unattached = others.toMutableList()
        val (prices, rest) = others.partition { isPrice(it.text) }
        // Prices keep the order of the rows, so each column is aligned as a whole
        for (column in columns(prices)) {
            for ((row, segment) in alignment(rows, column)) {
                row.parts += segment
                unattached -= segment
            }
        }
        // Attach the best overlaps first
        rest.flatMap { segment -> rows.map { row -> Triple(segment, row, row.overlapWith(segment.box!!)) } }
            .filter { it.third >= MIN_OVERLAP }
            .sortedByDescending { it.third }
            .forEach { (segment, row, _) ->
                if (segment in unattached) {
                    row.parts += segment
                    unattached -= segment
                }
            }
        return (rows + unattached.map { Row(it, it.box!!, 0.0) }).sortedBy { it.box.top + it.box.bottom }
    }

    /** Anchors side by side on the same line ("Total" and "5 Pos"), the leftmost first */
    private fun sameLines(anchors: List<RecognizedLine>): List<List<RecognizedLine>> {
        val groups = mutableListOf<MutableList<RecognizedLine>>()
        for (anchor in anchors) {
            val box = anchor.box!!
            groups.takeLast(SAME_LINE_LOOKBACK).firstOrNull { group ->
                group.all { other ->
                    overlap(box, other.box!!) >= SAME_LINE_OVERLAP &&
                            (box.left >= other.box!!.right || other.box!!.left >= box.right)
                }
            }?.add(anchor) ?: groups.add(mutableListOf(anchor))
        }
        return groups.map { group -> group.sortedBy { it.box!!.left } }
    }

    /** Price segments grouped by horizontal overlap, e.g. unit price and total price */
    private fun columns(prices: List<RecognizedLine>): List<List<RecognizedLine>> {
        val columns = mutableListOf<Pair<IntRange, MutableList<RecognizedLine>>>()
        for (price in prices.sortedBy { it.box!!.left }) {
            val box = price.box!!
            val index = columns.indexOfFirst { (range, _) -> box.left <= range.last && range.first <= box.right }
            if (index < 0) {
                columns += (box.left..box.right) to mutableListOf(price)
            } else {
                val (range, members) = columns[index]
                members += price
                columns[index] = minOf(range.first, box.left)..maxOf(range.last, box.right) to members
            }
        }
        return columns.map { it.second }
    }

    /** Order preserving assignment of [segments] to [rows] with the largest overlap in total */
    private fun alignment(rows: List<Row>, segments: List<RecognizedLine>): List<Pair<Row, RecognizedLine>> {
        val sorted = segments.sortedBy { it.box!!.top + it.box!!.bottom }
        val score = Array(rows.size) { i -> DoubleArray(sorted.size) { j -> rows[i].overlapWith(sorted[j].box!!) } }
        val best = Array(rows.size + 1) { DoubleArray(sorted.size + 1) }
        for (i in 1..rows.size) for (j in 1..sorted.size) {
            val matched = score[i - 1][j - 1].takeIf { it >= MIN_OVERLAP }?.let { best[i - 1][j - 1] + it }
            best[i][j] = maxOf(best[i - 1][j], best[i][j - 1], matched ?: Double.NEGATIVE_INFINITY)
        }
        val pairs = mutableListOf<Pair<Row, RecognizedLine>>()
        var i = rows.size
        var j = sorted.size
        while (i > 0 && j > 0) {
            val overlap = score[i - 1][j - 1]
            when {
                overlap >= MIN_OVERLAP && best[i][j] == best[i - 1][j - 1] + overlap -> {
                    pairs += rows[i - 1] to sorted[j - 1]
                    i--
                    j--
                }
                best[i][j] == best[i - 1][j] -> i--
                else -> j--
            }
        }
        return pairs
    }

    private fun List<Double>.median() = if (isEmpty()) null else sorted()[size / 2]

    /** Vertical overlap relative to the lower of both heights */
    private fun overlap(a: Box, b: Box): Double {
        val shared = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val height = minOf(a.bottom - a.top, b.bottom - b.top)
        return if (shared <= 0 || height <= 0) 0.0 else shared.toDouble() / height
    }
}

internal fun union(a: Box, b: Box) =
    Box(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
