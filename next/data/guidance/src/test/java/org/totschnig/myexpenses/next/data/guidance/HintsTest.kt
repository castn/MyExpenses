package org.totschnig.myexpenses.next.data.guidance

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import java.time.LocalDate

class HintsTest {

    private fun purchase(categoryId: Long? = 1, payee: String? = "REWE Markt", amount: Long = -2500) =
        LedgerTransaction(1, LocalDate.of(2026, 10, 15), amount, accountId = 1, payeeName = payee, categoryId = categoryId)

    @Test
    fun afterSavingACategoryComesFirst() {
        assertEquals(HintTopic.CATEGORY, hintAfterSave(purchase(categoryId = null), hasReceipt = false, emptySet()))
        assertEquals(HintTopic.RECEIPT, hintAfterSave(purchase(), hasReceipt = false, emptySet()))
        assertEquals(HintTopic.RECEIPT, hintAfterSave(purchase(categoryId = null), hasReceipt = false, setOf(HintTopic.CATEGORY)))
    }

    @Test
    fun noHintAfterSavingWhatNeedsNone() {
        assertNull(hintAfterSave(purchase(), hasReceipt = true, emptySet()))
        assertNull(hintAfterSave(purchase(payee = "Bäckerei"), hasReceipt = false, emptySet()))
        assertNull(hintAfterSave(purchase(categoryId = null, amount = 2500), hasReceipt = false, emptySet()))
        assertNull(hintAfterSave(purchase(categoryId = null).copy(transferAccountId = 2), hasReceipt = false, emptySet()))
        assertNull(hintAfterSave(purchase(categoryId = null), hasReceipt = false, HintTopic.entries.toSet()))
        assertNull(hintAfterSave(purchase(), hasReceipt = false, emptySet(), canScanReceipts = false))
    }

    private fun readiness(uncategorized: Int, unscanned: Int) = DataReadiness(
        debits = 50,
        uncategorized = List(uncategorized) { it.toLong() },
        vague = emptyList(),
        supermarketPurchases = List(unscanned) { it.toLong() },
        unscanned = List(unscanned) { it.toLong() },
        scannedRecent = 0,
        scannedUsual = 0,
        scannedLastYear = 0,
        scannedSupermarketLastYear = 0,
        taggedDebits = 0
    )

    @Test
    fun overviewTellsAboutTheFirstGapNotClosed() {
        val readiness = readiness(uncategorized = 20, unscanned = 5)
        assertEquals(DataGap.Uncategorized::class, overviewHint(readiness, emptySet())!!::class)
        assertEquals(DataGap.FewReceipts::class, overviewHint(readiness, setOf(HintTopic.CATEGORY))!!::class)
        assertNull(overviewHint(readiness, HintTopic.entries.toSet()))
        // Neither tags nor receipts without a purchase to scan have a hint
        assertNull(overviewHint(readiness(uncategorized = 0, unscanned = 0), emptySet()))
        assertNull(overviewHint(null, emptySet()))
    }

    @Test
    fun storeHidesAfterSaveOnceClosedOrShownOftenEnough() = withTestDataStore { dataStore ->
        val store = HintStore(dataStore)
        assertEquals(emptySet<HintTopic>(), store.hiddenAfterSave.first())
        repeat(HintTopic.MAX_AFTER_SAVE) { store.shownAfterSave(HintTopic.RECEIPT) }
        assertEquals(setOf(HintTopic.RECEIPT), store.hiddenAfterSave.first())
        assertEquals(emptySet<HintTopic>(), store.dismissed.first())
        store.dismiss(HintTopic.CATEGORY)
        assertEquals(setOf(HintTopic.CATEGORY), store.dismissed.first())
        assertEquals(HintTopic.entries.toSet(), store.hiddenAfterSave.first())
        store.reset()
        assertEquals(emptySet<HintTopic>(), store.hiddenAfterSave.first())
    }
}
