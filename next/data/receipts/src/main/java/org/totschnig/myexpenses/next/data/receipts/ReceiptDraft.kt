package org.totschnig.myexpenses.next.data.receipts

import android.net.Uri
import org.totschnig.myexpenses.feature.productKey
import org.totschnig.myexpenses.feature.Box
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup

/** Where a scanned receipt goes */
sealed interface ReceiptTarget {
    /** Scanned on its own, its transaction is still to be chosen */
    data object Unassigned : ReceiptTarget

    data class Transaction(val id: Long) : ReceiptTarget

    /** Waits for its transaction to be booked, [id] is null until it is saved */
    data class Waiting(val id: Long?) : ReceiptTarget
}

/**
 * A receipt while it is checked and corrected, before it is saved
 *
 * @param transactionAmount absolute amount of the transaction in minor units, for a receipt
 * that waits for its transaction the amount the bank will book
 * @param photo where the items were read from, null if the attachment was removed
 * @param isNewPhoto the photo still is in the cache of the scanner and needs to be kept on saving
 * @param itemCount number of articles printed on the receipt, only known right after reading it
 */
data class ReceiptDraft(
    val target: ReceiptTarget,
    val transactionAmount: Long,
    val merchant: String?,
    val total: Long?,
    val items: List<ReceiptItem>,
    val photo: Uri?,
    val isNewPhoto: Boolean,
    val itemCount: Int? = null,
    val isChanged: Boolean = false,
    val date: LocalDate? = null,
    val cardAmount: Long? = null,
    /** Opened from a receipt scanned on its own, not from the details of its transaction */
    val isStandalone: Boolean = false,
    /** Rows among the items no item was read from, only known right after reading the receipt */
    val leftoverRows: List<LeftoverRow> = emptyList(),
) {
    /** [leftoverRows] not yet taken as an item by the user */
    val openLeftoverRows: List<LeftoverRow>
        get() = leftoverRows.filter { row -> items.none { it.box?.covers(row.box) == true } }

    /** Where an item read from [box] goes, so that the items keep the order of the receipt */
    fun insertionIndex(box: Box): Int =
        items.indexOfFirst { item -> item.box?.let { it.top > box.top } == true }.takeIf { it >= 0 } ?: items.size

    val check: ReceiptCheck
        get() = ReceiptCheck(ParsedReceipt(merchant, items, total, itemCount), transactionAmount)

    val receipt: Receipt
        get() = Receipt(merchant, total, items, photo, date, cardAmount)

    /** Corrects what was read from the head and foot of the receipt */
    fun withDetails(
        merchant: String? = this.merchant,
        date: LocalDate? = this.date,
        total: Long? = this.total,
        cardAmount: Long? = this.cardAmount,
    ) = copy(merchant = merchant, date = date, total = total, cardAmount = cardAmount, isChanged = true)

    fun update(index: Int, item: ReceiptItem) =
        copy(items = items.toMutableList().apply { set(index, item) }, isChanged = true)

    fun remove(index: Int) = copy(items = items.toMutableList().apply { removeAt(index) }, isChanged = true)

    fun insert(index: Int, item: ReceiptItem) =
        copy(items = items.toMutableList().apply { add(index.coerceIn(0, size), item) }, isChanged = true)

    /** Purchased items without group; discounts and returned deposit are left out */
    val itemsWithoutGroup: List<Int>
        get() = items.indices.filter { items[it].group == null && items[it].amount > 0 }

    /**
     * The user chose [group] for the item at [index]; the same product elsewhere on the receipt
     * follows, unless the user chose a group for it, too
     */
    fun setGroup(index: Int, group: ItemGroup): ReceiptDraft {
        val key = productKey(items[index].name)
        return copy(
            items = items.mapIndexed { i, item ->
                when {
                    i == index -> item.copy(group = group.id, groupByUser = true)
                    !item.groupByUser && productKey(item.name) == key -> item.copy(group = group.id)
                    else -> item
                }
            },
            isChanged = true
        )
    }

    /**
     * Groups found by [ItemGroupClassifier] for items that have none, one per item. Does not count
     * as change, as nothing was decided by the user.
     */
    fun withSuggestedGroups(suggestions: List<ItemGroupSuggestion?>) = copy(items = items.withSuggestedGroups(suggestions))

    companion object {
        fun recognized(target: ReceiptTarget, transactionAmount: Long, receipt: ParsedReceipt, photo: Uri) =
            of(
                target, transactionAmount, receipt.toReceipt().copy(photo = photo), isNewPhoto = true,
                receipt.itemCount, receipt.leftoverRows
            )

        fun saved(target: ReceiptTarget, transactionAmount: Long, receipt: Receipt) =
            of(target, transactionAmount, receipt, isNewPhoto = false)

        fun of(
            target: ReceiptTarget,
            transactionAmount: Long,
            receipt: Receipt,
            isNewPhoto: Boolean,
            itemCount: Int? = null,
            leftoverRows: List<LeftoverRow> = emptyList(),
        ) = ReceiptDraft(
            target, transactionAmount, receipt.merchant, receipt.total, receipt.items,
            receipt.photo, isNewPhoto, itemCount, date = receipt.date, cardAmount = receipt.cardAmount,
            leftoverRows = leftoverRows
        )
    }
}

/** Groups found by [ItemGroupClassifier], one per item, for the items that have none */
fun List<ReceiptItem>.withSuggestedGroups(suggestions: List<ItemGroupSuggestion?>) = mapIndexed { i, item ->
    val suggestion = suggestions.getOrNull(i)
    if (item.group == null && !item.groupByUser && suggestion != null) item.copy(group = suggestion.group.id) else item
}

/**
 * Text of amount fields: minor units shown with [decimalSeparator], read back with comma or dot
 */
class AmountInput(private val fractionDigits: Int, private val decimalSeparator: Char) {

    fun format(minor: Long): String =
        BigDecimal.valueOf(minor, fractionDigits).toPlainString().replace('.', decimalSeparator)

    /** null if [text] is no amount */
    fun parse(text: String): Long? = text.trim().takeIf { it.isNotEmpty() }
        ?.replace(',', '.')
        ?.toBigDecimalOrNull()
        ?.setScale(fractionDigits, RoundingMode.HALF_UP)
        ?.unscaledValue()
        ?.toLong()

    fun formatQuantity(quantity: BigDecimal): String =
        quantity.stripTrailingZeros().toPlainString().replace('.', decimalSeparator)

    /** null if [text] is no positive quantity */
    fun parseQuantity(text: String): BigDecimal? = text.trim().replace(',', '.')
        .toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    /** Amount of [quantity] times [unitPrice], rounded to minor units */
    fun times(quantity: BigDecimal, unitPrice: Long): Long =
        (quantity * BigDecimal(unitPrice)).setScale(0, RoundingMode.HALF_UP).toLong()
}

/** The centre of [other] lies in this box, e.g. a row in the box of an item read from it */
internal fun Box.covers(other: Box): Boolean {
    val x = (other.left + other.right) / 2
    val y = (other.top + other.bottom) / 2
    return x in left..right && y in top..bottom
}
