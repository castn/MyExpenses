package org.totschnig.myexpenses.next.data.guidance

import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.data.tips.DepositNotReturned
import org.totschnig.myexpenses.next.data.tips.DrugstoreInSupermarkets
import org.totschnig.myexpenses.next.data.tips.ItemGroupRising
import org.totschnig.myexpenses.next.data.tips.Outliers

class PendingTipsTest {

    private fun readiness(
        uncategorized: Int = 0,
        scannedRecent: Int = 0,
        scannedUsual: Int = 0,
        scannedLastYear: Int = 0,
        scannedSupermarketLastYear: Int = 0,
    ) = DataReadiness(
        debits = 50,
        uncategorized = List(uncategorized) { it.toLong() },
        vague = emptyList(),
        supermarketPurchases = emptyList(),
        unscanned = emptyList(),
        scannedRecent = scannedRecent,
        scannedUsual = scannedUsual,
        scannedLastYear = scannedLastYear,
        scannedSupermarketLastYear = scannedSupermarketLastYear,
        taggedDebits = 1
    )

    @Test
    fun withoutReceiptsAndCategoriesAllArePending() {
        assertEquals(PendingTip.entries, pendingTips(readiness(uncategorized = 20), emptySet()))
    }

    @Test
    fun enoughDataLeavesNonePending() {
        val readiness = readiness(
            scannedRecent = ItemGroupRising.MIN_RECENT_PURCHASES,
            scannedUsual = ItemGroupRising.MIN_USUAL_PURCHASES,
            scannedLastYear = 10,
            scannedSupermarketLastYear = 10
        )
        assertEquals(emptyList<PendingTip>(), pendingTips(readiness, emptySet()))
    }

    @Test
    fun aTipThatIsThereIsNotPending() {
        val ids = setOf("${ItemGroupRising.ID_PREFIX}SWEETS_SNACKS", DrugstoreInSupermarkets.ID, DepositNotReturned.ID, Outliers.ID)
        assertEquals(emptyList<PendingTip>(), pendingTips(readiness(uncategorized = 20), ids))
    }

    @Test
    fun receiptsOfOtherShopsDoNotCountForTheSupermarket() {
        val readiness = readiness(scannedRecent = 4, scannedUsual = 6, scannedLastYear = 10, scannedSupermarketLastYear = 1)
        assertEquals(listOf(PendingTip.DRUGSTORE_IN_SUPERMARKETS), pendingTips(readiness, emptySet()))
    }

    @Test
    fun withoutReceiptsNoTipAboutThemIsPending() {
        val readiness = readiness(uncategorized = 20).copy(canScanReceipts = false)
        assertEquals(listOf(PendingTip.OUTLIERS), pendingTips(readiness, emptySet()))
    }
}
