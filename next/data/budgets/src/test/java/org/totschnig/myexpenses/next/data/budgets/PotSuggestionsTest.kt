package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractDirection
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractRule
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts

class PotSuggestionsTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 20)

    private val food = CategoryInfo(1, "Lebensmittel", "Lebensmittel")
    private val supermarket = CategoryInfo(2, "Supermarkt", "Lebensmittel > Supermarkt", mainId = 1, parentId = 1)
    private val eatingOut = CategoryInfo(3, "Essen gehen", "Essen gehen")
    private val restaurant = CategoryInfo(4, "Restaurant", "Essen gehen > Restaurant", mainId = 3, parentId = 3)
    private val leisure = CategoryInfo(5, "Freizeit & Hobbys", "Freizeit & Hobbys")
    private val gym = CategoryInfo(6, "Fitnessstudio & Sport", "Freizeit & Hobbys > Fitnessstudio & Sport", mainId = 5, parentId = 5)
    private val bars = CategoryInfo(7, "Ausgehen (Bar, Club)", "Freizeit & Hobbys > Ausgehen (Bar, Club)", mainId = 5, parentId = 5)
    private val barber = CategoryInfo(8, "Barbershop", "Freizeit & Hobbys > Barbershop", mainId = 5, parentId = 5)
    private val salary = CategoryInfo(9, "Lebensmittelzuschuss", "Lebensmittelzuschuss", isExpense = false)
    private val all = listOf(food, supermarket, eatingOut, restaurant, leisure, gym, bars, barber, salary)

    @Test
    fun templatesFindMainCategoriesOrMatchingSubcategories() {
        assertEquals(setOf(food.id), PotTemplate.DAILY.categoriesIn(all))
        assertEquals(setOf(eatingOut.id, bars.id), PotTemplate.GOING_OUT.categoriesIn(all))
        // "Bar" as a whole word only
        assertEquals(setOf(restaurant.id, bars.id), PotTemplate.RESTAURANTS.categoriesIn(all))
        assertEquals(setOf(gym.id), PotTemplate.FITNESS.categoriesIn(all))
        assertEquals(emptySet<Long>(), PotTemplate.GOAL.categoriesIn(all))
    }

    private fun spend(amount: Long, date: LocalDate, category: CategoryInfo, id: Long) =
        LedgerTransaction(id = id, date = date, amount = -amount, accountId = 1, categoryId = category.id)

    private fun input(transactions: List<LedgerTransaction>, pots: List<Pot> = emptyList(), contracts: List<Contract> = emptyList()) =
        BudgetInput(
            Ledger(transactions, OwnAccounts(setOf(1), emptyMap())), pots, all.associateBy { it.id },
            contracts, emptyList(), emptyMap(), today
        )

    @Test
    fun suggestsTheMedianOfTheLastPeriodsRoundedUp() {
        val transactions = listOf(
            spend(30000, LocalDate.of(2026, 6, 5), supermarket, 1),
            spend(41000, LocalDate.of(2026, 7, 5), supermarket, 2),
            spend(38210, LocalDate.of(2026, 8, 5), supermarket, 3),
            spend(52000, LocalDate.of(2026, 9, 5), supermarket, 4),
            spend(9000, LocalDate.of(2026, 10, 5), supermarket, 5), // current period, not complete
        )
        val pot = Pot(name = "Täglicher Bedarf", kind = PotKind.RUNNING, categoryIds = setOf(food.id))
        val suggestion = PotSuggestions.suggest(input(transactions), pot)
        // July, August, September: 41000, 38210, 52000
        assertEquals(41000L, suggestion.amount)
        assertEquals(3, suggestion.periods)
        assertEquals(39000L, PotSuggestions.roundUp(38210))
        assertEquals(4500L, PotSuggestions.roundUp(4001))
    }

    @Test
    fun yearlyPotSumsTheLastYearAndLeavesOutWhatOtherPotsHoldMoreClosely() {
        val transactions = listOf(
            spend(5000, LocalDate.of(2026, 3, 5), restaurant, 1),
            spend(7000, LocalDate.of(2026, 9, 5), restaurant, 2),
            spend(9000, LocalDate.of(2026, 9, 6), bars, 3),
        )
        val restaurants = Pot(id = "r", name = "Restaurants", kind = PotKind.RUNNING, categoryIds = setOf(restaurant.id))
        val pot = Pot(name = "Ausgehen", kind = PotKind.YEARLY, categoryIds = setOf(eatingOut.id, bars.id))
        assertEquals(9000L, PotSuggestions.suggest(input(transactions, listOf(restaurants)), pot).amount)
        assertEquals(21000L, PotSuggestions.suggest(input(transactions), pot).amount)
        assertNull(PotSuggestions.suggest(input(emptyList()), pot).amount)
    }

    private fun contract(ruleId: String, category: CategoryInfo, reserve: Boolean? = null) = Contract(
        signature = ruleId,
        name = ruleId,
        interval = ContractInterval.MONTHLY,
        transactions = listOf(ContractTransaction(100, LocalDate.of(2026, 10, 1), -3500, 1, categoryPath = category.path)),
        nextExpectedDate = LocalDate.of(2026, 11, 1),
        isActive = true,
        isConfirmed = true,
        rule = ContractRule(ruleId, ContractRule.Kind.CONTRACT, ContractDirection.EXPENSE, interval = ContractInterval.MONTHLY),
        reserveChoice = reserve
    )

    @Test
    fun suggestsContractsOfItsCategoriesNoOtherPotLists() {
        val studio = contract("studio", gym)
        val club = contract("club", gym)
        val stream = contract("stream", restaurant)
        val other = Pot(id = "o", name = "Verein", kind = PotKind.RUNNING, contractRuleIds = setOf("club"))
        val pot = Pot(name = "Fitness", kind = PotKind.RUNNING, categoryIds = setOf(leisure.id))
        val suggestion = PotSuggestions.suggest(input(emptyList(), listOf(other), listOf(studio, club, stream)), pot)
        assertEquals(listOf(studio), suggestion.contracts)
    }
}
