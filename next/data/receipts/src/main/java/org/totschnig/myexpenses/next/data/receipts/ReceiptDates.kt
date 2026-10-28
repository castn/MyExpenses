package org.totschnig.myexpenses.next.data.receipts

import java.time.DateTimeException
import java.time.LocalDate

/**
 * Reads the date of the purchase from the rows of a receipt: "11.10.26", "11.10.2026",
 * "11/10/2026", "2026-10-11" or with the name of the month, "11. Okt. 2026". Recognition may add
 * spaces around the separators ("11 .10. 2026"). The first valid date counts; masked dates of
 * anonymized receipts ("00.00.0000") are none.
 */
internal class ReceiptDates(private val vocabulary: ReceiptVocabulary) {

    fun dateOf(rows: List<String>): LocalDate? = rows.firstNotNullOfOrNull(::dateIn)

    fun dateIn(text: String): LocalDate? =
        NUMERIC.findAll(text).firstNotNullOfOrNull { match ->
            val (day, month, year) = match.destructured
            date(year, month.toInt(), day.toInt())
        } ?: ISO.findAll(text).firstNotNullOfOrNull { match ->
            val (year, month, day) = match.destructured
            date(year, month.toInt(), day.toInt())
        } ?: WITH_MONTH.findAll(text).firstNotNullOfOrNull { match ->
            val (day, name, year) = match.destructured
            vocabulary.monthOf(name)?.let { date(year, it, day.toInt()) }
        }

    private fun date(year: String, month: Int, day: Int): LocalDate? {
        val fullYear = when (year.length) {
            2 -> 2000 + year.toInt()
            4 -> year.toInt()
            else -> return null
        }
        if (fullYear !in 2000..2099) return null
        return try {
            LocalDate.of(fullYear, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private companion object {
        val NUMERIC = Regex("""(?<![\d.,])(\d{1,2})\s?[./-]\s?(\d{1,2})\s?[./-]\s?(\d{4}|\d{2})(?![\d,]|\.\d)""")
        val ISO = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
        val WITH_MONTH = Regex("""(?<!\d)(\d{1,2})\.?\s*(\p{L}{3,})\.?\s+(\d{4}|\d{2})(?!\d)""")
    }
}
