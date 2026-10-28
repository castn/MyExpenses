package org.totschnig.myexpenses.next.data.guidance

import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.data.tips.ItemGroupRising
import org.totschnig.myexpenses.next.core.model.words.Words
import org.totschnig.myexpenses.next.data.merchants.supermarketOf
import java.time.LocalDate

/**
 * How well the transactions are described, so that the analyses can tell something: how many debits
 * have a precise category, how many purchases have a scanned receipt, whether tags are used.
 * The analyses keep their own thresholds; this only counts, and [gaps] tells what is missing.
 *
 * @param debits of the last [RECENT_MONTHS] months that can have a category
 * @param uncategorized ids of [debits] without category
 * @param vague ids of [debits] in a catch-all category, e.g. "Sonstiges"
 * @param supermarketPurchases ids of [debits] at a supermarket chain
 * @param unscanned ids of [supermarketPurchases] without a scanned receipt
 * @param scannedRecent purchases with a scanned receipt in the recent months of [ItemGroupRising]
 * @param scannedUsual purchases with a scanned receipt in the year before them
 * @param scannedLastYear purchases with a scanned receipt within the last year
 * @param scannedSupermarketLastYear of [scannedLastYear] at a supermarket chain
 * @param taggedDebits debits of the last [TAG_MONTHS] months with a tag
 * @param canScanReceipts whether receipts can be scanned at all, see [ReceiptFacts.canScan]; without, nothing
 * asks for them
 */
data class DataReadiness(
    val debits: Int,
    val uncategorized: List<Long>,
    val vague: List<Long>,
    val supermarketPurchases: List<Long>,
    val unscanned: List<Long>,
    val scannedRecent: Int,
    val scannedUsual: Int,
    val scannedLastYear: Int,
    val scannedSupermarketLastYear: Int,
    val taggedDebits: Int,
    val canScanReceipts: Boolean = true,
) {
    /** What is missing, the most useful first. Empty when the data is good enough. */
    val gaps: List<DataGap>
        get() = buildList {
            if (debits < MIN_DEBITS) return@buildList
            if (isMany(uncategorized)) add(DataGap.Uncategorized(uncategorized, debits))
            if (isMany(vague)) add(DataGap.VagueCategories(vague, debits))
            if (canScanReceipts && !ItemGroupRising.hasEnough(scannedRecent, scannedUsual)) add(
                DataGap.FewReceipts(
                    scanned = scannedRecent,
                    supermarketPurchases = supermarketPurchases.size,
                    unscanned = unscanned,
                    missingRecent = (ItemGroupRising.MIN_RECENT_PURCHASES - scannedRecent).coerceAtLeast(0),
                    missingUsual = (ItemGroupRising.MIN_USUAL_PURCHASES - scannedUsual).coerceAtLeast(0)
                )
            )
            if (taggedDebits == 0) add(DataGap.NoTags)
        }

    private fun isMany(ids: List<Long>) = ids.size >= MIN_COUNT && ids.size >= debits * MAX_SHARE

    companion object {
        /** Categories are looked at in the last months, so that old habits do not count */
        const val RECENT_MONTHS = 3L

        /** Tags are looked at over a year, a trip may be months ago */
        const val TAG_MONTHS = 12L

        /** Fewer debits are too few to tell anything, the analysis tab says so itself */
        const val MIN_DEBITS = 10

        /** Fewer debits without a precise category are no gap */
        const val MIN_COUNT = 3

        /** A larger share of debits without a precise category is a gap */
        const val MAX_SHARE = 0.1

        /** Words of catch-all categories, matched at the start of a word of the last part of the category */
        val CATCH_ALL = listOf(
            "sonstig", "diverses", "verschiedenes", "allgemein", "unbekannt",
            "other", "misc", "various", "general", "unknown"
        )

        fun isCatchAll(categoryPath: String?): Boolean {
            val last = categoryPath?.substringAfterLast('>')?.takeIf { it.isNotBlank() } ?: return false
            val text = Words.textOf(last)
            return CATCH_ALL.any { Words.startsWord(text, it) }
        }
    }
}

/** Something the analyses lack to tell something precise */
sealed interface DataGap {
    /** Tells the kinds of gaps apart, e.g. to remember which one is opened */
    val key: String

    /** Debits that can be shown to fix the gap, none if empty */
    val transactionIds: List<Long> get() = emptyList()

    /** Of [total] debits, those with [transactionIds] have no category */
    data class Uncategorized(override val transactionIds: List<Long>, val total: Int) : DataGap {
        override val key: String get() = "uncategorized"
        val count: Int get() = transactionIds.size
    }

    /** Of [total] debits, those with [transactionIds] are in a catch-all category */
    data class VagueCategories(override val transactionIds: List<Long>, val total: Int) : DataGap {
        override val key: String get() = "vague"
        val count: Int get() = transactionIds.size
    }

    /**
     * Too few scanned receipts to compare what was bought
     *
     * @param scanned in the recent months
     * @param supermarketPurchases in the recent months, with or without receipt
     * @param unscanned the recent supermarket purchases without receipt, which could still be scanned
     * @param missingRecent / [missingUsual] scanned purchases still needed in the recent months and
     * the year before, see [ItemGroupRising]
     */
    data class FewReceipts(
        val scanned: Int,
        val supermarketPurchases: Int,
        val unscanned: List<Long>,
        val missingRecent: Int,
        val missingUsual: Int,
    ) : DataGap {
        override val key: String get() = "receipts"
        override val transactionIds: List<Long> get() = unscanned
    }

    /** No debit of the last year has a tag */
    data object NoTags : DataGap {
        override val key: String get() = "tags"
    }
}

/** Debits that can have a category: as [org.totschnig.myexpenses.next.categories.categoryInputs], without credits and own transfers */
private fun LedgerTransaction.isCategorizableDebit() = amount < 0 && transferAccountId == null &&
        targetAccountId == null && !isSplit && !isSealed

/**
 * @param receipts the transactions with a scanned receipt are the keys
 */
fun dataReadiness(ledger: Ledger, receipts: ReceiptFacts, today: LocalDate): DataReadiness {
    val debits = ledger.transactions.filter { it.amount < 0 && !it.date.isAfter(today) }
    val recentStart = today.minusMonths(DataReadiness.RECENT_MONTHS)
    val recent = debits.filter { it.isCategorizableDebit() && it.date.isAfter(recentStart) }
    val scanned = debits.filter { it.id in receipts.items }
    val tagStart = today.minusMonths(DataReadiness.TAG_MONTHS)
    val scannedLastYear = scanned.filter { it.date.isAfter(today.minusYears(1)) }
    val supermarket = recent.filter { supermarketOf(it.payeeName) != null }
    return DataReadiness(
        debits = recent.size,
        uncategorized = recent.filter { it.categoryId == null }.map { it.id },
        vague = recent.filter { DataReadiness.isCatchAll(it.categoryPath) }.map { it.id },
        supermarketPurchases = supermarket.map { it.id },
        unscanned = supermarket.filter { it.id !in receipts.items }.map { it.id },
        scannedRecent = scanned.count { ItemGroupRising.isRecent(it.date, today) },
        scannedUsual = scanned.count { ItemGroupRising.isUsual(it.date, today) },
        scannedLastYear = scannedLastYear.size,
        scannedSupermarketLastYear = scannedLastYear.count { supermarketOf(it.payeeName) != null },
        taggedDebits = debits.count { it.tagIds.isNotEmpty() && it.date.isAfter(tagStart) },
        canScanReceipts = receipts.canScan
    )
}
