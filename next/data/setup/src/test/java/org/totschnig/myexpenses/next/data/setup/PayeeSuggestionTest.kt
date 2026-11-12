package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
}
