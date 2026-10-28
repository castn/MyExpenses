package org.totschnig.myexpenses.next.data.categories

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.totschnig.myexpenses.next.core.common.longs
import org.totschnig.myexpenses.next.core.common.parseJsonArray
import org.totschnig.myexpenses.next.core.common.strings
import org.totschnig.myexpenses.next.core.common.toJsonArray

/*
 * How the decisions about categories are stored as JSON in the data store, see [CategoryStore].
 * What cannot be read is left out.
 */

internal fun parseRules(json: String): List<CategoryRule> = parseJsonArray(json) { rule ->
    CategoryRule(
        id = rule.getString("id"),
        payeeIds = rule.getJSONArray("payees").longs(),
        categoryId = rule.getLong("category"),
        merchants = rule.optJSONArray("merchants")?.strings() ?: emptySet()
    )
}

internal fun serializeRules(rules: List<CategoryRule>) = rules.toJsonArray { rule ->
    JSONObject().apply {
        put("id", rule.id)
        put("payees", JSONArray(rule.payeeIds.toList()))
        put("category", rule.categoryId)
        if (rule.merchants.isNotEmpty()) put("merchants", JSONArray(rule.merchants.toList()))
    }
}

internal fun parseIdMap(json: String): Map<Long, Long> = try {
    JSONObject(json).let { obj ->
        obj.keys().asSequence().mapNotNull { key -> key.toLongOrNull()?.let { it to obj.getLong(key) } }.toMap()
    }
} catch (_: JSONException) {
    emptyMap()
}

internal fun serializeIdMap(map: Map<Long, Long>) =
    JSONObject().apply { map.forEach { (id, categoryId) -> put(id.toString(), categoryId) } }.toString()
