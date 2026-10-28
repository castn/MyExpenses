package org.totschnig.myexpenses.next.data.receipts

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup

class ReceiptCodecsTest {

    // Without photo: android.net.Uri cannot be created in a JVM test
    private val draft = ReceiptDraft(
        target = ReceiptTarget.Transaction(7),
        transactionAmount = 1234,
        merchant = "REWE",
        total = 1200,
        items = listOf(
            ReceiptItem(
                "Bananen", 199, BigDecimal("0.856"), "kg", 233, "A", Box(1, 2, 3, 4), ItemGroup.PRODUCE.id, groupByUser = true
            ),
            ReceiptItem("Rabatt", -50)
        ),
        photo = null,
        isNewPhoto = true,
        itemCount = 3,
        isChanged = true,
        date = LocalDate.of(2026, 10, 26),
        cardAmount = 1234,
        isStandalone = true,
        leftoverRows = listOf(LeftoverRow("Pfand 0,25", Box(5, 6, 7, 8)))
    )

    @Test
    fun writesAndReadsDraftWithAllFields() {
        assertEquals(draft, receiptDraftOf(draft.toJson()))
    }

    @Test
    fun writesAndReadsEachTarget() {
        listOf(ReceiptTarget.Unassigned, ReceiptTarget.Waiting(null), ReceiptTarget.Waiting(3)).forEach { target ->
            assertEquals(target, receiptDraftOf(draft.copy(target = target).toJson())?.target)
        }
    }

    @Test
    fun writesAndReadsDraftWithoutOptionalFields() {
        val minimal = ReceiptDraft(ReceiptTarget.Waiting(null), 0, null, null, emptyList(), null, isNewPhoto = false)
        assertEquals(minimal, receiptDraftOf(minimal.toJson()))
    }

    @Test
    fun unreadableDraftIsNull() {
        assertNull(receiptDraftOf("{}"))
        assertNull(receiptDraftOf("no json"))
    }
}
