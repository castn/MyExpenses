package org.totschnig.myexpenses.model2

import android.net.Uri
import org.totschnig.myexpenses.feature.Box
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Items of a receipt as context of a transaction. Amounts in minor units.
 */
data class Receipt(
    val merchant: String?,
    val total: Long?,
    val items: List<ReceiptItem>,
    /** Attachment of the transaction that shows the receipt, the photo itself while it waits for one */
    val photo: Uri? = null,
    /** Day of the purchase printed on the receipt */
    val date: LocalDate? = null,
    /** Amount paid by card, differs from [total] by cash back or coupons */
    val cardAmount: Long? = null,
)

/**
 * One purchased article, negative amount for discounts and returned deposit.
 */
data class ReceiptItem(
    val name: String,
    val amount: Long,
    val quantity: BigDecimal = BigDecimal.ONE,
    /** null for pieces, otherwise e.g. "kg" */
    val unit: String? = null,
    val unitPrice: Long? = null,
    val taxClass: String? = null,
    /** Position on the photo, null for items added by hand */
    val box: Box? = null,
    /** Kind of goods, id of ItemGroup in ui_next, null if unknown */
    val group: String? = null,
    /** The user chose [group], so it is learned and never overwritten */
    val groupByUser: Boolean = false
)
