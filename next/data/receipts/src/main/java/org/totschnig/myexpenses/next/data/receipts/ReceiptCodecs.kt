package org.totschnig.myexpenses.next.data.receipts

import android.net.Uri
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.next.core.common.parseJsonArray
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup

/*
 * How a receipt being checked is kept as JSON in the saved state of the activity, so that it
 * survives when Android ends the process, e.g. while the document scanner is open. A draft that
 * cannot be read, e.g. of a later version, is lost like before.
 */

fun ReceiptDraft.toJson(): String = JSONObject().apply {
    put("target", target.toJson())
    put("transactionAmount", transactionAmount)
    put("receipt", receipt.toJson())
    put("newPhoto", isNewPhoto)
    itemCount?.let { put("itemCount", it) }
    put("changed", isChanged)
    put("standalone", isStandalone)
    put("leftoverRows", leftoverRows.toJson())
}.toString()

/** null if [json] cannot be read */
fun receiptDraftOf(json: String): ReceiptDraft? = readOrNull {
    with(JSONObject(json)) {
        ReceiptDraft.of(
            target = receiptTargetOf(getJSONObject("target")),
            transactionAmount = getLong("transactionAmount"),
            receipt = receiptOf(getJSONObject("receipt")),
            isNewPhoto = getBoolean("newPhoto"),
            itemCount = if (has("itemCount")) getInt("itemCount") else null,
            leftoverRows = leftoverRowsOf(getJSONArray("leftoverRows"))
        ).copy(
            isChanged = getBoolean("changed"),
            isStandalone = getBoolean("standalone")
        )
    }
}

fun Receipt.toJson(): JSONObject = JSONObject().apply {
    merchant?.let { put("merchant", it) }
    total?.let { put("total", it) }
    put("items", JSONArray(items.map { it.toJson() }))
    photo?.let { put("photo", it.toString()) }
    date?.let { put("date", it.toString()) }
    cardAmount?.let { put("cardAmount", it) }
}

fun receiptOf(json: JSONObject): Receipt = with(json) {
    Receipt(
        merchant = optStringOrNull("merchant"),
        total = if (has("total")) getLong("total") else null,
        items = parseJsonArray(getJSONArray("items").toString(), ::receiptItemOf),
        photo = optStringOrNull("photo")?.let(Uri::parse),
        date = optStringOrNull("date")?.let(LocalDate::parse),
        cardAmount = if (has("cardAmount")) getLong("cardAmount") else null
    )
}

fun List<LeftoverRow>.toJson(): JSONArray = JSONArray(map { row ->
    JSONObject().apply {
        put("text", row.text)
        put("box", row.box.toJson())
    }
})

fun leftoverRowsOf(json: JSONArray): List<LeftoverRow> = parseJsonArray(json.toString()) {
    LeftoverRow(it.getString("text"), boxOf(it.getJSONArray("box")))
}

private fun ReceiptTarget.toJson() = JSONObject().apply {
    when (this@toJson) {
        ReceiptTarget.Unassigned -> put("kind", "unassigned")
        is ReceiptTarget.Transaction -> {
            put("kind", "transaction")
            put("id", id)
        }
        is ReceiptTarget.Waiting -> {
            put("kind", "waiting")
            id?.let { put("id", it) }
        }
    }
}

private fun receiptTargetOf(json: JSONObject): ReceiptTarget = when (val kind = json.getString("kind")) {
    "unassigned" -> ReceiptTarget.Unassigned
    "transaction" -> ReceiptTarget.Transaction(json.getLong("id"))
    "waiting" -> ReceiptTarget.Waiting(if (json.has("id")) json.getLong("id") else null)
    else -> throw IllegalArgumentException("Unknown target $kind")
}

private fun ReceiptItem.toJson() = JSONObject().apply {
    put("name", name)
    put("amount", amount)
    put("quantity", quantity.toPlainString())
    unit?.let { put("unit", it) }
    unitPrice?.let { put("unitPrice", it) }
    taxClass?.let { put("taxClass", it) }
    box?.let { put("box", it.toJson()) }
    group?.let { put("group", it) }
    put("groupByUser", groupByUser)
}

private fun receiptItemOf(json: JSONObject) = with(json) {
    ReceiptItem(
        name = getString("name"),
        amount = getLong("amount"),
        quantity = BigDecimal(getString("quantity")),
        unit = optStringOrNull("unit"),
        unitPrice = if (has("unitPrice")) getLong("unitPrice") else null,
        taxClass = optStringOrNull("taxClass"),
        box = optJSONArray("box")?.let(::boxOf),
        group = optStringOrNull("group"),
        groupByUser = getBoolean("groupByUser")
    )
}

private fun Box.toJson() = JSONArray(listOf(left, top, right, bottom))

private fun boxOf(json: JSONArray) = Box(json.getInt(0), json.getInt(1), json.getInt(2), json.getInt(3))

private fun JSONObject.optStringOrNull(key: String) = if (has(key)) getString(key) else null

private fun <T> readOrNull(read: () -> T): T? = try {
    read()
} catch (_: JSONException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: DateTimeException) {
    null
}
