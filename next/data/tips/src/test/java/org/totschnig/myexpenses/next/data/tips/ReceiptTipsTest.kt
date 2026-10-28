package org.totschnig.myexpenses.next.data.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup
import java.time.LocalDate
import java.time.YearMonth

class ReceiptTipsTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 3)
    private var nextId = 1L

    private fun item(group: ItemGroup?, amount: Long) = ScannedItem(group?.name ?: "?", amount, group = group?.id)

    /** A scanned purchase whose transaction amount is the sum of [items] */
    private fun purchase(daysAgo: Long, vararg items: ScannedItem, payee: String = "REWE Markt") = ScannedPurchase(
        ContractTransaction(nextId++, today.minusDays(daysAgo), -items.sumOf { it.amount }, 1, payeeName = payee),
        items.toList()
    )

    /** Weekly purchases of 50 €, of which [sweets] are sweets */
    private fun weekly(fromDaysAgo: Long, toDaysAgo: Long, sweets: Long) = (fromDaysAgo downTo toDaysAgo step 7).map {
        purchase(it, item(ItemGroup.PRODUCE, 5000 - sweets), item(ItemGroup.SWEETS_SNACKS, sweets))
    }

    @Test
    fun scannedPurchasesAreTheDebitsWithReceipt() {
        val withReceipt = ContractTransaction(nextId++, today, -300, 1)
        val without = ContractTransaction(nextId++, today, -500, 1)
        val purchases = scannedPurchases(listOf(withReceipt, without), mapOf(withReceipt.id to listOf(item(ItemGroup.DAIRY, 300))))
        assertEquals(listOf(withReceipt), purchases.map { it.transaction })
    }

    @Test
    fun risingShareOfSweets() {
        val purchases = weekly(360, 95, sweets = 250) + weekly(85, 1, sweets = 750)
        val tip = itemGroupsRising(purchases, today).single()
        assertEquals(ItemGroup.SWEETS_SNACKS, tip.group)
        assertEquals(0.15f, tip.recentShare, 0.001f)
        assertEquals(0.05f, tip.usualShare, 0.001f)
        assertEquals(purchases.sumOf { it.amountOf(ItemGroup.SWEETS_SNACKS) }, tip.yearlyAmount)
        assertEquals(13, tip.transactions.size)
        assertEquals(setOf(YearMonth.from(today).toString()), tip.facts)
    }

    @Test
    fun sameShareOrTooFewPurchasesGiveNoTip() {
        assertTrue(itemGroupsRising(weekly(360, 1, sweets = 750), today).isEmpty())
        // a few purchases are no trend
        assertTrue(itemGroupsRising(weekly(360, 95, sweets = 250) + weekly(14, 1, sweets = 750), today).isEmpty())
        // produce is no tip, however much it rises
        val produce = (360L downTo 95 step 7).map { purchase(it, item(ItemGroup.PRODUCE, 500), item(ItemGroup.PANTRY, 4500)) } +
                (85L downTo 1 step 7).map { purchase(it, item(ItemGroup.PRODUCE, 4500), item(ItemGroup.PANTRY, 500)) }
        assertTrue(itemGroupsRising(produce, today).isEmpty())
    }

    @Test
    fun depositPaidButNotReturned() {
        val paid = purchase(30, item(ItemGroup.DRINKS, 2000), item(ItemGroup.DEPOSIT, 1500))
        val partlyReturned = purchase(20, item(ItemGroup.DRINKS, 2000), item(ItemGroup.DEPOSIT, 600), item(ItemGroup.DEPOSIT, -100))
        val tip = depositNotReturned(listOf(paid, partlyReturned, purchase(400, item(ItemGroup.DEPOSIT, 5000))), today)!!
        assertEquals(2100L, tip.paid)
        assertEquals(100L, tip.returned)
        assertEquals(2000L, tip.yearlyAmount)
        assertEquals(listOf(paid.transaction, partlyReturned.transaction), tip.transactions)
        assertNull(depositNotReturned(listOf(purchase(10, item(ItemGroup.DEPOSIT, 900))), today))
    }

    @Test
    fun drugstoreGoodsAtSupermarkets() {
        val supermarket = (1..5L).map { purchase(it * 10, item(ItemGroup.DRUGSTORE, 450), item(ItemGroup.DAIRY, 300)) }
        val drugstore = purchase(5, item(ItemGroup.DRUGSTORE, 5000), payee = "dm-drogerie markt")
        val tip = drugstoreInSupermarkets(supermarket + drugstore, today)!!
        assertEquals(2250L, tip.yearlyAmount)
        assertEquals(5, tip.itemCount)
        assertEquals(supermarket.map { it.transaction }, tip.transactions)
        assertNull(drugstoreInSupermarkets(supermarket.take(4), today))
    }
}
