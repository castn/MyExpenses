package org.totschnig.myexpenses.feature

private const val MIN_ROW_OVERLAP = 0.5

/**
 * Lines with a box grouped into rows of the receipt: rows from top to bottom, lines side by side
 * from left to right. Simple vertical overlap without tilt, good enough to show and edit fixtures.
 */
fun RecognizedText.rows(): List<List<RecognizedLine>> {
    val rows = mutableListOf<MutableList<RecognizedLine>>()
    for (line in lines.filter { it.box != null && it.text.isNotBlank() }.sortedBy { it.box!!.top }) {
        rows.lastOrNull { row -> verticalOverlap(row.first().box!!, line.box!!) >= MIN_ROW_OVERLAP }
            ?.add(line) ?: rows.add(mutableListOf(line))
    }
    return rows.map { row -> row.sortedBy { it.box!!.left } }
}

fun List<RecognizedLine>.rowText() = joinToString(" ") { it.text.trim() }

/** Vertical overlap relative to the lower of both heights */
private fun verticalOverlap(a: Box, b: Box): Double {
    val common = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    val height = minOf(a.bottom - a.top, b.bottom - b.top)
    return if (height <= 0) 0.0 else common.toDouble() / height
}
