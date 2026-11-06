package org.totschnig.fixturecollector

import org.totschnig.myexpenses.feature.ExpectedItem
import org.totschnig.myexpenses.feature.ExpectedReceipt
import org.totschnig.myexpenses.feature.RecognizedText
import org.totschnig.myexpenses.feature.rowText
import org.totschnig.myexpenses.feature.rows

/**
 * A rough first fill of the expected values, so that the user only corrects them.
 * Deliberately simple and independent of ReceiptParser: the fixture shall test the parser,
 * not repeat what it reads.
 */
object Suggestion {

    // Recognition adds spaces to numbers ("0, 750"), doubles the sign ("x x"), misreads kg and Stück
    private const val AMOUNT = """-?\d{1,5}\s?[,.]\s?\d{2}"""
    private const val QUANTITY = """\d+(?:\s?[,.]\s?\d+)?"""
    private const val UNIT = """(?i:Stück|Stk|St|kg|k9|kq|[S5G]t[uúüù]ck)"""
    private const val TIMES = """[x×*](?:\s*[x×*])*"""
    private val trailingAmount =
        Regex("""^(.*?)\s*€?($AMOUNT-?)\s*(?:€|EUR)?\s*(?:([A-Z][A-Z#*]?)|\d)?\s*[*#]?$""")

    /** "2 x 0,89", "0,456 kg x 3,99 €/kg", "2 x €0,99" */
    private val quantityRow =
        Regex(
            """(?i)^($QUANTITY)\s*($UNIT)?\.?\s*$TIMES\s*€?($AMOUNT)\s*(?:€|EUR)?\s*(?:/\s*$UNIT)?(?:\s*[x×*])?$"""
        )

    /** Unit price and count after the name: "G&G HASELNUSS 2,99 € x2" */
    private val inlineUnitPriceTimesCount =
        Regex("""(?i)^(.*?)\s+€?($AMOUNT)\s*(?:€|EUR)?\s*$TIMES\s*(\d+)(?:\s*[x×*])?$""")

    /** Count and unit price after the name: "BAGUETTE 2 Stk x 1,19" */
    private val inlineCountTimesUnitPrice =
        Regex("""(?i)^(.*?)\s+(\d+)\s*$UNIT?\.?\s*$TIMES\s*€?($AMOUNT)\s*(?:€|EUR)?$""")
    private val itemCount = Regex("""(?i)(\d+)\s+artikel|posten\s*:?\s*(\d+)""")
    private val totalWords = listOf("SUMME", "ZUZAHLEN", "GESAMT", "TOTAL")
    private val skippedWords = listOf("ZWSUMME", "ZWISCHENSUMME", "SUBTOTAL")

    fun of(text: RecognizedText, mlkitVersion: String?): ExpectedReceipt {
        val rows = rows(text)
        val totalIndex = rows.indexOfFirst { row ->
            letters(row).let { l -> totalWords.any { l.startsWith(it) } }
        }
        val total = rows.getOrNull(totalIndex)?.let {
            trailingAmount.matchEntire(it)
        }?.let { parseCents(it.groupValues[2]) }
        val items = (if (totalIndex >= 0) rows.take(totalIndex) else rows)
            .filter { row -> letters(row).let { l -> skippedWords.none { l.startsWith(it) } } }
            .mapNotNull { row -> item(row)?.takeIf { it.name.isNotEmpty() } }
        return ExpectedReceipt(
            merchant = rows.firstOrNull()?.takeIf { trailingAmount.matchEntire(it) == null },
            items = items,
            total = total,
            itemCount = rows.firstNotNullOfOrNull { row ->
                itemCount.find(row)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toInt()
            },
            mlkitVersion = mlkitVersion
        )
    }

    /** Item from a row ending with its price, null if the row has no price */
    fun item(row: String): ExpectedItem? {
        val match = trailingAmount.matchEntire(row) ?: return null
        val amount = parseCents(match.groupValues[2].replace(" ", "")) ?: return null
        return ExpectedItem(
            match.groupValues[1].trim(),
            amount,
            taxClass = match.groupValues[3].ifEmpty {
                null
            }
        )
    }

    /**
     * One item from several rows, e.g. the name in one row and the price in the next, or a
     * quantity row "2 x 0,89" below the name. Rows in the order of the receipt.
     * Rows without price make up the name, the last price is the amount.
     */
    fun combine(rows: List<String>): ExpectedItem {
        val names = mutableListOf<String>()
        var amount: Long? = null
        var taxClass: String? = null
        var quantity: String? = null
        var unit: String? = null
        var unitPrice: Long? = null
        fun quantity(text: String): Boolean {
            val match = quantityRow.matchEntire(text.trim()) ?: return false
            quantity = number(match.groupValues[1])
            unit = "kg".takeIf { match.groupValues[2].lowercase() in setOf("kg", "k9", "kq") }
            unitPrice = parseCents(match.groupValues[3].replace(" ", ""))
            return true
        }

        /** Name followed by unit price and count, or count and unit price: keeps the name */
        fun inline(text: String): String {
            inlineUnitPriceTimesCount.matchEntire(text)?.let { match ->
                unitPrice = parseCents(match.groupValues[2].replace(" ", ""))
                quantity = match.groupValues[3]
                return match.groupValues[1].trim()
            }
            inlineCountTimesUnitPrice.matchEntire(text)?.let { match ->
                quantity = match.groupValues[2]
                unitPrice = parseCents(match.groupValues[3].replace(" ", ""))
                return match.groupValues[1].trim()
            }
            return text
        }
        for (row in rows) {
            if (quantity(row)) continue
            val priced = trailingAmount.matchEntire(row)
            val price = priced?.let { parseCents(it.groupValues[2].replace(" ", "")) }
            if (priced == null || price == null) {
                names += inline(row.trim())
                continue
            }
            amount = price
            priced.groupValues[3].takeIf { it.isNotEmpty() }?.let { taxClass = it }
            val rest = priced.groupValues[1].trim()
            if (rest.isNotEmpty() && !quantity(rest)) names += inline(rest)
        }
        val computed = amount ?: unitPrice?.let { price ->
            quantity?.toBigDecimal()?.multiply(price.toBigDecimal())
                ?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()
        }
        return ExpectedItem(
            name = names.filter { it.isNotEmpty() }.joinToString(" "),
            amount = computed ?: 0,
            quantity = quantity ?: "1",
            unit = unit,
            unitPrice = unitPrice,
            taxClass = taxClass
        )
    }

    /** "0, 750" → "0.750" */
    private fun number(text: String) = text.replace(" ", "").replace(',', '.')

    /**
     * Where an item read from the row at [row] goes, so that items keep the order of the receipt:
     * after the items whose names appear in the rows above
     */
    fun insertionIndex(rows: List<String>, items: List<ExpectedItem>, row: Int): Int {
        val above = rows.take(row).map(::normalize)
        return items.indexOfFirst { item ->
            val name = normalize(item.name)
            name.isNotEmpty() && above.none { it.contains(name) }
        }.takeIf { it >= 0 } ?: items.size
    }

    private fun normalize(text: String) = text.filter { it.isLetterOrDigit() }.lowercase()

    private fun letters(text: String) = text.filter { it.isLetter() }.uppercase()

    /** Text of the rows from top to bottom */
    fun rows(text: RecognizedText) = text.rows().map { it.rowText() }
}
