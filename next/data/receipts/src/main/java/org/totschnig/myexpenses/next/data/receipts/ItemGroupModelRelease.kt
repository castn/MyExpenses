package org.totschnig.myexpenses.next.data.receipts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.totschnig.itemgroupmodel.ProductGroupModel
import java.security.MessageDigest
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup

/**
 * A model file of product-group-modell offered for download
 *
 * @param format of the file; only [ItemGroupModelRelease.FORMAT] loads
 * @param sha256 of the file, hex
 */
data class ItemGroupModelRelease(
    val language: String,
    val version: String,
    val format: Int,
    val url: String,
    val sha256: String,
    val size: Long,
) {
    companion object {
        /**
         * Lists the models, published by product-group-modell on its GitHub pages, the files are
         * attached to its releases:
         * `{"schema": 1, "models": [{"format": 2, "market": "de-DE", "version": "de-DE-2026.10.1",
         * "url": "…", "size": 1841016, "sha256": "…"}]}`
         */
        const val MANIFEST_URL = "https://castn.github.io/product-group-modell/models.json"

        /** Format the copied [ProductGroupModel] reads */
        const val FORMAT = 2

        /** Larger files are not read, neither downloaded nor picked */
        const val MAX_SIZE = 32L * 1024 * 1024

        /** Entries with missing fields are left out */
        fun parseManifest(json: String): List<ItemGroupModelRelease> =
            Json.parseToJsonElement(json).jsonObject["models"]?.jsonArray.orEmpty().mapNotNull { element ->
                runCatching {
                    val model = element.jsonObject
                    fun text(key: String) = model.getValue(key).jsonPrimitive.content
                    ItemGroupModelRelease(
                        language = text("market").substringBefore('-'),
                        version = text("version"),
                        format = model.getValue("format").jsonPrimitive.int,
                        url = text("url"),
                        sha256 = text("sha256").lowercase(),
                        size = model.getValue("size").jsonPrimitive.long
                    )
                }.getOrNull()
            }

        /**
         * The model for [language] this app can read, else the German one, as receipts so far
         * are German; the last entry wins if there are several
         */
        fun choose(releases: List<ItemGroupModelRelease>, language: String): ItemGroupModelRelease? {
            val readable = releases.filter { it.format == FORMAT && it.size <= MAX_SIZE }
            return readable.lastOrNull { it.language == language } ?: readable.lastOrNull { it.language == "de" }
        }
    }
}

/** Why a file cannot be used as model */
class InvalidItemGroupModel(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason {
        /** Not a model, damaged, or of another format */
        Invalid,

        /** Predicts groups this version of the app does not know */
        Incompatible
    }
}

/** @throws InvalidItemGroupModel */
fun checkItemGroupModel(bytes: ByteArray): ProductGroupModel {
    val model = try {
        ProductGroupModel.load(bytes)
    } catch (e: Exception) {
        // Wrong magic, format or checksum, or a file cut short
        throw InvalidItemGroupModel(InvalidItemGroupModel.Reason.Invalid, e.message ?: "Invalid model", e)
    }
    val unknown = model.groups.filter { ItemGroup.ofId(it) == null }.distinct()
    if (unknown.isNotEmpty()) {
        throw InvalidItemGroupModel(InvalidItemGroupModel.Reason.Incompatible, "Unknown groups $unknown")
    }
    return model
}

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
