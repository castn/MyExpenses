package org.totschnig.myexpenses.feature

import java.util.IdentityHashMap

/**
 * Removes personal data from a receipt before it becomes a test fixture, keeping what the parser
 * reads and the positions of all remaining lines:
 *
 * - Head (above the first item): the words after the name of the chain (see [chain]) count as the
 *   owner and are removed wherever they appear, e.g. in a logo; street, postcode and town, phone,
 *   tax number, owner and web address are replaced by fixed placeholders, a row above postcode
 *   and town counts as street; other rows, e.g.
 *   column headers, stay with long numbers, dates and times masked.
 * - Items up to the payment row stay; rows without price in between are masked.
 * - Foot (after the payment row): removed, except the number of articles ("7 Artikel").
 * - Lines without box are removed, the parser ignores them.
 *
 * Masking replaces digits by 0, so that patterns like article numbers keep their shape.
 * Lines the rules miss can be marked by hand; their text is hidden in the same shape.
 */
object ReceiptAnonymizer {

    /**
     * @param merchant what the merchant row reads after anonymizing, for the expected values
     * @param redactions areas of the photo whose text was replaced, masked or removed
     */
    data class Result(val text: RecognizedText, val merchant: String?, val redactions: List<Box>)

    const val STREET = "Musterstraße 1"
    const val TOWN = "12345 Musterstadt"
    const val PHONE = "Tel. 01234 567890"
    const val TAX_NUMBER = "USt-IdNr. DE000000000"
    const val OWNER = "Inh. Max Mustermann"
    const val WEB = "www.example.de"

    private val taxNumber = Regex("""(?i)\b(ust|uid|st\.?\s?-?\s?nr|steuer|vat|ident)""")
    private val phone = Regex("""(?i)\b(tel|telefon|fon|fax)\b|\d{3,5}\s*[/-]\s*\d{3,}""")
    private val town = Regex("""(?i)(^|\s)(D\s?-\s?)?\d{5}\s+\p{L}""")
    private val street = Regex("""(?i)(str\.|straße|strasse|weg|platz|allee|gasse|ring|damm|ufer)\s*\d""")
    private val web = Regex("""(?i)(www\.|https?://|@|\.(de|com|net|eu)\b)""")
    /**
     * Owner or the company behind the shop, legal forms also as recognition misreads them ("GabH");
     * KG only as "& Co. KG", as kg is the unit in column headers
     */
    private val owner = Regex(
        """(?i)\b(inh\.|inhaber|inhaberin|filialleit\p{L}*|marktleit\p{L}*|fr\.|frau|hr\.|herr)(\s|$)""" +
                """|\bg\p{L}bh\b|\bohg\b|\be\.\s?k\.|&\s?co\.?\s?kg\b"""
    )

    private val longNumber = Regex("""\d{5,}""")
    private val date = Regex("""\b\d{1,2}\s?\.\s?\d{1,2}\s?\.\s?\d{2,4}\b""")
    private val dateWithMonth = Regex(
        """(?i)\b\d{1,2}\.?\s+(jan|feb|mär|mar|apr|mai|may|jun|jul|aug|sep|okt|oct|nov|dez|dec)\p{L}*\.?\s+\d{2,4}\b"""
    )
    private val time = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val price = Regex("""\d{1,5}\s?[,.]\s?\d{2}""")
    /** Only an amount, e.g. the paid amount recognized a little below the payment row */
    private val amountOnly = Regex("""^[\s€]*(EUR)?[\s€]*-?[\d\s,.]+[\s€]*(EUR)?\s*$""")
    private val itemCount = Regex("""(?i)\d+\s+artikel|posten\s*:?\s*\d+|\d+\s*pos\b""")
    private val paymentWords = listOf("ZAHLUNG", "ECCASH", "ECASH", "BARGELD", "GEGEBEN", "GIROCARD", "MAESTRO", "VISA", "MASTERCARD")

    private sealed interface Action
    private data object Keep : Action
    private data object Mask : Action
    private data object Remove : Action
    /** A head row that is no address: owner's name removed, numbers masked */
    private data object Head : Action
    /** Marked by the user: same shape, no content */
    private data object Hide : Action
    private data class Replace(val placeholder: String) : Action

    /**
     * @param marked boxes of lines the user marked to be hidden, wherever they are on the receipt
     */
    fun anonymize(text: RecognizedText, expected: ExpectedReceipt, marked: Set<Box> = emptySet()): Result {
        val rows = text.rows()
        val names = expected.items.map { normalize(it.name) }.filter { it.isNotEmpty() }
        fun isItem(row: List<RecognizedLine>) = normalize(row.rowText()).let { row ->
            names.any { name -> row.contains(name) || (row.length >= 3 && name.startsWith(row)) }
        }
        fun hasPrice(row: List<RecognizedLine>) = price.containsMatchIn(row.rowText())

        val firstItem = rows.indexOfFirst(::isItem).takeIf { it >= 0 }
            ?: rows.indexOfFirst(::hasPrice).takeIf { it >= 0 }
            ?: rows.size
        val lastItem = rows.indexOfLast(::isItem).coerceAtLeast(firstItem)
        val payment = (lastItem + 1 until rows.size).firstOrNull { i ->
            letters(rows[i].rowText()).let { letters -> paymentWords.any { it in letters } }
        }
        val footStart = payment?.plus(1) ?: rows.size
        val firstRow = rows.firstOrNull()?.takeIf { firstItem > 0 }?.rowText()
        // Words after the name of the chain may be the owner, wherever they are in the head
        val owner = listOfNotNull(expected.merchant, firstRow).flatMap(::ownerWords).toSet()

        val head = rows.take(firstItem).map { row -> row.map { classify(it.text) } }
        fun isTown(i: Int) = head.getOrNull(i)?.any { it == Replace(TOWN) } == true
        val actions = IdentityHashMap<RecognizedLine, Action>()
        rows.forEachIndexed { i, row ->
            row.forEachIndexed { j, line ->
                actions[line] = when {
                    line.box in marked -> Hide
                    // The street is printed above postcode and town, even where recognition garbles it
                    i < firstItem && head[i][j] == Head && isTown(i + 1) && row.rowText() != firstRow -> Replace(STREET)
                    i < firstItem -> head[i][j]
                    i < footStart -> if (hasPrice(row) || isItem(row)) Keep else Mask
                    itemCount.containsMatchIn(row.rowText()) -> Mask
                    i == footStart && amountOnly.matches(row.rowText()) -> Keep
                    else -> Remove
                }
            }
        }

        val redactions = mutableListOf<Box>()
        val blocks = text.blocks.mapNotNull { block ->
            block.lines.mapNotNull { line ->
                when (val action = actions[line] ?: Remove) {
                    Keep -> line
                    Hide -> line.copy(text = hide(line.text), elements = line.elements.map { it.copy(text = hide(it.text)) })
                        .also { line.box?.let(redactions::add) }
                    Remove -> null.also { line.box?.let(redactions::add) }
                    Mask -> line.copy(text = mask(line.text), elements = line.elements.map { element ->
                        element.copy(text = mask(element.text)).also {
                            if (it.text != element.text) element.box?.let(redactions::add)
                        }
                    }).also { if (it.elements.isEmpty() && it.text != line.text) line.box?.let(redactions::add) }
                    Head -> withoutOwner(line.copy(text = mask(line.text)), owner, redactions)?.let { cleaned ->
                        cleaned.copy(elements = cleaned.elements.map { element ->
                            element.copy(text = mask(element.text)).also {
                                if (it.text != element.text) element.box?.let(redactions::add)
                            }
                        })
                    }
                    is Replace -> line.copy(text = action.placeholder, elements = line.elements.map { it.copy(text = hide(it.text)) })
                        .also { line.box?.let(redactions::add) }
                }
            }.takeIf { it.isNotEmpty() }?.let(::RecognizedBlock)
        }
        val anonymized = RecognizedText(blocks)
        val merchant = when {
            expected.merchant == null -> null
            // The parser reads the merchant from the first row
            expected.merchant.trim() == firstRow -> anonymized.rows().firstOrNull()?.rowText()
            else -> withoutWords(expected.merchant, owner)
        }
        return Result(anonymized, merchant, redactions)
    }

    private fun classify(text: String) = when {
        taxNumber.containsMatchIn(text) -> Replace(TAX_NUMBER)
        phone.containsMatchIn(text) -> Replace(PHONE)
        town.containsMatchIn(text) -> Replace(TOWN)
        street.containsMatchIn(text) -> Replace(STREET)
        owner.containsMatchIn(text) -> Replace(OWNER)
        web.containsMatchIn(text) -> Replace(WEB)
        else -> Head
    }

    /**
     * A word written like a name: a capital and at least one small letter ("Kampmann", "Süd"),
     * digits included, as recognition reads l as 1 ("K1ink")
     */
    private val nameLike = Regex("""\p{Lu}(?=.*\p{Ll})[\p{Ll}\d]+.*""")

    /**
     * The name of the chain: the merchant up to the first later word written like a name, which
     * may be the owner ("EDEKA Kampmann" → "EDEKA", "Bäckerei Schmidt" → "Bäckerei", "TAKE AWAY" stays)
     */
    internal fun chain(merchant: String): String {
        val words = merchant.trim().split(Regex("""\s+"""))
        val cut = (1 until words.size).firstOrNull { nameLike.matches(words[it]) } ?: words.size
        return words.take(cut).joinToString(" ")
    }

    /** Words the chain name leaves out, compared by letters and digits only */
    private fun ownerWords(merchant: String) =
        merchant.trim().split(Regex("""\s+""")).drop(chain(merchant).split(' ').size)
            .map(::normalize).filter { it.length >= 3 }

    private fun isOwner(word: String, owner: Set<String>) =
        normalize(word).let { normalized -> normalized.isNotEmpty() && owner.any { it in normalized } }

    private fun withoutWords(text: String, owner: Set<String>) =
        text.trim().split(Regex("""\s+""")).filterNot { isOwner(it, owner) }.joinToString(" ")

    /** [line] without the owner's name, its words hidden and painted over; null if nothing is left */
    private fun withoutOwner(line: RecognizedLine, owner: Set<String>, redactions: MutableList<Box>): RecognizedLine? {
        if (owner.isEmpty()) return line
        val text = withoutWords(line.text, owner)
        if (text == line.text.trim()) return line
        if (text.isEmpty()) {
            line.box?.let(redactions::add)
            return null
        }
        return line.copy(text = text, elements = line.elements.map { element ->
            if (isOwner(element.text, owner)) element.copy(text = hide(element.text)).also { element.box?.let(redactions::add) }
            else element
        })
    }

    internal fun mask(text: String) = listOf(longNumber, date, dateWithMonth, time).fold(text) { result, pattern ->
        pattern.replace(result) { match -> match.value.replace(Regex("""\d"""), "0") }
    }

    /** Same shape, no content */
    private fun hide(text: String) = text.map {
        when {
            it.isLetter() -> 'X'
            it.isDigit() -> '0'
            else -> it
        }
    }.joinToString("")

    private fun letters(text: String) = text.filter { it.isLetter() }.uppercase()

    private fun normalize(text: String) = text.filter { it.isLetterOrDigit() }.lowercase()
}
