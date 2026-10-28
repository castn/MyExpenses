package org.totschnig.myexpenses.next.data.receipts

import java.math.BigDecimal

/**
 * The kinds of rows on a receipt, recognized from their [Token]s. The examples are rows as
 * recognized on the receipts of the fixtures.
 */
internal class RowKinds(private val vocabulary: ReceiptVocabulary) {

    /**
     * The price at the end of a row
     *
     * @param start where the price starts in the row, the name ends before
     */
    class Price(val cents: Long, val taxClass: String?, val start: Int)

    /** Count or weight and the price of one piece or kilogram */
    class Quantity(val quantity: BigDecimal, val unit: ReceiptUnit?, val unitPrice: Long)

    /** Count and unit price after the name, which ends at [nameEnd] */
    class InlineQuantity(val nameEnd: Int, val count: BigDecimal, val unitPrice: Long)

    /**
     * The part of a second row before its price: article number, quantity and unit price, each
     * optional
     */
    class Detail(val quantity: BigDecimal?, val unit: ReceiptUnit?, val unitPrice: Long?)

    fun tokens(text: String) = ReceiptTokens.tokens(text, vocabulary)

    /**
     * The price at the end of a row: an amount, then maybe the currency and the tax class.
     * "KARTOFFELN 2, 22 B", "Knusperstange 2 x €0,99 €1,98 A", "Burek Käse 4,50 €",
     * "309172 1 3,79 3,79A", "Mivolis Wundspray 3,15 1 1" (dm prints a digit and the count),
     * "GEIG.PRISECCO0,75L 10,998" (the tax digit glued to the amount).
     */
    fun price(tokens: List<Token>): Price? {
        tokens.forEachIndexed { i, token ->
            val number = token as? Token.Number ?: return@forEachIndexed
            if (number.value.abs() >= MAX_AMOUNT) return@forEachIndexed
            // "x2,50" is no price
            if (tokens.getOrNull(i - 1)?.let { it is Token.Times && it.end == number.start } == true) return@forEachIndexed
            val start = tokens.getOrNull(i - 1)
                ?.takeIf { it is Token.Currency && it.end == number.start }?.start ?: number.start
            val rest = tokens.subList(i + 1, tokens.size)
            when {
                number.isAmount -> taxAfter(rest)?.let { return Price(number.cents, it.taxClass, start) }
                number.value.scale() == 3 && rest.isEmpty() -> {
                    val digits = number.text.filter(Char::isDigit)
                    val cents = digits.dropLast(1).toLong().let { if (number.value.signum() < 0) -it else it }
                    return Price(cents, digits.takeLast(1), start)
                }
            }
        }
        return null
    }

    /** The whole text is a price, e.g. a segment of the price column: "1,10 A", "€1,98 A" */
    fun isPrice(text: String) = tokens(text).let { tokens -> price(tokens)?.start == tokens.firstOrNull()?.start }

    /**
     * Only an amount, maybe with currency and tax class, e.g. the total recognized apart from its
     * row: "EUR 12, 15"
     */
    fun isAmountOnly(text: String): Boolean {
        val tokens = tokens(text)
        val price = price(tokens) ?: return false
        return tokens.takeWhile { it.start < price.start }.all { it is Token.Currency }
    }

    /** Only the currency, as printed above the price column: "EUR" */
    fun isCurrencyOnly(text: String) = tokens(text).singleOrNull() is Token.Currency

    /** Only the sign between quantity and price: "x" */
    fun isLoneSign(text: String) = tokens(text).singleOrNull()?.let { it is Token.Times && it.text.length == 1 } == true

    /**
     * A row of its own with count or weight and the price of one: "3 Stk x 0,59",
     * "0, 750 kg x 1,79 EUR/kg", "2 Stk x x 1, 19", "4 X 0.99", "0,942 kg x 1,79 EUR/kg X".
     * The sign may be missing if unit and price per unit say what the row is:
     * "0,764 kg 1,29 EUR/kg".
     */
    fun quantity(text: String): Quantity? {
        val tokens = tokens(text)
        var i = 0
        val quantity = tokens.getOrNull(i++) as? Token.Number ?: return null
        if (quantity.value.signum() < 0) return null
        val unit = (tokens.getOrNull(i) as? Token.Unit)?.also { i++ }
        val withSign = tokens.getOrNull(i) is Token.Times
        if (!withSign && unit == null) return null
        while (tokens.getOrNull(i) is Token.Times) i++
        if (tokens.getOrNull(i) is Token.Currency) i++
        val unitPrice = (tokens.getOrNull(i++) as? Token.Number)?.takeIf { it.isAmount } ?: return null
        if (tokens.getOrNull(i) is Token.Currency) i++
        val perUnit = tokens.getOrNull(i) is Token.PerUnit
        if (perUnit) i++
        if (!withSign && !perUnit) return null
        // A stray sign at the end
        if (tokens.getOrNull(i) is Token.Times) i++
        if (i != tokens.size) return null
        return Quantity(quantity.value, unit?.unit, unitPrice.cents)
    }

    /**
     * Count and unit price after the name, in either order: "MEGGLE BAGUET 1,32 € x X 3",
     * "G&G HASELNUSS 2,99 € x2", "FRISCHE FEIGE 0,39 € Lx 8", "BAGUETTE 2 Stk x 1,19"
     */
    fun inlineQuantity(tokens: List<Token>): InlineQuantity? {
        for (nameEnd in 1 until tokens.size) {
            val rest = tokens.subList(nameEnd, tokens.size)
            (unitPriceTimesCount(rest, wholeRest = true) ?: countTimesUnitPrice(rest))?.let { (count, unitPrice) ->
                return InlineQuantity(tokens[nameEnd].start, count, unitPrice)
            }
        }
        return null
    }

    /**
     * An item whose price was not recognized, but whose unit price and count were, maybe followed
     * by more: "U.H.BUTTER M2 1,69 €X2-A"
     */
    fun unitPriceTimesCountOnly(tokens: List<Token>): InlineQuantity? {
        for (nameEnd in 1 until tokens.size) {
            unitPriceTimesCount(tokens.subList(nameEnd, tokens.size), wholeRest = false)?.let { (count, unitPrice) ->
                return InlineQuantity(tokens[nameEnd].start, count, unitPrice)
            }
        }
        return null
    }

    /**
     * The second row of an item printed on two lines, before its price (bakery, DIY store,
     * butcher): article number, quantity, unit and unit price, each optional.
     * "1,00 Stück x 3,65", "309172 1 3,79", "1.640 14.90", "2 x €0,99", null for "Bez."
     */
    fun detail(text: String): Detail? {
        val tokens = tokens(text)
        var i = 0
        // An article number before more
        if (tokens.size > 1 && isArticleNumber(tokens[0], 5)) i++
        val quantity = (tokens.getOrNull(i) as? Token.Number)?.takeIf { it.value.signum() >= 0 }
        if (quantity == null) return Detail(null, null, null).takeIf { i == tokens.size }
        i++
        val unit = (tokens.getOrNull(i) as? Token.Unit)?.also { i++ }
        while (tokens.getOrNull(i) is Token.Times) i++
        val currencyBefore = tokens.getOrNull(i) is Token.Currency
        if (currencyBefore) i++
        val unitPrice = (tokens.getOrNull(i) as? Token.Number)?.takeIf { it.isAmount }
        if (unitPrice != null) {
            i++
            if (tokens.getOrNull(i) is Token.Currency) i++
            if (tokens.getOrNull(i) is Token.PerUnit) i++
        } else if (currencyBefore) return null
        if (i != tokens.size) return null
        return Detail(quantity.value, unit?.unit, unitPrice?.cents)
    }

    /** Only an article number, the name of the item follows below (electronics stores): "2945139" */
    fun isArticleNumberOnly(text: String) = tokens(text).singleOrNull()?.let { isArticleNumber(it, 5) } == true

    /** The name without the article number some tills print before it: "655809 BBQ Grillkäse" */
    fun withoutArticleNumber(name: String): String {
        val tokens = tokens(name)
        return if (tokens.size > 1 && isArticleNumber(tokens[0], 4)) name.substring(tokens[1].start) else name
    }

    /**
     * The number of articles: "7 Artikel", "Posten: 9", "Artikel 8", "5 Pos", and in brackets on
     * the total row, "SUMME [5]"
     */
    fun itemCount(text: String): Int? {
        val tokens = tokens(text)
        tokens.forEachIndexed { i, token ->
            if (token is Token.Number && isCount(token) &&
                (tokens.getOrNull(i + 1) as? Token.Word)?.let { word(it) in vocabulary.countAfterNumber } == true
            ) return token.value.toInt()
            if (token is Token.Word && word(token) in vocabulary.countBeforeNumber) {
                val next = tokens.getOrNull(i + 1)?.takeUnless { it is Token.Word && it.text == ":" } ?: tokens.getOrNull(i + 2)
                if (next is Token.Number && isCount(next)) return next.value.toInt()
            }
        }
        if (vocabulary.isTotal(text)) {
            tokens.firstNotNullOfOrNull { token ->
                (token as? Token.Word)?.text?.takeIf { it.startsWith("[") && it.endsWith("]") }
                    ?.drop(1)?.dropLast(1)?.toIntOrNull()
            }?.let { return it }
        }
        return null
    }

    private fun isCount(number: Token.Number) = number.value.scale() == 0 && number.value.signum() >= 0

    /** Letters of a word in small letters, "Posten:" → "posten" */
    private fun word(token: Token.Word) = token.text.filter { it.isLetter() }.lowercase()

    private fun isArticleNumber(token: Token, digits: Int) =
        token is Token.Number && token.text.length >= digits && token.text.all(Char::isDigit)

    /** "1,32 € x X 3", "0,39 € Lx 8": unit price, sign and count; [wholeRest] or followed by anything */
    private fun unitPriceTimesCount(tokens: List<Token>, wholeRest: Boolean): Pair<BigDecimal, Long>? {
        var i = 0
        if (tokens.getOrNull(i) is Token.Currency) i++
        val unitPrice = (tokens.getOrNull(i++) as? Token.Number)?.takeIf { it.isAmount } ?: return null
        if (tokens.getOrNull(i) is Token.Currency) i++
        if (!isTimes(tokens.getOrNull(i))) return null
        while (isTimes(tokens.getOrNull(i))) i++
        val count = (tokens.getOrNull(i++) as? Token.Number)?.takeIf { isCount(it) } ?: return null
        if (wholeRest) {
            if (tokens.getOrNull(i) is Token.Times) i++
            if (i != tokens.size) return null
        }
        return count.value to unitPrice.cents
    }

    /** "2 Stk x 1,19": count, unit, sign and unit price */
    private fun countTimesUnitPrice(tokens: List<Token>): Pair<BigDecimal, Long>? {
        var i = 0
        val count = (tokens.getOrNull(i++) as? Token.Number)?.takeIf { isCount(it) } ?: return null
        if (tokens.getOrNull(i) is Token.Unit) i++
        if (tokens.getOrNull(i) !is Token.Times) return null
        while (tokens.getOrNull(i) is Token.Times) i++
        if (tokens.getOrNull(i) is Token.Currency) i++
        val unitPrice = (tokens.getOrNull(i++) as? Token.Number)?.takeIf { it.isAmount } ?: return null
        if (tokens.getOrNull(i) is Token.Currency) i++
        if (i != tokens.size) return null
        return count.value to unitPrice.cents
    }

    /** The sign, also with a letter recognition glued to it: "x", "Lx" */
    private fun isTimes(token: Token?) = token is Token.Times ||
            token is Token.Word && token.text.length >= 2 && token.text[0].isLetter() && token.text.drop(1).all { it in "xX×*" }

    private class Tax(val taxClass: String?)

    /**
     * What may follow the amount at the end of a row: currency, tax class and a digit, each
     * optional; null if anything else follows
     */
    private fun taxAfter(rest: List<Token>): Tax? {
        var i = 0
        if (rest.getOrNull(i) is Token.Currency) i++
        if (rest.getOrNull(i)?.text == ".") i++
        val tax = rest.getOrNull(i)?.let(::taxClassOf)
        if (tax != null) {
            i++
            // dm prints the count after the tax class
            if ((rest.getOrNull(i) as? Token.Number)?.text?.let { it.length == 1 && it[0].isDigit() } == true) i++
        }
        return Tax(tax).takeIf { i == rest.size }
    }

    /**
     * A tax class: a capital, maybe with a second one or the marks recognition reads instead
     * ("A", "AW", "A#", "À"), a small letter other than e, which stands for €, a digit (dm) or "*"
     */
    private fun taxClassOf(token: Token): String? = when (token) {
        is Token.Word -> token.text.trimEnd { it in NOISE }.takeIf { text ->
            text.length == 1 && (text[0].isUpperCase() || text[0].isLowerCase() && text[0] != 'e') ||
                    text.length == 2 && text[0].isUpperCase() && (text[1].isUpperCase() || text[1] in "#*")
        }
        is Token.Number -> token.text.takeIf { it.length == 1 && it[0].isDigit() }
        is Token.Times -> token.text.takeIf { it == "*" }
        else -> null
    }

    companion object {
        private val MAX_AMOUNT = BigDecimal(100000)
        private const val NOISE = ".,:;"
    }
}
