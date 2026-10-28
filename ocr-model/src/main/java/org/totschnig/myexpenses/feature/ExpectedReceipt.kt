package org.totschnig.myexpenses.feature

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a receipt fixture shows, written down by hand next to its [RecognizedText].
 * Amounts in minor units. Names as recognition reads them, typos included.
 */
@Serializable
data class ExpectedReceipt(
    val merchant: String? = null,
    val items: List<ExpectedItem> = emptyList(),
    val total: Long? = null,
    /** Number of articles printed on the receipt ("7 Artikel") */
    val itemCount: Int? = null,
    /** Amount of the transaction, if it differs from [total] */
    val transactionAmount: Long? = null,
    /** Version of ML Kit text recognition that produced the fixture */
    val mlkitVersion: String? = null,
)

@Serializable
data class ExpectedItem(
    val name: String,
    val amount: Long,
    /** Decimal as text, "1", "2" or "0.456" */
    val quantity: String = "1",
    /** null for pieces, otherwise e.g. "kg" */
    val unit: String? = null,
    val unitPrice: Long? = null,
    val taxClass: String? = null,
    /** Id of the item group, e.g. "dairy" */
    val group: String? = null,
)

private val expectedJson = Json {
    prettyPrint = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

fun ExpectedReceipt.toJson() = expectedJson.encodeToString(this)

fun expectedReceiptFromJson(text: String) = expectedJson.decodeFromString<ExpectedReceipt>(text)

/** Rows for item-groups.csv ("receipt;name;group"), items without group are left out */
fun ExpectedReceipt.itemGroupRows(fixture: String) =
    items.filter { it.group != null }.map { "$fixture;${it.name};${it.group}" }
