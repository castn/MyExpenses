package org.totschnig.myexpenses.next.data.transactions

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

class TemplatesTest {

    private val today = LocalDate.of(2026, 3, 10)

    private val rent = EditableTemplate(
        id = 4,
        values = TemplateValues(
            title = "Miete",
            accountId = 2,
            amount = -85000,
            payee = "Vermieter",
            categoryId = 5,
            tagIds = listOf(3),
            methodId = 1,
            comment = "bar",
            saveDirectly = true
        ),
        categoryPath = "Wohnen",
        fractionDigits = 2
    )

    @Test
    fun draftOfATemplateShowsItsValues() {
        val draft = rent.toDraft(',', today)
        assertEquals(TemplateFields(4, "Miete", saveDirectly = true), draft.template)
        assertEquals("850,00", draft.amountText)
        assertEquals("Wohnen", draft.categoryPath)
        assertEquals(today, draft.day)
    }

    @Test
    fun templateWithoutAmountHasAnEmptyField() {
        val draft = rent.copy(values = rent.values.copy(amount = 0)).toDraft(',', today)
        assertEquals("", draft.amountText)
        assertEquals(0L, draft.toTemplateValues(2)!!.amount)
    }

    @Test
    fun valuesOfADraftKeepTheSignAndTrimTexts() {
        val draft = rent.toDraft(',', today).copy(
            amountText = "12,5",
            isIncome = true,
            payee = " ",
            comment = " Zins ",
            template = TemplateFields(4, " Zinsen ", saveDirectly = false)
        )
        val values = draft.toTemplateValues(2)!!
        assertEquals("Zinsen", values.title)
        assertEquals(1250L, values.amount)
        assertNull(values.payee)
        assertEquals("Zins", values.comment)
        assertEquals(false, values.saveDirectly)
    }

    @Test
    fun unreadableAmountGivesNoValues() {
        assertNull(rent.toDraft(',', today).copy(amountText = "zwölf").toTemplateValues(2))
    }

    @Test
    fun templateFillsANewTransactionButKeepsItsDay() {
        val day = LocalDate.of(2026, 3, 1)
        val draft = NewTransaction().toDraft(1, listOf(7), day, ',')
        val filled = draft.withTemplate(rent, ',') { true }
        assertEquals(TransactionDraft.NEW, filled.id)
        assertEquals(day, filled.day)
        assertEquals(2L, filled.accountId)
        assertEquals(listOf(3L), filled.tagIds)
        assertNull(filled.template)
        assertEquals(4L, filled.fromTemplateId)
        assertEquals(1L, draft.withTemplate(rent, ',') { false }.accountId)
    }

    @Test
    fun templateOfATransactionIsTitledAfterItsPayee() {
        val transaction = EditableTransaction(
            id = 9,
            values = EditValues(1, -4290, 0, 0, "REWE", 6, listOf(2), null, null),
            categoryPath = "Lebensmittel",
            fractionDigits = 2,
            accountType = null,
            isAmountEditable = true,
            isImported = true
        )
        val draft = transaction.toTemplateDraft(',', today)
        assertEquals(
            TemplateFields(TransactionDraft.NEW, "REWE", saveDirectly = false),
            draft.template
        )
        assertEquals("42,90", draft.amountText)
        assertEquals(6L, draft.categoryId)
    }

    @Test
    fun chipsAreTheOpenEntriesUsedMost() {
        fun summary(id: Long, kind: TemplateKind = TemplateKind.ENTRY, isClosed: Boolean = false) =
            TemplateSummary(
                id, "T$id", kind, 0,
                CurrencyInfo(
                    "EUR",
                    "€",
                    2
                ),
                "Giro", null, null, null, isClosed, false
            )
        val all = listOf(
            summary(1),
            summary(2, TemplateKind.SPLIT),
            summary(3, isClosed = true),
            summary(4, TemplateKind.TRANSFER)
        ) + (5L..12L).map { summary(it) }
        assertEquals(listOf(1L, 5L, 6L, 7L, 8L, 9L), all.forChips().map { it.id })
    }
}
