package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.feature.expectedReceiptFromJson
import org.totschnig.myexpenses.feature.recognizedTextFromJson
import java.io.File

/**
 * Checks every receipt that comes with `<name>.expected.json`, as written by the fixture collector,
 * against its recognized text `<name>.json`. Unit, unit price and tax class are only compared
 * where the expectation states them. The merchant is compared by its letters only.
 * All differences of all fixtures are reported at once.
 */
class ReceiptFixturesTest {

    private val dir get() = ReceiptFixtures.dir()

    @Test
    fun expectedFixtures() {
        val differences = mutableListOf<String>()
        fun check(name: String, what: String, actual: Any?, expected: Any?) {
            if (actual != expected) differences += "$name, $what: expected <$expected> but was <$actual>"
        }
        dir.listFiles { file -> file.name.endsWith(".expected.json") }.orEmpty().sortedBy { it.name }.forEach { file ->
            val name = file.name.removeSuffix(".expected.json")
            val expected = expectedReceiptFromJson(file.readText())
            val receipt = ReceiptParser.parse(recognizedTextFromJson(File(dir, "$name.json").readText()))

            // Recognition splits words ("RE WE") and adds marks ("Backshoo -")
            check(name, "merchant", receipt.merchant?.let(::letters), expected.merchant?.let(::letters))
            check(name, "items", receipt.items.map { it.name to it.amount }, expected.items.map { it.name to it.amount })
            receipt.items.zip(expected.items).forEach { (item, wanted) ->
                check(name, "quantity of ${wanted.name}", item.quantity.stripTrailingZeros(), wanted.quantity.toBigDecimal().stripTrailingZeros())
                wanted.unit?.let { check(name, "unit of ${wanted.name}", item.unit, it) }
                wanted.unitPrice?.let { check(name, "unit price of ${wanted.name}", item.unitPrice, it) }
                wanted.taxClass?.let { check(name, "tax class of ${wanted.name}", item.taxClass, it) }
            }
            check(name, "total", receipt.total, expected.total)
            check(name, "item count", receipt.itemCount, expected.itemCount)
            (expected.transactionAmount ?: expected.total)?.let {
                check(name, "consistent", ReceiptCheck(receipt, it).isConsistent, true)
            }
        }
        assertTrue(differences.joinToString("\n"), differences.isEmpty())
    }

    private fun letters(text: String) = text.filter { it.isLetter() }.uppercase()
}
