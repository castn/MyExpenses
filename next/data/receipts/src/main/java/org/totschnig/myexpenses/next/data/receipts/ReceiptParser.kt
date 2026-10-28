package org.totschnig.myexpenses.next.data.receipts

import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.RecognizedLine
import org.totschnig.myexpenses.feature.RecognizedText
import org.totschnig.myexpenses.next.data.receipts.ReceiptLayout.Row
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

/** A row among the items no item was read from, e.g. an item the parser missed */
data class LeftoverRow(val text: String, val box: Box)

data class ParsedReceipt(
    val merchant: String?,
    val items: List<ReceiptItem>,
    val total: Long?,
    /** Number of articles printed on the receipt ("7 Artikel", "Posten: 9") */
    val itemCount: Int?,
    /** Day of the purchase, null if none is readable */
    val date: LocalDate? = null,
    /**
     * Amount paid by card ("Geg. EC-Cash 12,15"), which the bank books: it differs from the
     * [total] by cash back or coupons. Null if paid in cash or unreadable.
     */
    val cardAmount: Long? = null,
    /** True if paid in cash, false if by card, null if the receipt does not tell */
    val cash: Boolean? = null,
    /** Rows among the items no item was read from, from top to bottom */
    val leftoverRows: List<LeftoverRow> = emptyList(),
) {
    val itemsSum: Long
        get() = items.sumOf { it.amount }

    /** Pieces of all items, weighed items count as one, discounts and returned deposit not at all */
    val pieces: Int
        get() = items.filter { it.amount > 0 }.sumOf { if (it.unit == null) it.quantity.toInt() else 1 }

    fun toReceipt() = Receipt(merchant, total, items, date = date, cardAmount = cardAmount)
}

/**
 * Reads merchant, items, total, number of articles, date and payment of a till receipt from text
 * recognition, in four layers:
 *
 * - [ReceiptLayout] rebuilds the rows from the positions of the recognized lines
 * - [ReceiptTokens] splits a row into numbers, units, signs and words
 * - [RowKinds] tells what a row is: an item with its price, a quantity row, a second row, …
 * - [ReceiptAssembler] puts the items together from the rows
 *
 * The words of the receipts are in a [ReceiptVocabulary], German for now.
 */
object ReceiptParser {

    private val vocabulary = ReceiptVocabulary.GERMAN
    private val kinds = RowKinds(vocabulary)
    private val assembler = ReceiptAssembler(kinds, vocabulary)
    private val dates = ReceiptDates(vocabulary)

    /**
     * Where the total is: the [index] of its row and its [amount], both null if not found;
     * the items are in the rows before [itemsEnd]
     */
    private data class Total(val index: Int?, val amount: Long?, val itemsEnd: Int)

    fun parse(text: RecognizedText): ParsedReceipt {
        val rows = rows(text)
        val total = total(rows)
        val (discounts, finalTotal) = total.index
            ?.let { discountsAfterTotal(rows, it, total.amount) }
            ?: (emptyList<ReceiptItem>() to total.amount)
        val payment = payment(rows.drop(total.index ?: total.itemsEnd))
        val itemRows = rows.subList(0, total.itemsEnd)
        val assembled = assembler.assemble(itemRows)
        return ParsedReceipt(
            // Photos cut to the items start with an item
            merchant = rows.firstOrNull()?.text?.takeIf { amountOf(it) == null },
            items = assembled.items + discounts,
            // Address, phone and column headers above the first item are no items
            leftoverRows = itemRows.drop(itemRows.indexOfFirst { it in assembled.used }.coerceAtLeast(0))
                .filter { it !in assembled.used && it.text.count(Char::isLetterOrDigit) >= 2 && kinds.itemCount(it.text) == null }
                .map { LeftoverRow(it.text, it.box) },
            total = finalTotal,
            itemCount = itemCount(rows, total.index),
            date = dates.dateOf(rows.map { it.text }),
            cardAmount = payment.cardAmount,
            cash = payment.cash
        )
    }

    /**
     * The item the user reads from rows the parser did not take, in the order of the receipt:
     * as the parser would read them together if it takes all of them, otherwise the rows without price make up the name,
     * the last price is the amount and a quantity row gives count and unit price. Its amount is 0
     * if none is found, for the user to fill in.
     */
    fun itemOf(rows: List<LeftoverRow>): ReceiptItem {
        require(rows.isNotEmpty())
        val box = rows.map { it.box }.reduce(::union)
        val layoutRows = rows.map { ReceiptLayout.Row(RecognizedLine(it.text, it.box, emptyList()), it.box, 0.0) }
        val assembled = assembler.assemble(layoutRows)
        if (assembled.used.size == layoutRows.size) assembled.items.singleOrNull()?.let { return it.copy(box = box) }
        val names = mutableListOf<String>()
        var price: RowKinds.Price? = null
        var quantity: RowKinds.Quantity? = null
        for (row in rows) {
            kinds.quantity(row.text)?.let { quantity = it; continue }
            val rowPrice = kinds.price(kinds.tokens(row.text))
            if (rowPrice == null) {
                names += row.text.trim()
            } else {
                price = rowPrice
                row.text.substring(0, rowPrice.start).trim().takeIf { it.any(Char::isLetter) }
                    ?.let { names += kinds.withoutArticleNumber(it) }
            }
        }
        val counted = quantity?.let { (it.quantity * BigDecimal(it.unitPrice)).setScale(0, RoundingMode.HALF_UP).toLong() }
        val item = ReceiptItem(
            name = names.filter { it.isNotEmpty() }.joinToString(" "),
            amount = price?.cents ?: counted ?: 0,
            taxClass = price?.taxClass,
            box = box
        )
        return quantity?.takeIf { price == null || counted == price!!.cents }?.let {
            item.copy(quantity = it.quantity, unit = "kg".takeIf { _ -> it.unit == ReceiptUnit.KG }, unitPrice = it.unitPrice)
        } ?: item
    }

    private data class Payment(val cardAmount: Long?, val cash: Boolean?)

    /**
     * How the receipt was paid, from the [rows] after the total: a row naming a card ("Geg. EC-Cash
     * 12,15", "Kartenzahlung EUR") with its amount, or from an amount only row just below it; else a
     * row naming cash ("BAR 20,00", "Rückgeld"). Card wins, as card receipts may print "Bargeld"
     * for cash back.
     */
    private fun payment(rows: List<Row>): Payment {
        val card = rows.indexOfFirst { vocabulary.isCard(it.text) }
        if (card >= 0) {
            val amount = amountOf(rows[card].text)
                ?: rows.getOrNull(card + 1)?.takeIf { kinds.isAmountOnly(it.text) }?.let { amountOf(it.text) }
            return Payment(amount?.let(::abs), cash = false)
        }
        return Payment(null, cash = if (rows.any { vocabulary.isCash(it.text) }) true else null)
    }

    private fun rows(text: RecognizedText) = ReceiptLayout.rows(
        text.lines.map { it.copy(text = ReceiptTokens.clean(it.text)) }
            .filter { it.box != null && it.text.isNotBlank() && !kinds.isCurrencyOnly(it.text) },
        isPrice = kinds::isPrice,
        isLoneSign = kinds::isLoneSign
    )

    /**
     * The total row, "SUMME EUR 12,15". Without one, the items end at the payment, "Geg. EC-Cash
     * 12,15", whose amount is the total, and a row above with the same amount is the total whose
     * word was not recognized. A total row without amount takes it from an amount only row just
     * above or below it, or from the payment below.
     */
    private fun total(rows: List<Row>): Total {
        val index = rows.indexOfFirst { vocabulary.isTotal(it.text) }
        if (index >= 0) {
            amountOf(rows[index].text)?.let { return Total(index, it, index) }
            val above = rows.getOrNull(index - 1)?.takeIf { kinds.isAmountOnly(it.text) }
            val below = rows.getOrNull(index + 1)?.takeIf { kinds.isAmountOnly(it.text) || vocabulary.isPayment(it.text) }
            val amount = (above ?: below)?.let { amountOf(it.text) }?.let(::abs)
            return Total(index, amount, if (above != null && amount != null) index - 1 else index)
        }
        val payment = rows.indexOfFirst { vocabulary.isPayment(it.text) }
        if (payment < 0) return Total(null, null, rows.size)
        val amount = amountOf(rows[payment].text)?.let(::abs)
        val lastPriced = (payment - 1 downTo 0).firstOrNull { amountOf(rows[it].text) != null }
        return if (lastPriced != null && amount != null && amountOf(rows[lastPriced].text) == amount) {
            Total(lastPriced, amount, lastPriced)
        } else Total(null, amount, payment)
    }

    /**
     * Some tills subtract discounts after the total and print a second total: the discounts and
     * the final total
     */
    private fun discountsAfterTotal(rows: List<Row>, totalIndex: Int, total: Long?): Pair<List<ReceiptItem>, Long?> {
        val discounts = mutableListOf<ReceiptItem>()
        var finalTotal: Long? = null
        for (row in rows.drop(totalIndex + 1)) {
            if (vocabulary.isPayment(row.text)) break
            val amount = amountOf(row.text) ?: continue
            if (vocabulary.isTotal(row.text)) {
                finalTotal = amount
            } else if (amount < 0) {
                discounts += assembler.readItem(row) ?: continue
            } else break
        }
        if (discounts.isEmpty()) return discounts to total
        return discounts to (finalTotal ?: total?.plus(discounts.sumOf { it.amount }))
    }

    /**
     * The last count before the total, as some tills also count a subtotal ("Posten: 26"),
     * otherwise the first after it (Aldi prints "7 Artikel" below)
     */
    private fun itemCount(rows: List<Row>, totalIndex: Int?): Int? {
        fun countOf(row: Row) = kinds.itemCount(row.text)
        if (totalIndex == null) return rows.firstNotNullOfOrNull(::countOf)
        return rows.subList(0, totalIndex + 1).asReversed().firstNotNullOfOrNull(::countOf)
            ?: rows.drop(totalIndex + 1).firstNotNullOfOrNull(::countOf)
    }

    private fun amountOf(text: String) = kinds.price(kinds.tokens(text))?.cents
}
