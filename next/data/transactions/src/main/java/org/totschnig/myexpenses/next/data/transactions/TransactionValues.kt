package org.totschnig.myexpenses.next.data.transactions

import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue

/**
 * What can be entered or changed of a transaction by hand: an expense or income of one account,
 * no transfer, no split
 *
 * @param amount in minor units, negative for an expense
 * @param date seconds since the epoch, like in the database
 * @param valueDate seconds since the epoch, moves with [date] while both are the same day
 */
data class EditValues(
    val accountId: Long,
    val amount: Long,
    val date: Long,
    val valueDate: Long,
    val payee: String?,
    val categoryId: Long?,
    val tagIds: List<Long>,
    val methodId: Long?,
    val comment: String?
) : Serializable {
    private companion object {
        private const val serialVersionUID = 2L
    }
}

/**
 * A transaction as loaded for changing it
 *
 * @param categoryPath of [EditValues.categoryId], to show it
 * @param accountType id of the type of its account, the payment methods depend on it
 * @param isAmountEditable false if the amount has an original or equivalent amount in another
 * currency, which a change would leave wrong
 * @param isImported comes from the bank import, a change of amount, date or payee no longer
 * matches what the bank reports
 */
data class EditableTransaction(
    val id: Long,
    val values: EditValues,
    val categoryPath: String?,
    val fractionDigits: Int,
    val accountType: Long?,
    val isAmountEditable: Boolean,
    val isImported: Boolean
)

/** A payment method to choose from */
data class MethodOption(val id: Long, val label: String)

/**
 * An open account a transaction can be entered in
 *
 * @param currency code of its currency, a transaction keeps its currency when it moves
 * @param type id of the type of the account, the payment methods depend on it
 */
data class AccountOption(
    val id: Long,
    val label: String,
    val currency: String,
    val currencySymbol: String,
    val fractionDigits: Int,
    val type: Long?
)

/** Which values a change in the details changed, for the text of its undo */
enum class ChangedField { COMMENT, DATE, METHOD, ALL }

/** A change that was saved and can be undone: back from [saved] to [previous] */
data class Undo(
    val id: Long,
    val previous: EditValues,
    val saved: EditValues,
    val field: ChangedField
)

/**
 * The amount as typed, without sign: the last separator followed by at most [fractionDigits]
 * digits is the decimal one, the others group thousands, so that "1.234" is a thousand and
 * "12,5" twelve and a half. Null if it is no amount.
 */
fun parseAmountText(text: String, fractionDigits: Int): Long? {
    val trimmed = text.filterNot { it.isWhitespace() || it == ' ' }
    if (trimmed.isEmpty() || !trimmed.all { it.isDigit() || it in SEPARATORS }) return null
    val lastSeparator = trimmed.indexOfLast { it in SEPARATORS }
    val decimals = if (lastSeparator >= 0) trimmed.length - lastSeparator - 1 else 0
    val isDecimal = lastSeparator >= 0 && decimals in 1..fractionDigits
    val whole = (if (isDecimal) trimmed.substring(0, lastSeparator) else trimmed).filter {
        it.isDigit()
    }
    val fraction = if (isDecimal) trimmed.substring(lastSeparator + 1) else ""
    if (whole.isEmpty() && fraction.isEmpty()) return null
    return BigDecimal("${whole.ifEmpty { "0" }}.${fraction.ifEmpty { "0" }}")
        .movePointRight(fractionDigits)
        .longValueExact()
}

/** [amount] in minor units as typed in a form: without sign and grouping */
fun formatAmountText(amount: Long, fractionDigits: Int, decimalSeparator: Char): String =
    BigDecimal.valueOf(
        amount.absoluteValue,
        fractionDigits
    ).toPlainString().replace('.', decimalSeparator)

/**
 * [EditValues] moved to [day], keeping the time; the value date moves along if it was the same day
 */
fun EditValues.withDay(day: LocalDate, zone: ZoneId): EditValues {
    val moved = date.onDay(day, zone)
    return copy(
        date = moved,
        valueDate = if (valueDate.toDay(zone) ==
            date.toDay(zone)
        ) {
            valueDate.onDay(day, zone)
        } else {
            valueDate
        }
    )
}

fun Long.toDay(zone: ZoneId): LocalDate = Instant.ofEpochSecond(this).atZone(zone).toLocalDate()

internal fun Long.onDay(day: LocalDate, zone: ZoneId): Long {
    val time = Instant.ofEpochSecond(this).atZone(zone).toLocalTime()
    return day.atTime(time).atZone(zone).toEpochSecond()
}

/** Whether amount, day or payee of [other] differ from these values, what the bank reports */
fun EditValues.differsInBankFields(other: EditValues, zone: ZoneId) =
    amount != other.amount || date.toDay(zone) != other.date.toDay(zone) || payee != other.payee

/** Decimal and grouping separators an amount may be typed with */
private const val SEPARATORS = ",."
