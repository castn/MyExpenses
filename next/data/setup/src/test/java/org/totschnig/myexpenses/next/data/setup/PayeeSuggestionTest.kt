package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.data.categories.CategorySuggestion
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.data.categories.SuggestionSource

class PayeeSuggestionTest {

    private val payee =
        SetupPayee(
            Counterpart("p:1", "REWE", payeeId = 1),
            100,
            listOf(1, 2, 3),
            LocalDate.of(2026, 1, 1)
        )

    private fun suggestion(transactionId: Long, categoryId: Long) =
        CategorySuggestion(transactionId, categoryId, SuggestionSource.WORDS, isCertain = false)

    @Test
    fun suggestsWhatMostDebitsOfThePayeeGet() {
        val suggestions =
            listOf(suggestion(1, 10), suggestion(2, 11), suggestion(3, 11), suggestion(9, 12))
        assertEquals(11L, suggestionFor(payee, suggestions.associateBy { it.transactionId }))
        assertNull(suggestionFor(payee, mapOf(9L to suggestion(9, 12))))
    }

    @Test
    fun suggestsACategoryNamedForCash() {
        val cash = SetupCash(300, listOf(1, 2), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1))
        val withdrawal = CategoryInfo(20, "Bargeldabhebung", "Sonstiges > Bargeldabhebung")
        val income = CategoryInfo(21, "Bargeld", "Einnahmen > Bargeld", isExpense = false)
        val other = CategoryInfo(22, "Lebensmittel", "Lebensmittel")
        val categories = listOf(other, income, withdrawal)
        assertEquals(20L, suggestionFor(cash, emptyMap(), categories))
        // What the withdrawals are suggested comes first
        assertEquals(22L, suggestionFor(cash, mapOf(1L to suggestion(1, 22)), categories))
        assertNull(suggestionFor(cash, emptyMap(), listOf(other)))
    }
}
