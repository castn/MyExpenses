package org.totschnig.myexpenses.next.data.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import java.time.LocalDate

class DataReadinessTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 15)
    private var nextId = 1L

    private fun debit(
        daysAgo: Long,
        categoryId: Long? = 1,
        categoryPath: String? = "Lebensmittel",
        payee: String? = "Bäcker",
        tagIds: Set<Long> = emptySet(),
        isSplit: Boolean = false,
    ) = LedgerTransaction(
        nextId++, today.minusDays(daysAgo), -1000, accountId = 1, payeeName = payee,
        categoryId = categoryId, categoryPath = categoryPath, tagIds = tagIds, isSplit = isSplit
    )

    private fun readiness(transactions: List<LedgerTransaction>, scanned: List<LedgerTransaction> = emptyList()) =
        dataReadiness(
            Ledger(transactions, OwnAccounts(emptySet(), emptyMap())),
            ReceiptFacts(scanned.associate { it.id to listOf(ScannedItem("Brot", 300)) }),
            today
        )

    @Test
    fun withoutReceiptsNoGapAsksForThem() {
        val readiness = dataReadiness(Ledger(wellDescribed(), OwnAccounts(emptySet(), emptyMap())), ReceiptFacts.None, today)
        assertEquals(emptyList<DataGap>(), readiness.gaps)
    }

    /** Twenty debits in the last weeks with a precise category and a tag */
    private fun wellDescribed() = (1L..20).map { debit(it, tagIds = setOf(1)) }

    /** Scanned purchases, four in the recent months and six in the year before */
    private fun enoughReceipts() =
        (1L..4).map { debit(it * 7, payee = "REWE") } + (1L..6).map { debit(100 + it * 30, payee = "REWE") }

    @Test
    fun wellDescribedDataHasNoGaps() {
        val receipts = enoughReceipts()
        assertEquals(emptyList<DataGap>(), readiness(wellDescribed() + receipts, receipts).gaps)
    }

    @Test
    fun countsOnlyRecentDebitsThatCanHaveACategory() {
        val readiness = readiness(
            listOf(
                debit(10, categoryId = null),
                debit(200, categoryId = null),
                debit(10).copy(amount = 1000),
                debit(10, categoryId = null).copy(transferAccountId = 2),
                debit(10, categoryId = null).copy(targetAccountId = 2),
                debit(10, isSplit = true),
                debit(10, categoryId = null).copy(isSealed = true),
                debit(-3, categoryId = null),
            )
        )
        assertEquals(1, readiness.debits)
        assertEquals(1, readiness.uncategorized.size)
    }

    @Test
    fun aFewDebitsWithoutCategoryAreNoGap() {
        val receipts = enoughReceipts()
        val some = wellDescribed() + (1L..2).map { debit(it, categoryId = null, categoryPath = null) }
        assertTrue(readiness(some + receipts, receipts).gaps.isEmpty())
        val without = (1L..5).map { debit(it, categoryId = null, categoryPath = null) }
        val gap = readiness(wellDescribed() + without + receipts, receipts).gaps.single() as DataGap.Uncategorized
        assertEquals(without.map { it.id }, gap.transactionIds)
        assertEquals(29, gap.total)
    }

    @Test
    fun catchAllCategoriesAreVague() {
        assertTrue(DataReadiness.isCatchAll("Sonstiges"))
        assertTrue(DataReadiness.isCatchAll("Haushalt > Sonstige Ausgaben"))
        assertTrue(DataReadiness.isCatchAll("Misc"))
        assertFalse(DataReadiness.isCatchAll("Sonstiges > Friseur"))
        assertFalse(DataReadiness.isCatchAll("Lebensmittel"))
        assertFalse(DataReadiness.isCatchAll(null))
        val vague = (1L..5).map { debit(it, categoryPath = "Sonstiges") }
        assertTrue(DataGap.VagueCategories(vague.map { it.id }, 25) in readiness(wellDescribed() + vague).gaps)
    }

    @Test
    fun receiptsAreCountedInTheWindowsOfTheItemGroupTip() {
        val recent = (1L..2).map { debit(it * 7, payee = "REWE") }
        val notScanned = debit(3, payee = "Aldi Süd")
        val usual = debit(150, payee = "Lidl")
        val readiness = readiness(wellDescribed() + recent + notScanned + usual, recent + usual)
        assertEquals(2, readiness.scannedRecent)
        assertEquals(1, readiness.scannedUsual)
        assertEquals(3, readiness.scannedLastYear)
        assertEquals(3, readiness.scannedSupermarketLastYear)
        assertEquals(3, readiness.supermarketPurchases.size)
        assertEquals(
            listOf(
                DataGap.FewReceipts(
                    scanned = 2, supermarketPurchases = 3, unscanned = listOf(notScanned.id), missingRecent = 2, missingUsual = 5
                )
            ),
            readiness.gaps
        )
    }

    @Test
    fun noTagWithinAYearIsAGap() {
        val receipts = enoughReceipts()
        val oldTag = (1L..20).map { debit(it) } + debit(400, tagIds = setOf(1))
        assertEquals(listOf(DataGap.NoTags), readiness(oldTag + receipts, receipts).gaps)
    }

    @Test
    fun fewDebitsGiveNoGaps() {
        assertTrue(readiness((1L..9).map { debit(it, categoryId = null, categoryPath = null) }).gaps.isEmpty())
    }
}
