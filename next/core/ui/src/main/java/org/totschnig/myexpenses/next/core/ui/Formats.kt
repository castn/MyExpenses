package org.totschnig.myexpenses.next.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/** Dates in the style of the locale, e.g. "05.10.2026" for [FormatStyle.MEDIUM] */
@Composable
fun rememberDateFormatter(style: FormatStyle = FormatStyle.MEDIUM): DateTimeFormatter =
    remember(style) { DateTimeFormatter.ofLocalizedDate(style) }

/** Month and year, e.g. "August 2026" */
@Composable
fun rememberMonthFormatter(): DateTimeFormatter = remember { DateTimeFormatter.ofPattern("LLLL yyyy") }

/** A period from [start] to [end], both included, e.g. "01.08.2026 – 14.08.2026" */
fun DateTimeFormatter.formatRange(start: LocalDate, end: LocalDate) = "${format(start)} – ${format(end)}"

/** A period from [start] until the day before [end], e.g. a month or the time from salary to salary */
@Composable
fun periodLabel(start: LocalDate, end: LocalDate): String = rememberDateFormatter().formatRange(start, end.minusDays(1))

/** Formats amounts in minor units of [currency] with [LocalAmountFormatter] */
@Composable
fun rememberAmountFormat(currency: CurrencyInfo): (Long) -> String {
    val formatter = LocalAmountFormatter.current
    return remember(formatter, currency) { { amount -> formatter.format(amount, currency) } }
}

/**
 * Amounts in minor units of [currency] as short labels of a chart axis, in major units without
 * currency symbol and without trailing zeros, e.g. "150" or "2,5"
 */
@Composable
fun rememberAxisAmountFormat(currency: CurrencyInfo): (Double) -> String = remember(currency) {
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = currency.fractionDigits }
    val divisor = Math.pow(10.0, currency.fractionDigits.toDouble())
    return@remember { amount: Double -> format.format(amount / divisor) }
}
