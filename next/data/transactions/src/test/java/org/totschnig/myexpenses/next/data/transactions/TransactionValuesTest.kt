package org.totschnig.myexpenses.next.data.transactions

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionValuesTest {

    private val zone: ZoneId = ZoneOffset.UTC

    @Test
    fun readsDecimalAndGroupingSeparators() {
        assertEquals(1250L, parseAmountText("12,5", 2))
        assertEquals(1250L, parseAmountText("12.50", 2))
        assertEquals(123400L, parseAmountText("1.234", 2))
        assertEquals(123456L, parseAmountText("1.234,56", 2))
        assertEquals(123456L, parseAmountText("1,234.56", 2))
        assertEquals(4200L, parseAmountText(" 42 ", 2))
        assertEquals(42L, parseAmountText("42", 0))
        assertEquals(50L, parseAmountText(",5", 2))
    }

    @Test
    fun rejectsWhatIsNoAmount() {
        assertNull(parseAmountText("", 2))
        assertNull(parseAmountText("abc", 2))
        assertNull(parseAmountText("-12", 2))
        assertNull(parseAmountText(",", 2))
    }

    @Test
    fun formatsWithoutSignAndGrouping() {
        assertEquals("1234,56", formatAmountText(-123456, 2, ','))
        assertEquals("0.05", formatAmountText(5, 2, '.'))
        assertEquals("42", formatAmountText(42, 0, ','))
    }

    @Test
    fun movesDateKeepingTimeAndValueDateOfTheSameDay() {
        val date = LocalDate.of(2026, 3, 1).atTime(14, 30).toEpochSecond(ZoneOffset.UTC)
        val values = values(date = date, valueDate = date)
        val moved = values.withDay(LocalDate.of(2026, 3, 5), zone)
        val expected = LocalDate.of(2026, 3, 5).atTime(14, 30).toEpochSecond(ZoneOffset.UTC)
        assertEquals(expected, moved.date)
        assertEquals(expected, moved.valueDate)
    }

    @Test
    fun keepsValueDateOfAnotherDay() {
        val date = LocalDate.of(2026, 3, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val valueDate = LocalDate.of(2026, 3, 3).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val values = values(date = date, valueDate = valueDate)
        assertEquals(valueDate, values.withDay(LocalDate.of(2026, 3, 5), zone).valueDate)
    }

    @Test
    fun onlyAmountDayAndPayeeDifferFromTheBank() {
        val values = transaction().values
        assertEquals(
            false,
            values.differsInBankFields(values.copy(comment = "Notiz", methodId = 3), zone)
        )
        assertEquals(
            false,
            values.differsInBankFields(values.copy(categoryId = 7, tagIds = listOf(1)), zone)
        )
        assertEquals(false, values.differsInBankFields(values.copy(date = values.date + 60), zone))
        assertEquals(true, values.differsInBankFields(values.copy(amount = -1), zone))
        assertEquals(true, values.differsInBankFields(values.copy(payee = "Lidl"), zone))
        assertEquals(
            true,
            values.differsInBankFields(values.withDay(LocalDate.of(2026, 3, 2), zone), zone)
        )
    }
}

internal fun values(
    amount: Long = -4290,
    date: Long = LocalDate.of(2026, 3, 1).atTime(12, 0).toEpochSecond(ZoneOffset.UTC),
    valueDate: Long = date
) = EditValues(
    accountId = 1,
    amount = amount,
    date = date,
    valueDate = valueDate,
    payee = "REWE",
    categoryId = null,
    tagIds = emptyList(),
    methodId = null,
    comment = null
)

internal fun transaction(amount: Long = -4290) = EditableTransaction(
    id = 1,
    values = values(amount),
    categoryPath = null,
    fractionDigits = 2,
    accountType = 1,
    isAmountEditable = true,
    isImported = false
)
