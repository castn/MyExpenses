package org.totschnig.myexpenses.next.data.receipts

import org.totschnig.myexpenses.next.data.receipts.ReceiptLayout.Row
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer

/** Accents taken apart by [Normalizer], built once since building a regex takes long on Android */
private val MARKS = Regex("""\p{M}""")

/**
 * Puts the items together from the rows between head and total. Each row goes to the first rule
 * that takes it, in this order:
 *
 * 1. [Assembly.nameBelowArticleNumber]: rows without price after "2945139 48,89 b" are its name
 *    (electronics stores)
 * 2. [Assembly.subtotal]: "Zw.Summe 43,10" is no item
 * 3. [Assembly.quantityRow]: "3 Stk x 0,59" goes to the item above if the amounts fit (REWE),
 *    otherwise to the item below (Aldi, Netto)
 * 4. [Assembly.secondRow]: "1,00 Stück x 3,65 3,65 B" with the name in the row above,
 *    "Käse-Ei Brötchen" (bakery, DIY store, butcher)
 * 5. [Assembly.itemRow]: "KARTOFFELN 2, 22 B", also with count and unit price after the name,
 *    "MEGGLE BAGUET 1,32 € x X 3 3,96 AP"
 * 6. [Assembly.nameOnly]: a name without price waits for its second row (rule 4)
 */
internal class ReceiptAssembler(private val kinds: RowKinds, private val vocabulary: ReceiptVocabulary) {

    fun items(rows: List<Row>): List<ReceiptItem> = assemble(rows).items

    /** The items and the rows they were read from, subtotals included, as they need no attention */
    fun assemble(rows: List<Row>): Assembled = Assembly().apply { rows.forEach(::add) }.let { Assembled(it.items, it.used) }

    class Assembled(val items: List<ReceiptItem>, val used: Set<Row>)

    /** Count or weight and unit price, applied to an item whose amount they give */
    private class Quantity(val quantity: BigDecimal, val unit: String?, val unitPrice: Long) {
        fun fits(item: ReceiptItem) = item.quantity.compareTo(BigDecimal.ONE) == 0 && item.unitPrice == null &&
                (quantity * BigDecimal(unitPrice)).setScale(0, RoundingMode.HALF_UP).toLong() == item.amount

        fun applyTo(item: ReceiptItem) = item.copy(quantity = quantity, unit = unit, unitPrice = unitPrice)
    }

    private inner class Assembly {
        val items = mutableListOf<ReceiptItem>()

        /** Rows something was read from, by identity */
        val used: MutableSet<Row> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

        /** A quantity row printed above its item */
        private var quantityAbove: Quantity? = null
        private var quantityAboveRow: Row? = null

        /** A row with a name but without price, whose price may follow in the next row */
        private var nameRow: Row? = null

        /** Index of the item of an article number row, whose name follows in the rows below */
        private var nameBelow: Int? = null

        fun add(row: Row) {
            if (nameBelowArticleNumber(row)) return
            nameBelow = null
            if (subtotal(row) || quantityRow(row) || secondRow(row) || itemRow(row)) return
            nameOnly(row)
        }

        /** Rule 1 */
        fun nameBelowArticleNumber(row: Row): Boolean {
            val index = nameBelow ?: return false
            if (readItem(row) != null || quantity(row.text) != null || row.text.none { it.isLetter() }) return false
            val item = items[index]
            items[index] = item.copy(name = "${item.name} ${row.text}".trim(), box = item.box?.let { union(it, row.box) })
            used += row
            return true
        }

        /** Rule 2 */
        fun subtotal(row: Row): Boolean {
            if (!vocabulary.isSubtotal(row.text)) return false
            nameRow = null
            used += row
            return true
        }

        /** Rule 3 */
        fun quantityRow(row: Row): Boolean {
            val quantity = quantity(row.text) ?: return false
            val previous = items.lastOrNull()
            if (previous != null && quantity.fits(previous)) {
                items[items.lastIndex] = quantity.applyTo(previous)
                used += row
                quantityAbove = null
            } else {
                quantityAbove = quantity
                quantityAboveRow = row
            }
            nameRow = null
            return true
        }

        /** Rule 4 */
        fun secondRow(row: Row): Boolean {
            val item = nameRow?.let { readSecondRow(it, row) } ?: return false
            items += item
            used += nameRow!!
            used += row
            quantityAbove = null
            nameRow = null
            return true
        }

        /** Rule 5 */
        fun itemRow(row: Row): Boolean {
            val item = readItem(row) ?: return false
            if (item.name.isEmpty() && kinds.isArticleNumberOnly(row.text.substring(0, priceOf(row.text)!!.start))) {
                nameBelow = items.size
            }
            val above = quantityAbove?.takeIf { it.fits(item) }
            items += above?.applyTo(item) ?: item
            used += row
            if (above != null) quantityAboveRow?.let { used += it }
            quantityAbove = null
            nameRow = null
            return true
        }

        /** Rule 6 */
        fun nameOnly(row: Row) {
            nameRow = row.takeIf { it.text.count(Char::isLetter) >= 2 && kinds.detail(it.text) == null }
        }
    }

    /**
     * The item of a row with its price at the end: "KARTOFFELN 2, 22 B", with count and unit
     * price after the name, "G&G HASELNUSS 2,99 € x2 5,98 A", or only those when the price was not
     * recognized, "U.H.BUTTER M2 1,69 €X2-A"; null for a row without price
     */
    fun readItem(row: Row): ReceiptItem? {
        val text = row.text
        val price = priceOf(text) ?: return readUnitPriceTimesCount(row)
        val name = kinds.withoutArticleNumber(text.substring(0, price.start).trim())
            .takeUnless { kinds.isArticleNumberOnly(it) }.orEmpty()
        val item = ReceiptItem(name = name, amount = price.cents, taxClass = price.taxClass?.let(::taxClass), box = row.box)
        val inline = kinds.inlineQuantity(kinds.tokens(name)) ?: return item
        return Quantity(inline.count, null, inline.unitPrice)
            .takeIf { it.fits(item) }
            ?.applyTo(item.copy(name = name.substring(0, inline.nameEnd).trim()))
            ?: item
    }

    /** The price is not recognized, but count and unit price give the amount: "U.H.BUTTER M2 1,69 €X2-A" */
    private fun readUnitPriceTimesCount(row: Row): ReceiptItem? {
        val match = kinds.unitPriceTimesCountOnly(kinds.tokens(row.text)) ?: return null
        val name = row.text.substring(0, match.nameEnd).trim()
        if (name.count(Char::isLetter) < 2) return null
        return ReceiptItem(
            name = kinds.withoutArticleNumber(name),
            amount = match.unitPrice * match.count.toLong(),
            quantity = match.count,
            unitPrice = match.unitPrice,
            box = row.box
        )
    }

    /**
     * The item with the name in [nameRow] and quantity and prices in [row] below it:
     * "Käse-Ei Brötchen" and "1,00 Stück x 3,65 3,65 B"; null if [row] is no such second row,
     * e.g. only an article number, whose name follows below
     */
    private fun readSecondRow(nameRow: Row, row: Row): ReceiptItem? {
        val text = row.text
        val price = priceOf(text) ?: return null
        val before = text.substring(0, price.start)
        if (kinds.isArticleNumberOnly(before)) return null
        val detail = kinds.detail(before) ?: return null
        val item = ReceiptItem(
            name = kinds.withoutArticleNumber(nameRow.text),
            amount = price.cents,
            taxClass = price.taxClass?.let(::taxClass),
            box = union(nameRow.box, row.box)
        )
        val quantity = detail.quantity ?: return item
        val unitPrice = detail.unitPrice ?: return item
        val value = quantity.stripTrailingZeros()
        // Weighed items show the weight with decimals under a "kg" column header
        val weighed = detail.unit == ReceiptUnit.KG || (detail.unit == null && value.scale() > 0)
        return Quantity(value, "kg".takeIf { weighed }, unitPrice).takeIf { it.fits(item) }?.applyTo(item) ?: item
    }

    private fun quantity(text: String) = kinds.quantity(text)?.let {
        Quantity(it.quantity, "kg".takeIf { _ -> it.unit == ReceiptUnit.KG }, it.unitPrice)
    }

    private fun priceOf(text: String) = kinds.price(kinds.tokens(text))

    /** "À" → "A" */
    private fun taxClass(text: String) = text.takeIf { it.isNotEmpty() }
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFD).replace(MARKS, "") }
}
