package org.totschnig.fixturecollector

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.ExpectedReceipt
import org.totschnig.myexpenses.feature.ReceiptAnonymizer
import org.totschnig.myexpenses.feature.RecognizedText
import org.totschnig.myexpenses.feature.expectedReceiptFromJson
import org.totschnig.myexpenses.feature.itemGroupRows
import org.totschnig.myexpenses.feature.recognizedTextFromJson
import org.totschnig.myexpenses.feature.toJson

/**
 * Fixtures as flat files in [dir], named as in next/data/receipts/src/test/resources/receipts:
 * `<name>.json` (recognized text), `<name>.jpg`, `<name>.expected.json` and
 * `<name>.item-groups.csv` (rows to append to item-groups.csv). The files on the device keep
 * the original, so that the photo can be recognized again; only the export is anonymized.
 * `<name>.marked.json` holds the boxes of lines the user marked to hide, it stays on the device.
 */
class FixtureStore(val dir: File) {

    data class Summary(val name: String, val items: Int, val total: Long?, val merchant: String?)

    data class Fixture(
        val name: String,
        val image: File,
        val text: RecognizedText,
        val expected: ExpectedReceipt,
        val marked: Set<Box> = emptySet()
    )

    init {
        dir.mkdirs()
    }

    fun image(name: String) = File(dir, "$name.jpg")
    private fun text(name: String) = File(dir, "$name.json")
    private fun expected(name: String) = File(dir, "$name.expected.json")
    private fun itemGroups(name: String) = File(dir, "$name.item-groups.csv")
    private fun marked(name: String) = File(dir, "$name.marked.json")
    private fun files(name: String) =
        listOf(image(name), text(name), expected(name), itemGroups(name), marked(name))

    fun names() = dir.listFiles().orEmpty()
        .map { it.name }
        .filter {
            it.endsWith(".json") && !it.endsWith(".expected.json") &&
                !it.endsWith(".marked.json")
        }
        .map { it.removeSuffix(".json") }
        .sorted()

    fun summaries() = names().map { name ->
        val expected = loadExpected(name)
        Summary(name, expected.items.size, expected.total, expected.merchant)
    }

    fun exists(name: String) = text(name).exists()

    fun load(name: String) = Fixture(
        name,
        image(name),
        recognizedTextFromJson(text(name).readText()),
        loadExpected(name),
        marked(name).takeIf {
            it.exists()
        }?.let { Json.decodeFromString(boxes, it.readText()).toSet() }.orEmpty()
    )

    private fun loadExpected(name: String) =
        expected(name).takeIf { it.exists() }?.let { expectedReceiptFromJson(it.readText()) }
            ?: ExpectedReceipt()

    /**
     * @param previousName the name the fixture was stored under, its files are moved to [name]
     * @param photo copied byte by byte to `<name>.jpg`, as the boxes refer to exactly this image
     */
    fun save(
        name: String,
        text: RecognizedText,
        expected: ExpectedReceipt,
        photo: File,
        previousName: String? = null,
        marked: Set<Box> = emptySet()
    ) {
        require(isValidName(name)) { "Invalid name $name" }
        val target = image(name)
        if (photo.canonicalPath != target.canonicalPath) {
            photo.copyTo(target, overwrite = true)
        }
        text(name).writeText(text.toJson())
        expected(name).writeText(expected.toJson())
        val rows = expected.itemGroupRows(name)
        if (rows.isEmpty()) {
            itemGroups(
                name
            ).delete()
        } else {
            itemGroups(name).writeText(rows.joinToString("\n", postfix = "\n"))
        }
        if (marked.isEmpty()) {
            marked(
                name
            ).delete()
        } else {
            marked(name).writeText(Json.encodeToString(boxes, marked.toList()))
        }
        if (previousName != null && previousName != name) delete(previousName)
    }

    fun delete(name: String) {
        files(name).forEach { it.delete() }
    }

    /**
     * All fixtures in one ZIP, flat as in the test resources, anonymized with [ReceiptAnonymizer]:
     * the recognized text without personal data, the merchant cut to the chain, and the photo with
     * [blacken] applied to the areas whose text was changed.
     *
     * @param blacken returns the JPEG of the photo with the boxes painted over
     */
    fun zipTo(target: File, blacken: (photo: File, boxes: List<Box>) -> ByteArray) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            names().forEach { name ->
                val fixture = load(name)
                val anonymized = ReceiptAnonymizer.anonymize(
                    fixture.text,
                    fixture.expected,
                    fixture.marked
                )
                val expected = fixture.expected.copy(merchant = anonymized.merchant)
                entry("$name.json", anonymized.text.toJson().toByteArray())
                entry("$name.expected.json", expected.toJson().toByteArray())
                expected.itemGroupRows(name).takeIf { it.isNotEmpty() }?.let { rows ->
                    entry(
                        "$name.item-groups.csv",
                        rows.joinToString("\n", postfix = "\n").toByteArray()
                    )
                }
                if (fixture.image.exists()) {
                    entry(
                        "$name.jpg",
                        blacken(fixture.image, anonymized.redactions)
                    )
                }
            }
        }
    }

    companion object {
        private val boxes = ListSerializer(Box.serializer())

        /** Like the existing fixtures: "aldi", "edeka-hummus" */
        private val validName = Regex("""[a-z0-9]+(-[a-z0-9]+)*""")

        fun isValidName(name: String) = validName.matches(name) && name != "item-groups"
    }
}
