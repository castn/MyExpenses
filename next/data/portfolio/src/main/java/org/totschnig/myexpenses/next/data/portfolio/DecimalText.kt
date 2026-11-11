package org.totschnig.myexpenses.next.data.portfolio

import java.math.BigDecimal

private val SEPARATORS = charArrayOf('.', ',')

/**
 * A number as typed, e.g. a quantity or a price, which may have more decimals than an amount. With
 * both separators the last one is the decimal one ("1.234,5"); a single separator that occurs once
 * is the decimal one ("0,0125", "1.5"), one that occurs more than once groups thousands
 * ("1.234.567"). Null if it is no number.
 */
fun parseDecimalText(text: String): BigDecimal? {
    val trimmed = text.filterNot { it.isWhitespace() }
    if (trimmed.isEmpty() || !trimmed.all { it.isDigit() || it in SEPARATORS }) return null
    val separators = trimmed.filter { it in SEPARATORS }
    val hasDecimal = separators.toSet().size > 1 || separators.length == 1
    val decimalAt = if (hasDecimal) trimmed.indexOfLast { it in SEPARATORS } else trimmed.length
    val whole = trimmed.take(decimalAt).filter { it.isDigit() }.ifEmpty { "0" }
    val fraction = trimmed.drop(decimalAt + 1).filter { it.isDigit() }.ifEmpty { "0" }
    if (trimmed.none { it.isDigit() }) return null
    val value = BigDecimal("$whole.$fraction").stripTrailingZeros()
    return if (value.scale() < 0) value.setScale(0) else value
}

/** [value] as typed in a form: without grouping and trailing zeros */
fun formatDecimalText(value: BigDecimal, decimalSeparator: Char): String =
    value.stripTrailingZeros().toPlainString().replace('.', decimalSeparator)

/** [amount] in minor units as typed in a form */
fun formatMinorText(amount: Long, fractionDigits: Int, decimalSeparator: Char): String =
    formatDecimalText(BigDecimal.valueOf(amount, fractionDigits), decimalSeparator)
