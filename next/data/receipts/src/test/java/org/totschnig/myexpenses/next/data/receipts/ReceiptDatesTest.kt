package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptDatesTest {

    private val dates = ReceiptDates(ReceiptVocabulary.GERMAN)

    @Test
    fun numericDates() {
        val date = LocalDate.of(2026, 10, 11)
        listOf(
            "11.10.26 18:42", "11.10.2026", "Datum: 11/10/2026", "11-10-2026", "19:05 11 .10. 2026",
            "11.10. 2026", "2026-10-11T18:42", "BON 4711 11.10.26 KASSE 3"
        ).forEach { assertEquals(it, date, dates.dateIn(it)) }
    }

    @Test
    fun withNameOfMonth() {
        assertEquals(LocalDate.of(2026, 8, 3), dates.dateIn("03 Aug. 2026 14:42"))
        assertEquals(LocalDate.of(2026, 3, 1), dates.dateIn("1. März 2026"))
        assertEquals(LocalDate.of(2026, 3, 1), dates.dateIn("1. Mar 26"))
    }

    @Test
    fun noDates() {
        listOf(
            "00.00.0000 00:00", "00 Aug. 0000", "1.640 14.90", "0,750 kg x 1,79", "32.13.2026", "12:15",
            "Steuer 19,00 % 1,23", "1,32 € x 3", "Posten: 9"
        ).forEach { assertNull(it, dates.dateIn(it)) }
    }

    @Test
    fun firstValidDateOfTheRows() {
        assertEquals(
            LocalDate.of(2026, 10, 9),
            dates.dateOf(listOf("REWE", "00.00.0000", "SUMME 12,15", "09.10.2026 11:00", "10.10.2026"))
        )
    }
}
