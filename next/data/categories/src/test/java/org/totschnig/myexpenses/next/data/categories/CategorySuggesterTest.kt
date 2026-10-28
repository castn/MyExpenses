package org.totschnig.myexpenses.next.data.categories

import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySuggesterTest {

    private val supermarket = CategoryInfo(1, "Supermarkt", "Essen > Supermarkt")
    private val restaurant = CategoryInfo(2, "Restaurant", "Essen > Restaurant")
    private val fuel = CategoryInfo(3, "Kraftstoff", "Auto > Kraftstoff")
    private val food = CategoryInfo(4, "Essen", "Essen")
    private val petFood = CategoryInfo(5, "Essen", "Haustiere > Essen")
    private val refunds = CategoryInfo(6, "Erstattungen", "Sonstige Einnahmen > Erstattungen")
    private val leisure = CategoryInfo(7, "Freizeit", "Freizeit")
    private val categories = listOf(supermarket, restaurant, fuel, food, petFood, refunds, leisure)

    private var nextId = 1L

    private fun input(
        payeeId: Long? = null,
        payeeName: String? = null,
        comment: String? = null,
        categoryId: Long? = null,
        amount: Long = -2500,
    ) = CategoryInput(nextId++, amount, payeeId, payeeName, comment, categoryId)

    private fun suggester(history: List<CategoryInput> = emptyList(), rules: List<CategoryRule> = emptyList()) =
        CategorySuggester(history, rules, categories)

    @Test
    fun ruleIsCertain() {
        val suggestion = suggester(rules = listOf(CategoryRule("r1", setOf(10), leisure.id)))
            .suggest(input(payeeId = 10, payeeName = "REWE"))!!
        assertEquals(leisure.id, suggestion.categoryId)
        assertEquals(SuggestionSource.RULE, suggestion.source)
        assertTrue(suggestion.isCertain)
    }

    @Test
    fun transactionWithCategoryGetsNoSuggestion() {
        assertNull(suggester().suggest(input(payeeName = "REWE", categoryId = leisure.id)))
    }

    @Test
    fun payeeHistoryIsCertainFromFiveWithNinetyPercent() {
        val history = List(9) { input(payeeId = 10, categoryId = supermarket.id) } + input(payeeId = 10, categoryId = leisure.id)
        val suggestion = suggester(history).suggest(input(payeeId = 10))!!
        assertEquals(supermarket.id, suggestion.categoryId)
        assertEquals(SuggestionSource.PAYEE, suggestion.source)
        assertTrue(suggestion.isCertain)
    }

    @Test
    fun fewOrMixedEarlierTransactionsAreOnlyASuggestion() {
        val few = List(4) { input(payeeId = 10, categoryId = supermarket.id) }
        assertFalse(suggester(few).suggest(input(payeeId = 10))!!.isCertain)
        val mixed = List(6) { input(payeeId = 10, categoryId = supermarket.id) } + List(3) { input(payeeId = 10, categoryId = leisure.id) }
        val suggestion = suggester(mixed).suggest(input(payeeId = 10))!!
        assertEquals(supermarket.id, suggestion.categoryId)
        assertFalse(suggestion.isCertain)
    }

    @Test
    fun noMajorityNoPayeeSuggestion() {
        val tied = List(2) { input(payeeId = 10, categoryId = supermarket.id) } + List(2) { input(payeeId = 10, categoryId = leisure.id) }
        assertNull(suggester(tied).suggest(input(payeeId = 10, payeeName = "Amazon")))
    }

    @Test
    fun refundOfAShopIsNoPurchase() {
        val history = List(6) { input(payeeId = 10, categoryId = supermarket.id) }
        assertNull(suggester(history).suggest(input(payeeId = 10, payeeName = "Kaufhaus Schmidt", amount = 2500)))
    }

    @Test
    fun payeeWithoutIdIsMatchedByName() {
        val history = List(5) { input(payeeName = "Café Fritz", categoryId = restaurant.id) }
        val suggestion = suggester(history).suggest(input(payeeName = "CAFÉ FRITZ"))!!
        assertEquals(restaurant.id, suggestion.categoryId)
        assertTrue(suggestion.isCertain)
    }

    @Test
    fun wordsOfOtherPayeesLeadToASuggestion() {
        // Every branch is a payee of its own
        val history = (1L..4L).map { input(payeeId = it, payeeName = "Rhein Center Markt $it", categoryId = supermarket.id) }
        val suggestion = suggester(history).suggest(input(payeeId = 99, payeeName = "Rhein Center Markt 7"))!!
        assertEquals(supermarket.id, suggestion.categoryId)
        assertEquals(SuggestionSource.WORDS, suggestion.source)
        assertFalse(suggestion.isCertain)
    }

    @Test
    fun paymentWordsTellNothing() {
        val history = List(5) { input(payeeName = "Kino $it", comment = "Kartenzahlung girocard", categoryId = leisure.id) }
        assertNull(suggester(history).suggest(input(payeeName = "Unbekannt", comment = "Kartenzahlung girocard")))
    }

    @Test
    fun wellKnownMerchantsMatchedToTheUsersCategories() {
        fun suggestion(payee: String) = suggester().suggest(input(payeeName = payee))
        assertEquals(supermarket.id, suggestion("REWE Markt GmbH")!!.categoryId)
        assertEquals(SuggestionSource.MERCHANT, suggestion("REWE Markt GmbH")!!.source)
        assertEquals(restaurant.id, suggestion("Lieferando.de")!!.categoryId)
        assertEquals(fuel.id, suggestion("ARAL Station 1234")!!.categoryId)
        assertEquals(petFood.id, suggestion("Fressnapf")!!.categoryId)
        assertNull(suggestion("Hans Müller"))
    }

    @Test
    fun merchantWithoutMatchingCategoryGetsNoSuggestion() {
        assertNull(CategorySuggester(emptyList(), emptyList(), listOf(leisure)).suggest(input(payeeName = "REWE")))
    }

    @Test
    fun deletedCategoryIsNotSuggested() {
        val suggestion = suggester(rules = listOf(CategoryRule("r1", setOf(10), 999))).suggest(input(payeeId = 10, payeeName = "REWE"))!!
        assertEquals(SuggestionSource.MERCHANT, suggestion.source)
    }
}
