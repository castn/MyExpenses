package org.totschnig.myexpenses.next.data.transactions

import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What a new transaction starts with, e.g. from a receipt; all of it can be changed in the form
 *
 * @param accountId null for the account used last
 * @param amount without sign, null for an empty field
 * @param day null for today
 */
data class NewTransaction(
    val accountId: Long? = null,
    val isIncome: Boolean = false,
    val amount: BigDecimal? = null,
    val day: LocalDate? = null,
    val payee: String? = null
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * What is typed in the form of a transaction. Kept as typed, so that an amount that cannot be read
 * yet survives, and [Serializable] for the saved state.
 *
 * @param id of the transaction changed, [NEW] for a new one
 * @param isCategorySuggested the category was set from a suggestion, not by the user, so that it
 * follows a change of the payee
 */
data class TransactionDraft(
    val id: Long,
    val accountId: Long,
    val amountText: String,
    val isIncome: Boolean,
    val day: LocalDate,
    val payee: String,
    val categoryId: Long?,
    val categoryPath: String?,
    val tagIds: List<Long>,
    val methodId: Long?,
    val comment: String,
    val isCategorySuggested: Boolean = false
) : Serializable {
    val isNew get() = id == NEW

    companion object {
        const val NEW = 0L
        private const val serialVersionUID = 2L
    }
}

/** The draft for changing [this] */
fun EditableTransaction.toDraft(decimalSeparator: Char, zone: ZoneId) = TransactionDraft(
    id = id,
    accountId = values.accountId,
    amountText = formatAmountText(values.amount, fractionDigits, decimalSeparator),
    isIncome = values.amount > 0,
    day = values.date.toDay(zone),
    payee = values.payee.orEmpty(),
    categoryId = values.categoryId,
    categoryPath = categoryPath,
    tagIds = values.tagIds,
    methodId = values.methodId,
    comment = values.comment.orEmpty()
)

/**
 * The draft for a new transaction in [accountId] with the tags the account gives its transactions
 *
 * @param today the day when [NewTransaction.day] is not given
 */
fun NewTransaction.toDraft(
    accountId: Long,
    tagIds: List<Long>,
    today: LocalDate,
    decimalSeparator: Char
) = TransactionDraft(
    id = TransactionDraft.NEW,
    accountId = accountId,
    amountText = amount?.abs()?.stripTrailingZeros()?.toPlainString()?.replace(
        '.',
        decimalSeparator
    ).orEmpty(),
    isIncome = isIncome,
    day = day ?: today,
    payee = payee.orEmpty(),
    categoryId = null,
    categoryPath = null,
    tagIds = tagIds,
    methodId = null,
    comment = ""
)

/**
 * The draft after "save and new": account, sign, day and payment method stay for the next
 * transaction, as several are often entered from one receipt or one day
 */
fun TransactionDraft.next(tagIds: List<Long>) = copy(
    id = TransactionDraft.NEW,
    amountText = "",
    payee = "",
    categoryId = null,
    categoryPath = null,
    tagIds = tagIds,
    comment = "",
    isCategorySuggested = false
)

/**
 * The values of this draft, null if its amount cannot be read.
 *
 * @param previous the values of the transaction changed, null for a new one. Its time of day is
 * kept; an amount that is not editable stays.
 * @param now the moment a new transaction is saved, its time of day is taken
 */
fun TransactionDraft.toValues(
    previous: EditValues?,
    fractionDigits: Int,
    isAmountEditable: Boolean,
    zone: ZoneId,
    now: Instant
): EditValues? {
    val amount = if (isAmountEditable || previous == null) {
        val typed = parseAmountText(amountText, fractionDigits) ?: return null
        if (isIncome) typed else -typed
    } else {
        previous.amount
    }
    val dated = previous?.withDay(day, zone) ?: run {
        val date = now.epochSecond.onDay(day, zone)
        EditValues(accountId, amount, date, date, null, null, emptyList(), null, null)
    }
    return dated.copy(
        accountId = accountId,
        amount = amount,
        payee = payee.trim().ifEmpty { null },
        categoryId = categoryId,
        tagIds = tagIds,
        methodId = methodId,
        comment = comment.trim().ifEmpty { null }
    )
}
