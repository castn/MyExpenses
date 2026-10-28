package org.totschnig.myexpenses.next.core.model.receipts

/**
 * What other features know about scanned receipts, e.g. saving tips
 *
 * @param items of all scanned receipts by transaction
 * @param canScan whether receipts can be scanned at all, so that other features offer what needs them
 */
data class ReceiptFacts(val items: Map<Long, List<ScannedItem>>, val canScan: Boolean = true) {
    companion object {
        /** Without receipts, e.g. while the area of receipts is left out: nothing offers to scan one */
        val None = ReceiptFacts(emptyMap(), canScan = false)
    }
}

/**
 * An item of a scanned receipt
 *
 * @param amount in minor units
 * @param group id of its [ItemGroup], null if unknown
 */
data class ScannedItem(val name: String, val amount: Long, val group: String? = null)
