package org.totschnig.myexpenses.next.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.balance.SalaryChoice
import java.time.LocalDate
import java.time.Period

class ContractAnalysisTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 25)
    private var nextId = 1L

    private fun monthly(count: Int, amount: Long, payeeId: Long, templateId: Long? = null, last: LocalDate = today.minusDays(3)) =
        (0 until count).map {
            ContractTransaction(
                id = nextId++, date = last.minus(Period.ofMonths(count - 1 - it)), amount = amount, accountId = 1,
                payeeId = payeeId, payeeName = "Payee $payeeId", templateId = templateId
            )
        }

    private val rent = monthly(12, -80000, payeeId = 1)
    private val gym = monthly(3, -3000, payeeId = 2)
    private val salary = monthly(12, 300000, payeeId = 3)
    private val transactions = rent + gym + salary

    private fun analysis(rules: List<ContractRule> = emptyList(), transactions: List<ContractTransaction> = this.transactions) =
        ContractAnalysis.of(transactions, rules, today)

    private fun ContractAnalysis.contract(payeeId: Long) =
        contracts.single { it.transactions.first().payeeId == payeeId }

    @Test
    fun confidentWithManyRegularPaymentsOrTemplate() {
        val analysis = analysis(transactions = transactions + monthly(3, -1000, payeeId = 4, templateId = 7))
        assertTrue(analysis.contract(1).isConfident)
        assertFalse(analysis.contract(2).isConfident)
        assertTrue(analysis.contract(3).isConfident)
        assertTrue(analysis.contract(4).isConfident)
    }

    @Test
    fun confirmsConfidentActiveSuggestions() {
        val ended = monthly(8, -1500, payeeId = 5, last = today.minusMonths(6))
        val analysis = analysis(transactions = transactions + ended)
        val rules = analysis.automaticRules(emptyList())
        assertEquals(setOf(1L, 3L), rules.flatMap { it.payeeIds }.toSet())
        assertTrue(rules.all { it.kind == ContractRule.Kind.CONTRACT })
    }

    @Test
    fun doesNotConfirmTwice() {
        val first = analysis().automaticRules(emptyList())
        assertTrue(analysis(first).automaticRules(first).isEmpty())
        // Also when the analysis did not know the stored rules yet
        assertTrue(analysis().automaticRules(first).isEmpty())
    }

    @Test
    fun rulesTurnSuggestionsIntoConfirmedContracts() {
        val rules = analysis().automaticRules(emptyList())
        val analysis = analysis(rules)
        assertTrue(analysis.contract(1).isConfirmed)
        assertFalse(analysis.contract(2).isConfirmed)
        assertEquals(listOf(analysis.contract(2)), analysis.suggestions)
    }

    @Test
    fun dismissingSuggestionIgnoresItsPayments() {
        val before = analysis()
        val rules = emptyList<ContractRule>().dismissing(before.contract(2), transactions)
        val after = analysis(rules)
        assertTrue(after.contracts.none { it.transactions.first().payeeId == 2L })
        assertEquals(gym, after.dismissed.single().transactions)
    }

    @Test
    fun dismissingConfirmedContractKeepsItsDecisions() {
        val (rules, rule) = emptyList<ContractRule>().withRuleFor(analysis().contract(1), transactions) {
            it.copy(name = "Rent")
        }
        val confirmed = analysis(rules).contract(1)
        val dismissed = rules.dismissing(confirmed, transactions)
        assertEquals(ContractRule.Kind.IGNORE, dismissed.single().kind)
        assertEquals("Rent", dismissed.single().name)
        assertEquals(rule.id, dismissed.single().id)
    }

    @Test
    fun restoringConfirms() {
        val rules = emptyList<ContractRule>().dismissing(analysis().contract(2), transactions)
        val restored = rules.restoring(analysis(rules).dismissed.single())
        assertEquals(ContractRule.Kind.CONTRACT, restored.single().kind)
        assertTrue(analysis(restored).contract(2).isConfirmed)
    }

    @Test
    fun withRuleForChangesExistingRule() {
        val (rules, _) = emptyList<ContractRule>().withRuleFor(analysis().contract(2), transactions)
        val confirmed = analysis(rules).contract(2)
        val (changed, rule) = rules.withRuleFor(confirmed, transactions) { it.copy(name = "Gym") }
        assertEquals(1, changed.size)
        assertEquals("Gym", rule.name)
    }

    @Test
    fun migratesDecisionsStoredBySignature() {
        val before = analysis()
        val rent = before.contract(1)
        val gym = before.contract(2)
        val salary = before.contract(3)
        val settings = ContractSettings(
            consent = true,
            dismissed = setOf(gym.signature),
            names = mapOf(rent.signature to "Rent"),
            areas = mapOf(rent.signature to BuiltInArea.HOUSING.key),
            salary = SalaryChoice.Fixed(setOf(salary.signature, "gone"))
        )
        val (rules, salaryChoice) = migrateToRules(settings, before)
        assertEquals(3, rules.size)
        val rentRule = rules.single { 1L in it.payeeIds }
        assertEquals(ContractRule.Kind.CONTRACT, rentRule.kind)
        assertEquals("Rent", rentRule.name)
        assertEquals(BuiltInArea.HOUSING.key, rentRule.areaKey)
        assertEquals(ContractRule.Kind.IGNORE, rules.single { 2L in it.payeeIds }.kind)
        val salaryRule = rules.single { 3L in it.payeeIds }
        assertEquals(ContractRule.Kind.CONTRACT, salaryRule.kind)
        assertNull(salaryRule.name)
        assertEquals(SalaryChoice.Fixed(setOf(salaryRule.key)), salaryChoice)
    }

    @Test
    fun migrationWithoutDecisionsCreatesNoRules() {
        val (rules, salary) = migrateToRules(ContractSettings(consent = true), analysis())
        assertTrue(rules.isEmpty())
        assertEquals(SalaryChoice.Automatic, salary)
    }

    @Test
    fun stateTakesDecisionsFromRules() {
        val (rules, _) = emptyList<ContractRule>().withRuleFor(analysis().contract(1), transactions) {
            it.copy(name = "Rent", areaKey = BuiltInArea.HOUSING.key)
        }
        val ignored = rules.dismissing(analysis(rules).contract(2), transactions)
        val analysis = analysis(ignored)
        val state = buildContractsState(
            analysis.contracts.filter { !it.isIncome },
            ContractSettings(consent = true, rules = ignored),
            analysis.dismissed
        )
        val rent = state.active.single { it.isConfirmed }
        assertEquals("Rent", rent.displayName)
        assertEquals(BuiltInArea.HOUSING, rent.area)
        assertEquals(listOf(gym), state.dismissed.map { it.transactions })
    }
}
