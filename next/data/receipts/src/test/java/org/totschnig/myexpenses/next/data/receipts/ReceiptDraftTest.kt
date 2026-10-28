package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup

class ReceiptDraftTest {

    private val draft = ReceiptDraft(
        target = ReceiptTarget.Transaction(1),
        transactionAmount = 400,
        merchant = "REWE",
        total = 400,
        items = listOf(ReceiptItem("BROT", 249), ReceiptItem("MILCH", 119), ReceiptItem("FEHLER", 30)),
        photo = null,
        isNewPhoto = false
    )

    @Test
    fun correctionsAreReflectedInTheCheck() {
        assertEquals(-2L, draft.check.itemsDifference)
        val corrected = draft.update(2, ReceiptItem("BUTTER", 32))
        assertTrue(corrected.check.isConsistent)
        assertTrue(corrected.isChanged)
        assertFalse(draft.isChanged)
    }

    @Test
    fun suggestedGroupsFillOnlyMissingOnes() {
        val withGroups = draft.copy(items = draft.items.mapIndexed { i, item -> if (i == 0) item.copy(group = "other", groupByUser = true) else item })
            .withSuggestedGroups(
                listOf(
                    ItemGroupSuggestion(ItemGroup.BAKERY, ItemGroupSource.DICTIONARY),
                    ItemGroupSuggestion(ItemGroup.DAIRY, ItemGroupSource.DICTIONARY),
                    null
                )
            )
        assertEquals(listOf("other", "dairy", null), withGroups.items.map { it.group })
        assertEquals(listOf(2), withGroups.itemsWithoutGroup)
        // nothing decided by the user, so closing does not ask
        assertFalse(withGroups.isChanged)
    }


    @Test
    fun chosenGroupAppliesToSameProduct() {
        val receipt = draft.copy(
            items = listOf(
                ReceiptItem("Milch 1,5%", 119), ReceiptItem("MILCH 1,5 %", 119),
                ReceiptItem("milch 1.5", 119, group = "other", groupByUser = true), ReceiptItem("Rabatt", -30)
            )
        )
        val chosen = receipt.setGroup(0, ItemGroup.DAIRY)
        assertEquals(listOf("dairy", "dairy", "other", null), chosen.items.map { it.group })
        assertEquals(listOf(true, false, true, false), chosen.items.map { it.groupByUser })
        assertTrue(chosen.isChanged)
        // discounts need no group
        assertEquals(emptyList<Int>(), chosen.itemsWithoutGroup)
    }

    @Test
    fun removeAndUndo() {
        val removed = draft.remove(1)
        assertEquals(listOf("BROT", "FEHLER"), removed.items.map { it.name })
        assertEquals(draft.items, removed.insert(1, draft.items[1]).items)
    }

    @Test
    fun insertBeyondTheEndAppends() {
        assertEquals("NEU", draft.insert(10, ReceiptItem("NEU", 1)).items.last().name)
    }

    private val input = AmountInput(fractionDigits = 2, decimalSeparator = ',')

    @Test
    fun amounts() {
        assertEquals("2,29", input.format(229))
        assertEquals("-0,25", input.format(-25))
        assertEquals("0,05", input.format(5))
        assertEquals(229L, input.parse("2,29"))
        assertEquals(229L, input.parse(" 2.29 "))
        assertEquals(200L, input.parse("2"))
        assertEquals(-25L, input.parse("-0,25"))
        assertNull(input.parse(""))
        assertNull(input.parse("abc"))
    }

    @Test
    fun quantities() {
        assertEquals("3", input.formatQuantity(BigDecimal("3.0")))
        assertEquals("0,512", input.formatQuantity(BigDecimal("0.512")))
        assertEquals(0, BigDecimal("0.512").compareTo(input.parseQuantity("0,512")))
        assertNull(input.parseQuantity("0"))
        assertEquals(153L, input.times(BigDecimal("0.512"), 299))
        assertEquals(177L, input.times(BigDecimal("3"), 59))
    }

    @Test
    fun correctedDetailsAreSavedAndChecked() {
        val corrected = draft.withDetails(merchant = "REWE Markt", date = java.time.LocalDate.of(2026, 10, 9), total = 398)
        assertTrue(corrected.isChanged)
        assertEquals(0L, corrected.check.itemsDifference)
        assertEquals(-2L, corrected.check.totalDifference)
        assertEquals("REWE Markt", corrected.receipt.merchant)
        assertEquals(java.time.LocalDate.of(2026, 10, 9), corrected.receipt.date)
    }

    @Test
    fun rowTakenAsItemGoesInReceiptOrderAndIsNoLongerOpen() {
        fun box(row: Int) = org.totschnig.myexpenses.feature.Box(0, row * 100, 1000, row * 100 + 80)
        val missed = LeftoverRow("BUTTER 1,99 A", box(1))
        val withRows = draft.copy(
            items = listOf(ReceiptItem("BROT", 249, box = box(0)), ReceiptItem("MILCH", 119, box = box(2))),
            leftoverRows = listOf(missed)
        )
        assertEquals(listOf(missed), withRows.openLeftoverRows)
        val item = ReceiptParser.itemOf(listOf(missed))
        val index = withRows.insertionIndex(item.box!!)
        assertEquals(1, index)
        val taken = withRows.insert(index, item)
        assertEquals(listOf("BROT", "BUTTER", "MILCH"), taken.items.map { it.name })
        assertTrue(taken.openLeftoverRows.isEmpty())
    }
}
