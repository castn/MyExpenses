package org.totschnig.myexpenses.next.data.receipts

/** Built once, building a regex takes long on Android */
private val SPACES = Regex("""\s+""")

/**
 * The words of the receipts of one language: what marks the total, a subtotal, the payment, how
 * it was paid and the number of articles, the units, the currency, the names of the months, and
 * the letters recognition confuses.
 * Keywords are given in capitals and compared with the letters of a row only, so that
 * "Zu zahlen" and "ZU ZAHLEN:" both match "ZUZAHLEN".
 *
 * Another language needs another instance, the rules for rows stay the same.
 */
internal class ReceiptVocabulary(
    /** A row starting with one of these is the total */
    private val totalStarts: List<String>,
    /** A word that marks the total anywhere in its row ("2 Artikel TOTAL 7,50 €") */
    private val totalWords: Set<String>,
    /** Total words recognition misreads, matched with one wrong letter after [confusions] */
    private val misreadTotalWords: List<String>,
    /** A row starting with one of these carries an amount, but is no item */
    private val subtotalStarts: List<String>,
    /** A row containing one of these is the payment, which follows the total */
    private val paymentWords: List<String>,
    /** A word of a row naming a card payment contains one of these ("EC-Cash", "Kartenzahlung", "Visa") */
    private val cardWords: List<String>,
    /** A row with one of these words names a cash payment or the change ("BAR", "Rückgeld") */
    private val cashWords: Set<String>,
    /** Words after the number of articles ("7 Artikel", "5 Pos") */
    val countAfterNumber: List<String>,
    /** Words before the number of articles ("Posten: 9", "Artikel 8") */
    val countBeforeNumber: List<String>,
    /** Units by how they are written and misread, in small letters */
    val units: Map<String, ReceiptUnit>,
    /** How the currency is written next to amounts */
    val currencies: Set<String>,
    /** The months, January first, by the start of their names in small letters ("aug" for "Aug.") */
    val months: List<String>,
    /** Letters recognition reads instead of others, as wrong to right */
    private val confusions: Map<Char, Char>,
) {

    fun isTotal(text: String): Boolean {
        val letters = letters(text)
        if (isSubtotal(text)) return false
        return totalStarts.any { letters.startsWith(it) } ||
                words(text).any { word -> word in totalWords || misreadTotalWords.any { looksLike(word, it) } }
    }

    fun isSubtotal(text: String) = letters(text).let { letters -> subtotalStarts.any { letters.startsWith(it) } }

    fun isPayment(text: String) = letters(text).let { letters -> paymentWords.any { it in letters } }

    fun isCard(text: String) = words(text).any { word -> cardWords.any { it in word } }

    fun isCash(text: String) = words(text).any { it in cashWords }

    /**
     * The number of the month whose name [word] starts with, 1 for January, null if none;
     * umlauts may be read without dots ("Mar" for "Mär")
     */
    fun monthOf(word: String): Int? = foldUmlauts(word.lowercase()).takeIf { it.length >= 3 }
        ?.let { name -> months.indexOfFirst { name.startsWith(foldUmlauts(it)) } }?.takeIf { it >= 0 }?.plus(1)

    private fun foldUmlauts(text: String) = text.replace('ä', 'a').replace('ö', 'o').replace('ü', 'u')

    fun unitOf(word: String) = units[word.lowercase().removeSuffix(".")]

    /**
     * [word] is [keyword] read with one wrong letter, after undoing the [confusions];
     * the first two letters must be right, as short words are easily alike
     */
    private fun looksLike(word: String, keyword: String) =
        word.length == keyword.length && word.take(2) == keyword.take(2) &&
                word.map { confusions[it] ?: it }.zip(keyword.toList()).count { (a, b) -> a != b } <= 1

    private fun words(text: String) = text.split(SPACES).map(::letters)

    private fun letters(text: String) = text.filter { it.isLetter() }.uppercase()

    companion object {
        val GERMAN = ReceiptVocabulary(
            totalStarts = listOf("SUMME", "ZUZAHLEN", "GESAMT", "TOTAL", "ALDIPREIS", "EINKAUFSWERT"),
            totalWords = setOf("SUMME", "TOTAL", "GESAMTSUMME"),
            misreadTotalWords = listOf("SUMME"),
            subtotalStarts = listOf("ZWSUMME", "ZWISCHENSUMME", "SUBTOTAL", "NEUERPREIS"),
            paymentWords = listOf("ZAHLUNG", "ECCASH", "ECASH", "BARGELD", "GEGEBEN"),
            // "ARTENZAHLUNG" and "FCCASH" as misread on dm and REWE receipts
            cardWords = listOf(
                "ECCASH", "FCCASH", "ECASH", "ECKARTE", "KARTE", "ARTENZAHLUNG", "GIROCARD", "CARD", "VISA",
                "MAESTRO", "VPAY", "KONTAKTLOS", "DEBIT", "AMEX", "APPLEPAY", "GOOGLEPAY"
            ),
            cashWords = setOf("BAR", "BARGELD", "BARZAHLUNG", "RÜCKGELD", "ZURÜCK"),
            countAfterNumber = listOf("artikel", "pos"),
            countBeforeNumber = listOf("artikel", "posten"),
            units = mapOf(
                "kg" to ReceiptUnit.KG, "k9" to ReceiptUnit.KG, "kq" to ReceiptUnit.KG,
                "stück" to ReceiptUnit.PIECE, "stk" to ReceiptUnit.PIECE, "st" to ReceiptUnit.PIECE,
                "stuck" to ReceiptUnit.PIECE, "stúck" to ReceiptUnit.PIECE, "stùck" to ReceiptUnit.PIECE,
                "5tuck" to ReceiptUnit.PIECE, "5tück" to ReceiptUnit.PIECE,
                "gtuck" to ReceiptUnit.PIECE, "gtúck" to ReceiptUnit.PIECE, "gtück" to ReceiptUnit.PIECE,
            ),
            currencies = setOf("€", "EUR", "e"),
            months = listOf("jan", "feb", "mär", "apr", "mai", "jun", "jul", "aug", "sep", "okt", "nov", "dez"),
            confusions = mapOf('N' to 'M'),
        )
    }
}
