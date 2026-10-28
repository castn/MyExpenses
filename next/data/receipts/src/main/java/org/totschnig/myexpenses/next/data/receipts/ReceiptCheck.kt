package org.totschnig.myexpenses.next.data.receipts

/**
 * Compares the items with the total of the receipt and the amount of the transaction.
 * All amounts in minor units, [transactionAmount] as absolute value.
 */
data class ReceiptCheck(val receipt: ParsedReceipt, val transactionAmount: Long) {

    /** Items minus the receipt total, or minus the transaction if no total was found */
    val itemsDifference: Long
        get() = receipt.itemsSum - (receipt.total ?: transactionAmount)

    /** Receipt total minus transaction, null if no total was found */
    val totalDifference: Long?
        get() = receipt.total?.let { it - transactionAmount }

    /**
     * Printed number of articles differs from the parsed pieces, so a row was probably missed.
     * Some tills count rows instead of pieces ("5 Pos").
     */
    val countDiffers: Boolean
        get() = receipt.itemCount != null && receipt.itemCount != receipt.pieces &&
                receipt.itemCount != receipt.items.count { it.amount > 0 }

    val isConsistent: Boolean
        get() = itemsDifference == 0L && (totalDifference ?: 0L) == 0L && !countDiffers
}
