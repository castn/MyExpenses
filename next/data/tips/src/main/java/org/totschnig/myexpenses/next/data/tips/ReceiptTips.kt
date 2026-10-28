package org.totschnig.myexpenses.next.data.tips

import org.totschnig.myexpenses.next.data.merchants.supermarketOf
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.absoluteValue

/*
 * Tips from the items of scanned receipts. Only some purchases are scanned, so the tips compare
 * shares within the scanned receipts, or name what the receipts show, never totals per month.
 */

/** A purchase with the items of its receipt */
internal class ScannedPurchase(val transaction: ContractTransaction, val items: List<ScannedItem>) {
    val total: Long get() = items.sumOf { it.amount }

    fun amountOf(group: ItemGroup) = items.filter { it.group == group.id }.sumOf { it.amount }
}

/**
 * Debits of [spending] that have a receipt
 *
 * @param receipts items by transaction
 */
internal fun scannedPurchases(spending: List<ContractTransaction>, receipts: Map<Long, List<ScannedItem>>) =
    spending.mapNotNull { transaction -> receipts[transaction.id]?.let { ScannedPurchase(transaction, it) } }

/**
 * A kind of goods that is easy to buy less of, e.g. sweets, took a clearly larger share of the
 * scanned purchases in the last [RECENT_MONTHS] months than in the year before
 *
 * @param recentShare / [usualShare] of the sum of the scanned purchases, from 0 to 1
 * @param transactions the recent purchases with items of [group]
 */
data class ItemGroupRising(
    val group: ItemGroup,
    val recentShare: Float,
    val usualShare: Float,
    override val yearlyAmount: Long,
    val transactions: List<ContractTransaction>,
    val month: YearMonth,
) : SavingTip {
    override val id: String get() = "$ID_PREFIX${group.id}"

    /** Comes back in another month in which the share is still high */
    override val facts: Set<String> get() = setOf(month.toString())
    override val transactionGroups get() = mapOf(id to transactions)

    companion object {
        const val ID_PREFIX = "ITEM_GROUP_RISING:"
        const val RECENT_MONTHS = 3L

        /** Groups that are a tip when they rise; necessities like produce are none */
        val WATCHED = listOf(ItemGroup.SWEETS_SNACKS, ItemGroup.ALCOHOL, ItemGroup.TOBACCO)

        /** Scanned purchases needed in the recent months and in the year before */
        const val MIN_RECENT_PURCHASES = 4
        const val MIN_USUAL_PURCHASES = 6
        const val MIN_FACTOR = 1.5f

        /** Percentage points the share must have grown by */
        const val MIN_POINTS = 0.05f

        /** A purchase on [date] counts to the recent months */
        fun isRecent(date: LocalDate, today: LocalDate) = !date.isAfter(today) && date.isAfter(today.minusMonths(RECENT_MONTHS))

        /** A purchase on [date] counts to the year before the recent months, the usual share */
        fun isUsual(date: LocalDate, today: LocalDate): Boolean {
            val recentStart = today.minusMonths(RECENT_MONTHS)
            return !date.isAfter(recentStart) && date.isAfter(recentStart.minusYears(1))
        }

        /** Enough scanned purchases in both periods to compare them */
        fun hasEnough(recent: Int, usual: Int) = recent >= MIN_RECENT_PURCHASES && usual >= MIN_USUAL_PURCHASES
    }
}

/**
 * More deposit paid than returned on the scanned receipts of the last year
 *
 * @param transactions the purchases with deposit paid
 */
data class DepositNotReturned(
    val paid: Long,
    val returned: Long,
    val transactions: List<ContractTransaction>,
) : SavingTip {
    override val id: String get() = ID
    override val yearlyAmount: Long get() = paid - returned
    override val facts: Set<String> get() = setOf("level:${level(yearlyAmount)}")
    override val transactionGroups get() = mapOf(ID to transactions)

    companion object {
        const val ID = "DEPOSIT_NOT_RETURNED"
        const val MIN_AMOUNT = 1000L
    }
}

/**
 * Drugstore goods bought at supermarket chains within the last year, according to the scanned receipts
 *
 * @param transactions the purchases with such goods
 */
data class DrugstoreInSupermarkets(
    override val yearlyAmount: Long,
    val itemCount: Int,
    val transactions: List<ContractTransaction>,
) : SavingTip {
    override val id: String get() = ID
    override val facts: Set<String> get() = setOf("level:${level(yearlyAmount)}")
    override val transactionGroups get() = mapOf(ID to transactions)

    companion object {
        const val ID = "DRUGSTORE_IN_SUPERMARKETS"
        const val MIN_AMOUNT = 2000L
        const val MIN_ITEMS = 5
    }
}

private fun List<ScannedPurchase>.lastYear(today: LocalDate) = filter { it.transaction.date.isAfter(today.minusYears(1)) }

/**
 * Compares the share of each of [ItemGroupRising.WATCHED] in the last [ItemGroupRising.RECENT_MONTHS]
 * months with the year before
 */
internal fun itemGroupsRising(purchases: List<ScannedPurchase>, today: LocalDate): List<ItemGroupRising> {
    val recent = purchases.filter { ItemGroupRising.isRecent(it.transaction.date, today) }
    val usual = purchases.filter { ItemGroupRising.isUsual(it.transaction.date, today) }
    if (!ItemGroupRising.hasEnough(recent.size, usual.size)) return emptyList()
    fun List<ScannedPurchase>.shareOf(group: ItemGroup): Float {
        val total = sumOf { it.total }
        return if (total <= 0) 0f else sumOf { it.amountOf(group) }.toFloat() / total
    }
    return ItemGroupRising.WATCHED.mapNotNull { group ->
        val recentShare = recent.shareOf(group)
        val usualShare = usual.shareOf(group)
        if (recentShare < usualShare * ItemGroupRising.MIN_FACTOR || recentShare - usualShare < ItemGroupRising.MIN_POINTS) {
            return@mapNotNull null
        }
        ItemGroupRising(
            group = group,
            recentShare = recentShare,
            usualShare = usualShare,
            yearlyAmount = purchases.lastYear(today).sumOf { it.amountOf(group) },
            transactions = recent.filter { it.amountOf(group) > 0 }.map { it.transaction },
            month = YearMonth.from(today)
        )
    }
}

internal fun depositNotReturned(purchases: List<ScannedPurchase>, today: LocalDate): DepositNotReturned? {
    val deposits = purchases.lastYear(today)
        .map { purchase -> purchase to purchase.items.filter { it.group == ItemGroup.DEPOSIT.id } }
        .filter { (_, items) -> items.isNotEmpty() }
    val paid = deposits.sumOf { (_, items) -> items.filter { it.amount > 0 }.sumOf { it.amount } }
    val returned = deposits.sumOf { (_, items) -> items.filter { it.amount < 0 }.sumOf { it.amount.absoluteValue } }
    if (paid - returned < DepositNotReturned.MIN_AMOUNT) return null
    return DepositNotReturned(
        paid, returned,
        deposits.filter { (_, items) -> items.any { it.amount > 0 } }.map { it.first.transaction }
    )
}

internal fun drugstoreInSupermarkets(purchases: List<ScannedPurchase>, today: LocalDate): DrugstoreInSupermarkets? {
    val withDrugstore = purchases.lastYear(today)
        .filter { supermarketOf(it.transaction.payeeName) != null && it.amountOf(ItemGroup.DRUGSTORE) > 0 }
    val amount = withDrugstore.sumOf { it.amountOf(ItemGroup.DRUGSTORE) }
    val itemCount = withDrugstore.sumOf { purchase ->
        purchase.items.count { it.group == ItemGroup.DRUGSTORE.id && it.amount > 0 }
    }
    if (amount < DrugstoreInSupermarkets.MIN_AMOUNT || itemCount < DrugstoreInSupermarkets.MIN_ITEMS) return null
    return DrugstoreInSupermarkets(amount, itemCount, withDrugstore.map { it.transaction })
}
