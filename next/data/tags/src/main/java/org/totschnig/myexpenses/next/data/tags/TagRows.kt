package org.totschnig.myexpenses.next.data.tags

import org.totschnig.myexpenses.next.data.categories.CategoryInput
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.data.categories.counterpart
import org.totschnig.myexpenses.next.core.model.ledger.Ledger

/**
 * A debit or credit of the ledger as the analyses of tags read it: refunds and trips are found by
 * it, tags are learned from its counterpart
 *
 * @param learnFrom the counterpart tags are learned from, null if there is none to learn from
 */
data class TagRow(
    val refund: RefundInput,
    val isForeign: Boolean,
    val learnFrom: Counterpart?,
    val tagIds: Set<Long>,
) {
    fun toTripInput() = TripInput(refund.id, refund.date, refund.amount, refund.payeeName, refund.comment, isForeign)

    fun toTagInput() = TagInput(refund.id, learnFrom?.key, learnFrom?.name, tagIds)
}

/** The transactions of [ledger] that can be tagged by the analyses: transfers in the app are no purchases */
fun tagRows(ledger: Ledger): List<TagRow> = ledger.transactions
    .filter { it.transferAccountId == null }
    .map { transaction ->
        with(transaction) {
            TagRow(
                RefundInput(
                    id = id,
                    date = date,
                    amount = amount,
                    accountId = accountId,
                    payeeName = payeeName,
                    comment = comment,
                    counterpart = counterpartKey(payeeId, payeeName, comment)
                ),
                isForeign = isForeign,
                learnFrom = CategoryInput(0, 0, payeeId, payeeName, comment).counterpart(),
                tagIds = tagIds
            )
        }
    }
