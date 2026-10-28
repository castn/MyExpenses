package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup

class ItemGroupSharesTest {

    @Test
    fun largestFirstWithoutGroupLastDiscountsIncluded() {
        val shares = itemGroupShares(
            listOf(
                ReceiptItem("Milch", 358, group = "dairy"),
                ReceiptItem("Preisänderung", -178, group = "dairy"),
                ReceiptItem("Feigen", 312, group = "produce"),
                ReceiptItem("Unbekannt", 500),
                ReceiptItem("Pfand", 25, group = "deposit"),
                ReceiptItem("Leergut", -25, group = "deposit"),
                ReceiptItem("Toilettenpapier", 745, group = "household")
            )
        )
        assertEquals(
            listOf(ItemGroup.HOUSEHOLD to 745L, ItemGroup.PRODUCE to 312L, ItemGroup.DAIRY to 180L, null to 500L),
            shares.map { it.group to it.amount }
        )
        // the discount is no item of its own
        assertEquals(1, shares[2].itemCount)
        assertEquals(1f, shares.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
        assertTrue(shares[0].share > shares[1].share)
    }

    @Test
    fun noItems() {
        assertEquals(emptyList<ItemGroupShare>(), itemGroupShares(emptyList()))
    }
}
