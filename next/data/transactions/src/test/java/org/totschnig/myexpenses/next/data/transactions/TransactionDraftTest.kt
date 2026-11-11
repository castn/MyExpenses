package org.totschnig.myexpenses.next.data.transactions

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionDraftTest {

    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 3, 10).atTime(9, 15).toInstant(zone)

    @Test
    fun draftOfATransactionShowsItsValues() {
        val transaction = transaction(amount = -4290).let {
            it.copy(
                values = it.values.copy(
                    categoryId = 5,
                    tagIds = listOf(2),
                    methodId = 3,
                    comment = "Wocheneinkauf"
                ),
                categoryPath = "Lebensmittel"
            )
        }
        assertEquals(
            TransactionDraft(
                id = 1,
                accountId = 1,
                amountText = "42,90",
                isIncome = false,
                day = LocalDate.of(2026, 3, 1),
                payee = "REWE",
                categoryId = 5,
                categoryPath = "Lebensmittel",
                tagIds = listOf(2),
                methodId = 3,
                comment = "Wocheneinkauf"
            ),
            transaction.toDraft(',', zone)
        )
    }

    @Test
    fun appliesDraftWithSignAndTrimmedTexts() {
        val transaction = transaction(amount = -4290)
        val draft =
            transaction.toDraft(
                ',',
                zone
            ).copy(amountText = "50", isIncome = true, payee = "  ", comment = " Pizza ")
        val values = draft.toValues(transaction.values, 2, true, zone, now)!!
        assertEquals(5000L, values.amount)
        assertNull(values.payee)
        assertEquals("Pizza", values.comment)
        // The time of day of the transaction stays
        assertEquals(transaction.values.date, values.date)
    }

    @Test
    fun keepsAmountThatCannotBeEdited() {
        val transaction = transaction(amount = -4290)
        val draft = transaction.toDraft(',', zone).copy(amountText = "x")
        assertEquals(-4290L, draft.toValues(transaction.values, 2, false, zone, now)!!.amount)
    }

    @Test
    fun noValuesForAnAmountThatCannotBeRead() {
        val transaction = transaction(amount = -4290)
        assertNull(
            transaction.toDraft(
                ',',
                zone
            ).copy(amountText = "4x").toValues(transaction.values, 2, true, zone, now)
        )
        assertNull(
            NewTransaction().toDraft(
                1,
                emptyList(),
                LocalDate.of(2026, 3, 10),
                ','
            ).toValues(null, 2, true, zone, now)
        )
    }

    @Test
    fun newDraftStartsWithPrefillAndTheTagsOfTheAccount() {
        val draft = NewTransaction(
            accountId = 4,
            isIncome = false,
            amount = BigDecimal("12.50"),
            payee = "Bäcker"
        )
            .toDraft(4, listOf(9), LocalDate.of(2026, 3, 10), ',')
        assertEquals(TransactionDraft.NEW, draft.id)
        assertEquals("12,5", draft.amountText)
        assertEquals(LocalDate.of(2026, 3, 10), draft.day)
        assertEquals("Bäcker", draft.payee)
        assertEquals(listOf(9L), draft.tagIds)
        assertNull(draft.categoryId)
    }

    @Test
    fun newTransactionGetsTheTimeOfSavingOnItsDay() {
        val draft = NewTransaction(day = LocalDate.of(2026, 3, 8), amount = BigDecimal("3"))
            .toDraft(4, emptyList(), LocalDate.of(2026, 3, 10), ',')
        val values = draft.toValues(null, 2, true, zone, now)!!
        val expected = LocalDate.of(2026, 3, 8).atTime(9, 15).toEpochSecond(zone)
        assertEquals(expected, values.date)
        assertEquals(expected, values.valueDate)
        assertEquals(-300L, values.amount)
        assertEquals(4L, values.accountId)
    }

    @Test
    fun nextDraftKeepsAccountSignDayAndMethod() {
        val draft = TransactionDraft(
            id = TransactionDraft.NEW,
            accountId = 4,
            amountText = "3",
            isIncome = true,
            day = LocalDate.of(2026, 3, 8),
            payee = "Flohmarkt",
            categoryId = 5,
            categoryPath = "Verkäufe",
            tagIds = listOf(1, 2),
            methodId = 7,
            comment = "Stand",
            isCategorySuggested = true
        )
        val next = draft.next(listOf(9))
        assertEquals(4L, next.accountId)
        assertEquals(true, next.isIncome)
        assertEquals(LocalDate.of(2026, 3, 8), next.day)
        assertEquals(7L, next.methodId)
        assertEquals("", next.amountText)
        assertEquals("", next.payee)
        assertNull(next.categoryId)
        assertEquals(listOf(9L), next.tagIds)
        assertEquals("", next.comment)
        assertFalse(next.isCategorySuggested)
    }
}
