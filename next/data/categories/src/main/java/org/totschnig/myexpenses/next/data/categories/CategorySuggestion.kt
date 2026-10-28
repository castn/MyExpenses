package org.totschnig.myexpenses.next.data.categories

import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.data.merchants.EATING_OUT
import org.totschnig.myexpenses.next.data.merchants.SUPERMARKETS
import org.totschnig.myexpenses.next.core.model.words.Words

/**
 * A transaction as input for [CategorySuggester]: with a category to learn from, or without to get a
 * suggestion. Splits and transfers are left out before.
 */
data class CategoryInput(
    val id: Long,
    val amount: Long,
    val payeeId: Long? = null,
    val payeeName: String? = null,
    val comment: String? = null,
    val categoryId: Long? = null,
)

/**
 * The transactions of [ledger] that can have a category: no transfers, no splits, and none whose
 * changes the database rejects
 */
fun categoryInputs(ledger: Ledger) = ledger.transactions
    .filter { it.transferAccountId == null && !it.isSplit && !it.isSealed }
    .map { CategoryInput(it.id, it.amount, it.payeeId, it.payeeName, it.comment, it.categoryId) }

/**
 * The user decided: transactions with these counterparts always get this category ("Immer so machen")
 *
 * @param merchants normalized names of merchants paid through a payment service, see [Counterpart.merchant]
 */
data class CategoryRule(
    val id: String,
    val payeeIds: Set<Long>,
    val categoryId: Long,
    val merchants: Set<String> = emptySet(),
) {
    fun matches(counterpart: Counterpart) =
        counterpart.payeeId?.let { it in payeeIds } == true || counterpart.merchant?.let { it in merchants } == true
}

/** Where a suggestion comes from, the most reliable first */
enum class SuggestionSource {
    /** A [CategoryRule] of the user */
    RULE,

    /** Earlier transactions with the same counterpart: payee, or merchant paid through a payment service */
    PAYEE,

    /** Words of payee and purpose that led to a category before */
    WORDS,

    /** A well-known merchant, e.g. a supermarket chain, matched to a category of the user by name */
    MERCHANT
}

/**
 * @param isCertain applied without asking, see [CategorySuggester.isCertain]
 */
data class CategorySuggestion(
    val transactionId: Long,
    val categoryId: Long,
    val source: SuggestionSource,
    val isCertain: Boolean,
)

/**
 * Suggests categories for transactions without one, from the rules of the user, the transactions
 * that have a category, and a list of well-known merchants. Credits and debits are looked at
 * separately, since e.g. a refund from a shop is no purchase.
 *
 * @param history all transactions, those with a category are learned from
 */
class CategorySuggester(
    history: List<CategoryInput>,
    rules: List<CategoryRule>,
    private val categories: List<CategoryInfo>,
) {
    private val rules = rules

    private val categorized = history.filter { it.categoryId != null }

    /** Categories of the earlier transactions by [counterpartKey] */
    private val payeeCategories: Map<String, List<Long>> =
        categorized.groupBy({ it.counterpartKey() }, { it.categoryId!! }).filterKeys { it != null }.mapKeys { it.key!! }

    /** Categories of the earlier transactions by word, see [wordsOf] */
    private val wordCategories: Map<String, List<Long>> =
        categorized.flatMap { input -> input.wordsOf().map { it to input.categoryId!! } }.groupBy({ it.first }, { it.second })

    private val categoriesById = categories.associateBy { it.id }

    fun suggest(transaction: CategoryInput): CategorySuggestion? {
        if (transaction.categoryId != null) return null
        // Categories that were deleted, or are meant for the other direction, are no suggestion
        fun suggestion(categoryId: Long, source: SuggestionSource, isCertain: Boolean = false) =
            categoriesById[categoryId]
                ?.takeIf { if (transaction.amount < 0) it.isExpense else it.isIncome }
                ?.let { CategorySuggestion(transaction.id, it.id, source, isCertain) }

        val counterpart = transaction.counterpart()
        counterpart?.let { rules.firstOrNull { rule -> rule.matches(it) } }?.let { rule ->
            suggestion(rule.categoryId, SuggestionSource.RULE, isCertain = true)?.let { return it }
        }
        transaction.counterpartKey()?.let(payeeCategories::get)?.let { earlier ->
            majority(earlier)?.let { (categoryId, count) ->
                suggestion(categoryId, SuggestionSource.PAYEE, isCertain(count, earlier.size))?.let { return it }
            }
        }
        byWords(transaction)?.let { categoryId ->
            suggestion(categoryId, SuggestionSource.WORDS)?.let { return it }
        }
        return byMerchant(transaction)?.let { suggestion(it, SuggestionSource.MERCHANT) }
    }

    fun suggestAll(transactions: List<CategoryInput>): List<CategorySuggestion> = transactions.mapNotNull(::suggest)

    /** Words that led to one category in most cases add up, the category with most of them wins */
    private fun byWords(transaction: CategoryInput): Long? =
        transaction.wordsOf()
            .mapNotNull { word -> wordCategories[word]?.takeIf { it.size >= MIN_WORD_COUNT } }
            .mapNotNull { earlier ->
                majority(earlier)?.takeIf { (_, count) -> count >= earlier.size * MIN_WORD_SHARE }
            }
            .groupBy({ it.first }, { it.second })
            .maxByOrNull { (_, counts) -> counts.sum() }
            ?.key

    private fun byMerchant(transaction: CategoryInput): Long? {
        if (transaction.amount >= 0) return null
        val text = Words.textOf(transaction.payeeName, transaction.comment)
        val labels = MERCHANTS.firstOrNull { (patterns, _) -> patterns.any { Words.startsWord(text, it) } }?.second
            ?: return null
        return labels.firstNotNullOfOrNull { label ->
            val wanted = " " + Words.normalize(label)
            // "Essen" matches the main category rather than "Haustiere > Essen"
            categories.filter { it.isExpense && (" " + Words.normalize(it.path)).endsWith(wanted) }
                .minByOrNull { it.path.length }?.id
        }
    }

    /** The category most earlier transactions had, if more than half of them, with their number */
    private fun majority(categoryIds: List<Long>): Pair<Long, Int>? =
        categoryIds.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value * 2 > categoryIds.size }
            ?.toPair()

    /** [Counterpart.key] with the direction, so that credits and debits of a payee are told apart */
    private fun CategoryInput.counterpartKey(): String? {
        val direction = if (amount < 0) "-" else "+"
        return counterpart()?.let { "$direction${it.key}" }
    }

    /**
     * Distinct words of payee and purpose that can tell something, with the direction: no numbers,
     * no words of payment methods
     */
    private fun CategoryInput.wordsOf(): Set<String> {
        val direction = if (amount < 0) "-" else "+"
        return Words.normalize(listOfNotNull(payeeName, comment).joinToString(" "))
            .split(" ")
            .filterTo(HashSet()) { word -> word.length >= MIN_WORD_LENGTH && word.all { it.isLetter() } && word !in STOP_WORDS }
            .mapTo(HashSet()) { "$direction$it" }
    }

    companion object {
        /**
         * Earlier transactions of a payee needed to apply its category without asking, and the share
         * that must have had it, as for confirming contracts automatically
         */
        const val CERTAIN_MIN_COUNT = 5
        const val CERTAIN_MIN_SHARE = 0.9

        fun isCertain(count: Int, total: Int) = total >= CERTAIN_MIN_COUNT && count >= total * CERTAIN_MIN_SHARE

        private const val MIN_WORD_LENGTH = 3

        /** A word needs to have been seen this often, and mostly with one category */
        private const val MIN_WORD_COUNT = 3
        private const val MIN_WORD_SHARE = 0.8

        /** Words of payment methods, legal forms and filler words, which tell nothing about the purchase */
        private val STOP_WORDS = setOf(
            "sepa", "lastschrift", "basislastschrift", "kartenzahlung", "karte", "kartennr", "girocard", "debitkarte",
            "visa", "mastercard", "maestro", "apple", "google", "pay", "zahlung", "überweisung", "dauerauftrag", "gutschrift",
            "einzug", "mandat", "mandatsref", "gläubiger", "glaeubiger", "end", "ref", "kundenref", "referenz", "datum", "uhr",
            "folgenr", "verfalld", "gmbh", "und", "der", "die", "das", "den", "dem", "des", "vom", "fuer", "für", "mit",
            "bei", "nach", "ohne", "the", "and", "for", "from", "card", "payment", "purchase", "debit", "credit", "transfer",
            "ltd", "inc", "llc", "markt", "filiale", "sagt", "danke", "deutschland", "germany", "gmbhco", "kgaa",
            // Payment services and their purposes, see [PaymentService]
            "paypal", "klarna", "europe", "sarl", "cie", "sca", "ihr", "ihre", "einkauf", "bestellung", "amazon", "amzn",
            "payments", "mktp"
        )

        /**
         * Patterns matched at the start of a word of payee or purpose, to names of categories to look
         * for in the user's tree, the more specific first. A name matches the end of a category path,
         * e.g. "Haustiere > Essen". The default tree comes first, then names users often choose.
         */
        private val MERCHANTS: List<Pair<List<String>, List<String>>> = listOf(
            SUPERMARKETS.flatMap { it.second } to listOf("Supermarkt", "Supermarket", "Lebensmittel", "Grocery", "Groceries", "Einkauf"),
            EATING_OUT to listOf("Restaurant", "Restaurants", "Essen gehen", "Eating out", "Lieferdienst"),
            listOf("bäckerei", "baeckerei", "backwerk", "backshop", "kamps", "ditsch") to listOf("Bäcker & Metzger", "Bäcker", "Baker", "Bäckerei", "Bakery"),
            listOf("aral", "shell", "esso", "jet tankstelle", "avia", "agip", "tankstelle", "totalenergies", "star tankstelle") to
                    listOf("Kraftstoff", "Fuel", "Tanken", "Benzin", "Sprit"),
            listOf("apotheke", "docmorris", "shop apotheke", "pharmacy") to listOf("Apotheker", "Chemist", "Apotheke", "Medikamente", "Pharmacy"),
            listOf("rossmann", "dm drogerie", "dm fil", "drogerie", "müller drogerie", "budnikowsky") to
                    listOf("Drogerie & Reinigungsmittel", "Drogerie", "Drugstore", "Körperpflege", "Hygiene"),
            listOf("deutsche bahn", "db vertrieb", "db fernverkehr", "flixbus", "flixtrain") to listOf("Zug", "Bahn (Fernverkehr)", "Train", "Bahn"),
            listOf("bvg", "mvg", "hvv", "rmv", "vrr", "kvb", "üstra", "vvs", "vgn", "deutschlandticket") to
                    listOf("U-Bahn", "ÖPNV & Deutschlandticket", "Metro", "Bus", "ÖPNV", "Nahverkehr", "Public transport"),
            listOf("lufthansa", "eurowings", "ryanair", "easyjet", "condor", "tuifly") to listOf("Flugzeug", "Airplane", "Flug", "Flüge"),
            listOf("zara", "primark", "zalando", "about you", "peek cloppenburg", "tk maxx", "uniqlo", "deichmann") to
                    listOf("Kleidung", "Clothing", "Bekleidung", "Schuhe"),
            listOf("obi", "bauhaus", "hornbach", "toom", "hagebau") to listOf("Baumarkt", "Heimwerken", "Ausrüstung", "Werkzeug", "Equipment"),
            listOf("ikea", "höffner", "xxxlutz", "poco") to listOf("Möbel", "Furniture", "Einrichtung"),
            listOf("mediamarkt", "media markt", "saturn", "cyberport", "notebooksbilliger") to listOf("Technik", "Elektronik & Geräte", "IT", "Elektronik", "Electronics"),
            listOf("cinemaxx", "cineplex", "uci kinowelt", "kino") to listOf("Kino", "Movies", "Cinema"),
            listOf("fressnapf", "zooplus", "das futterhaus") to
                    listOf("Haustiere > Essen", "Haustiere > Futter", "Pets > Food", "Tierbedarf", "Haustiere", "Pets"),
            listOf("friseur", "klier", "hairdresser") to listOf("Friseur", "Hairdresser"),
            listOf("thalia", "hugendubel", "buchhandlung") to listOf("Bücher", "Bücher & Zeitschriften", "Books")
        )
    }
}
