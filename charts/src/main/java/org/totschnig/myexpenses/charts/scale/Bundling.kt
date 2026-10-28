package org.totschnig.myexpenses.charts.scale

/** At most this many bars or segments in one chart, the last one sums up the rest */
const val MAX_ENTRIES = 6

/**
 * The entries of a chart that compares parts, largest first. If there are more than [limit], the
 * largest `limit - 1` are kept and [rest] sums up the others into one last entry, e.g. "Others".
 * So a chart never shows more than [limit] entries, however many the data has.
 *
 * @param value the size of an entry
 * @param rest creates the entry for the given smaller entries
 */
fun <T> largestWithRest(
    entries: List<T>,
    value: (T) -> Double,
    limit: Int = MAX_ENTRIES,
    rest: (List<T>) -> T,
): List<T> {
    require(limit >= 2) { "limit must leave room for one entry and the rest" }
    val sorted = entries.sortedByDescending(value)
    if (sorted.size <= limit) return sorted
    return sorted.take(limit - 1) + rest(sorted.drop(limit - 1))
}
