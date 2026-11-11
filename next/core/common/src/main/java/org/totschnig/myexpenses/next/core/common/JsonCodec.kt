package org.totschnig.myexpenses.next.core.common

import java.time.DateTimeException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reads what the new UI stores as JSON array in the data store. Elements that cannot be read, e.g.
 * of a type from a later version, are left out; JSON that cannot be read gives an empty list.
 * Writing with [toJsonArray] and the stored JSON as `previous` keeps them.
 */
fun <T> parseJsonArray(json: String, element: (JSONObject) -> T): List<T> =
    elementsOf(json).mapNotNull { readOrNull(it, element) }

/**
 * Writes [element] for each item, followed by the elements of [previous] that [read] cannot read,
 * as they were stored, so that a value from a later version or a broken one is not lost by writing.
 */
fun <T> List<T>.toJsonArray(
    previous: String?,
    read: (JSONObject) -> T,
    element: (T) -> JSONObject
): String {
    val unreadable = previous?.let(::elementsOf).orEmpty().filter { readOrNull(it, read) == null }
    return JSONArray(map(element) + unreadable).toString()
}

fun <T> List<T>.toJsonArray(element: (T) -> JSONObject): String = JSONArray(map(element)).toString()

/** The values of a JSON array of numbers */
fun JSONArray.longs(): Set<Long> = (0 until length()).mapTo(HashSet()) { getLong(it) }

fun JSONArray.strings(): Set<String> = (0 until length()).mapTo(HashSet()) { getString(it) }

private fun elementsOf(json: String): List<Any> = try {
    JSONArray(json).let { array -> (0 until array.length()).map { array.get(it) } }
} catch (_: JSONException) {
    emptyList()
}

private fun <T> readOrNull(value: Any, element: (JSONObject) -> T): T? = try {
    (value as? JSONObject)?.let(element)
} catch (_: JSONException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: DateTimeException) {
    null
}
