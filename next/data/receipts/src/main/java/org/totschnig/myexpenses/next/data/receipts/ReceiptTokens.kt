package org.totschnig.myexpenses.next.data.receipts

import java.math.BigDecimal

/** Unit of a quantity on a receipt */
internal enum class ReceiptUnit { PIECE, KG }

/**
 * A piece of a receipt row: a number, a sign or a word, with the errors of recognition evened
 * out. [start] and [end] point into the text of the row, so that names can be cut from it as
 * recognized.
 */
internal sealed interface Token {
    val text: String
    val start: Int
    val end: Int

    /** "2", "1,79", "0, 750", "1.640", "-0,30" and "0,30-" */
    data class Number(override val text: String, override val start: Int, override val end: Int, val value: BigDecimal) : Token {
        /** Two decimals, like a price */
        val isAmount: Boolean
            get() = value.scale() == 2

        val cents: Long
            get() = value.movePointRight(2).toLong()
    }

    /** "€", "EUR", "e" after an amount, "E" before one */
    data class Currency(override val text: String, override val start: Int, override val end: Int) : Token

    /** The sign between count and price: "x", "X", "×", "*", also doubled "x x" */
    data class Times(override val text: String, override val start: Int, override val end: Int) : Token

    /** "kg", "Stk", "St", "Stück", and as recognition misreads them, "k9", "5tuck" */
    data class Unit(override val text: String, override val start: Int, override val end: Int, val unit: ReceiptUnit) : Token

    /** "/kg" after a unit price, "€/kg" and "EUR/kg" give a [Currency] before it */
    data class PerUnit(override val text: String, override val start: Int, override val end: Int, val unit: ReceiptUnit) : Token

    /** Anything else: words of names, tax classes, article numbers with letters */
    data class Word(override val text: String, override val start: Int, override val end: Int) : Token
}

/**
 * Splits receipt rows into [Token]s. Recognition splits numbers at spaces ("2, 22"), glues signs
 * to numbers ("€0,99", "3,79A", "6x", "x2") and adds stray marks ("9,99."), which is undone here,
 * so that the rules for rows only deal with clean pieces.
 */
internal object ReceiptTokens {

    private const val TIMES = "xX×*"
    /** Marks recognition puts after numbers */
    private const val NOISE = ".,:;'´`"
    /** A minus after the number only with decimals, "2-A" is 2 with tax class A */
    private val number = Regex("""-?\d+(?:[,.]\d+-?)?""")
    private val taxLike = Regex("""\p{Lu}[\p{Lu}#*]?|-\p{Lu}""")
    private val markAfterDigit = Regex("""(?<=\d)['´`]""")
    private val iBeforeAmount = Regex("""(?<![\p{L}\d])[iI](?=\d{1,5},\d{2}(?!\d))""")
    private val nonSpaces = Regex("""\S+""")
    private val numberWithSeparator = Regex("""[€E]?-?\d+[,.]""")
    private val integer = Regex("""[€E]?-?\d+""")
    private val decimals = Regex("""[,.]\d.*""")

    /**
     * A recognized line without the marks recognition reads from pen strokes and stains:
     * a leading "/" or "|", "'" after digits, an "i" before an amount ("i3,12")
     */
    fun clean(text: String) = text.trim()
        .trimStart('/', '|')
        .replace(markAfterDigit, "")
        .replace(iBeforeAmount, "")
        .trim()

    fun tokens(text: String, vocabulary: ReceiptVocabulary = ReceiptVocabulary.GERMAN): List<Token> =
        chunks(text, vocabulary).flatMap { (chunk, position) -> Splitter(vocabulary).split(chunk, position) }

    /**
     * Pieces between spaces, with numbers split at a space put together again ("2, 22", "3 ,29"),
     * each with the position in the row of every character
     */
    private fun chunks(text: String, vocabulary: ReceiptVocabulary): List<Pair<String, (Int) -> Int>> {
        val raw = nonSpaces.findAll(text).map { it.value to it.range.first }.toList()
        val result = mutableListOf<Pair<String, (Int) -> Int>>()
        var i = 0
        while (i < raw.size) {
            val (chunk, start) = raw[i]
            val next = raw.getOrNull(i + 1)?.first
            val joined = when {
                next == null -> null
                // "2," + "22", "€6," + "12"
                numberWithSeparator.matches(chunk) && next.first().isDigit() -> chunk + next
                // "3" + ",29"
                integer.matches(chunk) && decimals.matches(next) -> chunk + next
                // "EUR /" + "kg", "/" + "kg"
                chunk.endsWith("/") && vocabulary.unitOf(next) != null -> chunk + next
                else -> null
            }
            if (joined != null) {
                val gap = raw[i + 1].second - (start + chunk.length)
                result += joined to { index: Int -> start + index + if (index >= chunk.length) gap else 0 }
                i += 2
            } else {
                result += chunk to { index: Int -> start + index }
                i++
            }
        }
        return result
    }

    /** Splits the pieces between spaces, with the units and currencies of [vocabulary] */
    private class Splitter(private val vocabulary: ReceiptVocabulary) {

        /** Tokens of [chunk], [position] gives the position in the row of a character of it */
        fun split(chunk: String, position: (Int) -> Int): List<Token> {
            val start = position(0)
            val end = position(chunk.length - 1) + 1
            fun rest(from: Int) = split(chunk.substring(from)) { position(from + it) }
            if (chunk.all { it in TIMES }) return listOf(Token.Times(chunk, start, end))
            if (chunk in vocabulary.currencies) return listOf(Token.Currency(chunk, start, end))
            vocabulary.unitOf(chunk)?.let { return listOf(Token.Unit(chunk, start, end, it)) }
            perUnit(chunk, position)?.let { return it }
            // Signs glued before a number: "€0,99", "E6,12", "€x", "x2"
            val beforeDigit = chunk.length > 1 && chunk[1].isDigit()
            if (chunk.startsWith("€") || chunk.startsWith("E") && beforeDigit) {
                return listOf(Token.Currency(chunk.take(1), start, start + 1)) + rest(1)
            }
            if (chunk.first() in TIMES && beforeDigit) {
                return listOf(Token.Times(chunk.take(1), start, start + 1)) + rest(1)
            }
            val match = number.matchAt(chunk, 0) ?: return listOf(Token.Word(chunk, start, end))
            val length = match.value.length
            val token = Token.Number(match.value, start, position(length - 1) + 1, value(match.value))
            val tail = chunk.substring(length)
            return when {
                tail.isEmpty() || tail.all { it in NOISE } -> listOf(token)
                // "9,99€", "9,99e", "6x", "1,69€X2-A", "1,79€/kg"
                vocabulary.currencies.any { tail == it || it.length > 1 && tail.startsWith(it) } || tail.startsWith("€") ||
                        tail.first() in TIMES || tail.startsWith("/") ->
                    listOf(token) + rest(length)
                // "3,79A", "2,98B", "2-A", "17.90.A"
                taxLike.matches(tail.trim { it in NOISE }) -> {
                    val noise = tail.takeWhile { it in NOISE }.length
                    listOf(token, Token.Word(tail.substring(noise), position(length + noise), end))
                }
                // "200g", "0,75L", "2009": part of a name
                else -> listOf(Token.Word(chunk, start, end))
            }
        }

        /** "/kg", "€/kg", "EUR/kg", "e/kg" */
        private fun perUnit(chunk: String, position: (Int) -> Int): List<Token>? {
            val slash = chunk.indexOf('/')
            if (slash < 0) return null
            val currency = chunk.substring(0, slash)
            if (currency.isNotEmpty() && currency !in vocabulary.currencies) return null
            val unit = vocabulary.unitOf(chunk.substring(slash + 1)) ?: return null
            val perUnit = Token.PerUnit(chunk.substring(slash), position(slash), position(chunk.length - 1) + 1, unit)
            return if (currency.isEmpty()) listOf(perUnit)
            else listOf(Token.Currency(currency, position(0), position(slash)), perUnit)
        }
    }

    /** "1,79" → 1.79, "0,30-" → -0.30 */
    private fun value(text: String): BigDecimal {
        val negative = text.startsWith('-') || text.endsWith('-')
        val value = BigDecimal(text.trim('-').replace(" ", "").replace(',', '.'))
        return if (negative) value.negate() else value
    }
}
