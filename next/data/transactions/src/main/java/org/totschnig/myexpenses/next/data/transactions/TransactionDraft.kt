package org.totschnig.myexpenses.next.data.transactions

import java.io.Serializable
import java.time.LocalDate
import java.time.ZoneId

/**
 * What is typed in the edit mode of the details. Kept as typed, so that an amount that cannot be
 * read yet survives, and [Serializable] for the saved state.
 */
data class EditDraft(
    val id: Long,
    val amountText: String,
    val isIncome: Boolean,
    val day: LocalDate,
    val payee: String,
    val methodId: Long?,
    val comment: String,
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** The draft of the edit mode for [transaction] */
fun EditableTransaction.toDraft(decimalSeparator: Char, zone: ZoneId) = EditDraft(
    id = id,
    amountText = formatAmountText(values.amount, fractionDigits, decimalSeparator),
    isIncome = values.amount > 0,
    day = values.date.toDay(zone),
    payee = values.payee.orEmpty(),
    methodId = values.methodId,
    comment = values.comment.orEmpty(),
)

/** The values of [draft] applied to [values], null if its amount cannot be read */
fun EditableTransaction.apply(draft: EditDraft, zone: ZoneId): EditValues? {
    val amount = if (isAmountEditable) {
        val typed = parseAmountText(draft.amountText, fractionDigits) ?: return null
        if (draft.isIncome) typed else -typed
    } else values.amount
    return values.withDay(draft.day, zone).copy(
        amount = amount,
        payee = draft.payee.trim().ifEmpty { null },
        methodId = draft.methodId,
        comment = draft.comment.trim().ifEmpty { null },
    )
}
