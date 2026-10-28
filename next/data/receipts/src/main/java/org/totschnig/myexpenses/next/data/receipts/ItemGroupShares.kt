package org.totschnig.myexpenses.next.data.receipts

import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup


/**
 * Sum of the items of one group on a receipt
 *
 * @param group null for the items without group
 * @param itemCount purchased items, discounts are only part of [amount]
 * @param share of the sum of all [ItemGroupShare]s of the receipt
 */
data class ItemGroupShare(val group: ItemGroup?, val amount: Long, val itemCount: Int, val share: Float)

/**
 * What a purchase went to: the largest group first, items without group last. Discounts count
 * for the group they belong to; groups that add up to nothing or less, e.g. deposit that was
 * returned, are left out.
 */
fun itemGroupShares(items: List<ReceiptItem>): List<ItemGroupShare> {
    val sums = items.groupBy { ItemGroup.ofId(it.group) }
        .mapValues { (_, items) -> items.sumOf { it.amount } to items.count { it.amount > 0 } }
        .filterValues { (amount, _) -> amount > 0 }
    val total = sums.values.sumOf { it.first }
    return sums.map { (group, sum) -> ItemGroupShare(group, sum.first, sum.second, sum.first.toFloat() / total) }
        .sortedWith(compareBy<ItemGroupShare> { it.group == null }.thenByDescending { it.amount })
}
