package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.feature.ExpectedItem
import org.totschnig.myexpenses.feature.ExpectedReceipt
import org.totschnig.myexpenses.feature.ReceiptAnonymizer
import org.totschnig.myexpenses.feature.recognizedTextFromJson

/**
 * The fixture collector anonymizes receipts before they become fixtures. This makes sure that the
 * parser reads every fixture the same before and after, apart from the merchant cut to the chain.
 */
class ReceiptAnonymizerParserTest {

    private val dir get() = ReceiptFixtures.dir()

    @Test
    fun parserReadsAnonymizedFixturesTheSame() {
        val differences = mutableListOf<String>()
        dir.listFiles { file -> file.name.endsWith(".json") && !file.name.endsWith(".expected.json") }
            .orEmpty().sortedBy { it.name }.forEach { file ->
                val name = file.name.removeSuffix(".json")
                val text = recognizedTextFromJson(file.readText())
                val before = ReceiptParser.parse(text)
                val expected = ExpectedReceipt(
                    merchant = before.merchant,
                    items = before.items.map { ExpectedItem(it.name, it.amount) },
                    total = before.total,
                    itemCount = before.itemCount
                )
                val result = ReceiptAnonymizer.anonymize(text, expected)
                val after = ReceiptParser.parse(result.text)
                fun check(what: String, actual: Any?, wanted: Any?) {
                    if (actual != wanted) differences += "$name, $what: expected <$wanted> but was <$actual>"
                }
                check("merchant", after.merchant, result.merchant)
                check("items", after.items.map { it.copy(box = null) }, before.items.map { it.copy(box = null) })
                check("total", after.total, before.total)
                check("item count", after.itemCount, before.itemCount)
            }
        assertTrue(differences.joinToString("\n"), differences.isEmpty())
    }
}
