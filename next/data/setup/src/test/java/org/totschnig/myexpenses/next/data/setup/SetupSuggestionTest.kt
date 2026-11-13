package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.data.categories.CategorySuggestion
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.data.categories.SuggestionSource

class SetupSuggestionTest {

    private val payee =
        SetupPayee(
            Counterpart("p:1", "REWE", payeeId = 1),
            100,
            listOf(1, 2, 3),
            LocalDate.of(2026, 1, 1)
        )

    private fun suggestion(transactionId: Long, categoryId: Long, isCertain: Boolean = false) =
        CategorySuggestion(transactionId, categoryId, SuggestionSource.WORDS, isCertain)

    @Test
    fun suggestsWhatMostDebitsOfThePayeeGet() {
        val suggestions =
            listOf(suggestion(1, 10), suggestion(2, 11), suggestion(3, 11), suggestion(9, 12))
        assertEquals(
            SetupSuggestion(11, isCertain = false),
            suggestionFor(payee, suggestions.associateBy { it.transactionId })
        )
        assertNull(suggestionFor(payee, mapOf(9L to suggestion(9, 12))))
    }

    @Test
    fun isCertainOnlyIfAllDebitsAreSuggestedItForSure() {
        val certain = listOf(1L, 2L, 3L).associateWith { suggestion(it, 10, isCertain = true) }
        assertEquals(SetupSuggestion(10, isCertain = true), suggestionFor(payee, certain))
        val mixed = certain + (3L to suggestion(3, 10))
        assertEquals(SetupSuggestion(10, isCertain = false), suggestionFor(payee, mixed))
    }

    @Test
    fun suggestsACategoryNamedForCash() {
        val cash = SetupCash(300, listOf(1, 2), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1))
        val withdrawal = CategoryInfo(20, "Bargeldabhebung", "Sonstiges > Bargeldabhebung")
        val income = CategoryInfo(21, "Bargeld", "Einnahmen > Bargeld", isExpense = false)
        val other = CategoryInfo(22, "Lebensmittel", "Lebensmittel")
        val categories = listOf(other, income, withdrawal)
        assertEquals(
            SetupSuggestion(20, isCertain = false),
            suggestionFor(cash, emptyMap(), categories)
        )
        // What the withdrawals are suggested comes first
        assertEquals(
            SetupSuggestion(22, isCertain = false),
            suggestionFor(cash, mapOf(1L to suggestion(1, 22)), categories)
        )
        assertNull(suggestionFor(cash, emptyMap(), listOf(other)))
    }
}
