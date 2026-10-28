package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.feature.recognizedTextFromJson
import java.io.File

/**
 * Everything the parser reads from every fixture, positions included, compared with the stored
 * snapshot, so that any change of its behaviour shows. After an intended change, write a new
 * snapshot with `./gradlew :next:data:receipts:testDebugUnitTest -PupdateReceiptSnapshot` and review its diff.
 */
class ReceiptParserSnapshotTest {

    private val dir get() = ReceiptFixtures.dir()
    private val snapshot get() = File(dir, "parser-snapshot.txt")

    @Test
    fun parserOutputIsUnchanged() {
        val actual = dir.listFiles { file -> file.name.endsWith(".json") && !file.name.endsWith(".expected.json") }
            .orEmpty().sortedBy { it.name }.joinToString("") { file -> describe(file) }
        if (System.getProperty("updateReceiptSnapshot") != null || !snapshot.exists()) {
            snapshot.writeText(actual)
        }
        assertEquals(snapshot.readText(), actual)
    }

    private fun describe(file: File): String {
        val receipt = ReceiptParser.parse(recognizedTextFromJson(file.readText()))
        return buildString {
            appendLine("== ${file.name.removeSuffix(".json")}")
            appendLine("merchant: ${receipt.merchant}, total: ${receipt.total}, item count: ${receipt.itemCount}")
            appendLine("date: ${receipt.date}, card: ${receipt.cardAmount}, cash: ${receipt.cash}")
            receipt.leftoverRows.forEach { appendLine("leftover: ${it.text}") }
            receipt.items.forEach { item ->
                appendLine(
                    listOf(
                        item.name,
                        item.amount,
                        item.quantity.toPlainString() + (item.unit?.let { " $it" } ?: ""),
                        item.unitPrice,
                        item.taxClass,
                        item.box?.let { "${it.left},${it.top},${it.right},${it.bottom}" }
                    ).joinToString(" | ")
                )
            }
        }
    }
}
