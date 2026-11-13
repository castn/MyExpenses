package org.totschnig.myexpenses.next.feature.setup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.data.setup.CategoryCoverage
import org.totschnig.myexpenses.next.data.setup.CategoryQueue
import org.totschnig.myexpenses.next.data.setup.Pace
import org.totschnig.myexpenses.next.data.setup.SetupCash
import org.totschnig.myexpenses.next.data.setup.SetupContracts
import org.totschnig.myexpenses.next.data.setup.SetupPayee
import org.totschnig.myexpenses.next.data.setup.SetupProgress
import org.totschnig.myexpenses.next.data.setup.SetupStep

class SetupStatesTest {

    private fun payee(id: Long, amount: Long) = SetupPayee(
        Counterpart("p:$id", "Payee $id", payeeId = id),
        amount,
        listOf(id),
        LocalDate.of(2026, 1, 1)
    )

    /** 1000 spent, nothing categorized: 600 and 300 reach the target, 100 is beyond it */
    private val queue = CategoryQueue(
        CategoryCoverage(spent = 1000, categorized = 0),
        required = listOf(payee(1, 600), payee(2, 300)),
        optional = listOf(payee(3, 100))
    )

    private fun step(
        decided: Set<String> = emptySet(),
        skipped: Set<String> = emptySet(),
        unclear: Set<String> = emptySet(),
        beyondTarget: Boolean = false
    ): CategoriesStep {
        val visit = SetupVisit(decided, skipped, unclear, beyondTarget)
        return categoriesStep(queue, null, visit, Pace(), backlog = 0)
    }

    @Test
    fun overviewTellsWhatEachStepTakes() {
        val all = listOf(SetupStep.CATEGORIES, SetupStep.CONTRACTS)
        val overview = setupOverview(all, SetupProgress(), queue, SetupContracts.Available(3))
        assertEquals(SetupStep.CATEGORIES, overview.next)
        assertEquals(listOf(StepState.CURRENT, StepState.OPEN), overview.steps.map { it.state })
        assertEquals(listOf(2, 3), overview.steps.map { it.decisions })
        // 5 decisions at the placeholder of 8 seconds
        assertEquals(40L, overview.seconds)
    }

    @Test
    fun overviewAsksAboutTheIncomesAndTheSalaryFirst() {
        val contracts = SetupContracts.Available(3, incomeSuggestions = 2, incomes = 1)
        val overview = setupOverview(SetupStep.entries, SetupProgress(), queue, contracts)
        assertEquals(SetupStep.INCOME, overview.next)
        // Two incomes to confirm and the salary
        assertEquals(listOf(3, 2, 3), overview.steps.map { it.decisions })
    }

    @Test
    fun overviewCountsNothingForDoneSteps() {
        val progress = SetupProgress(
            finished = setOf(SetupStep.CATEGORIES),
            skipped = setOf(SetupStep.CONTRACTS)
        )
        val overview =
            setupOverview(
                listOf(SetupStep.CATEGORIES, SetupStep.CONTRACTS),
                progress,
                queue,
                SetupContracts.Available(3)
            )
        assertEquals(listOf(StepState.DONE, StepState.CURRENT), overview.steps.map { it.state })
        assertEquals(listOf(0, 3), overview.steps.map { it.decisions })
    }

    @Test
    fun showsThePayeesWithTheMostMoneyFirst() {
        val step = step()
        assertEquals("p:1", step.card?.key)
        assertEquals(2, step.left)
        assertEquals(16L, step.seconds)
        assertEquals(0.0, step.share, 0.0)
    }

    @Test
    fun countsDecisionsAtOnceAndStopsAtTheTarget() {
        val one = step(decided = setOf("p:1"))
        assertEquals("p:2", one.card?.key)
        assertEquals(0.6, one.share, 0.001)
        val both = step(decided = setOf("p:1", "p:2"))
        assertTrue(both.isTargetReached)
        assertNull(both.card)
        assertTrue(both.hasMore)
        assertEquals(0, both.left)
        // Going on, the payee beyond the target comes
        assertEquals("p:3", step(decided = setOf("p:1", "p:2"), beyondTarget = true).card?.key)
    }

    @Test
    fun theMoneyOfAnUnclearPayeeNoLongerCounts() {
        // 600 of the 700 left
        val step = step(decided = setOf("p:1", "p:2"), unclear = setOf("p:2"))
        assertEquals(600.0 / 700, step.share, 0.001)
        assertTrue(step.isTargetReached)
    }

    @Test
    fun putsOffPayeesUntilRevisited() {
        val step = step(skipped = setOf("p:1"))
        assertEquals("p:2", step.card?.key)
        val allSkipped = step(skipped = setOf("p:1", "p:2", "p:3"))
        assertNull(allSkipped.card)
        assertTrue(allSkipped.hasSkipped)
        assertFalse(allSkipped.isTargetReached)
    }

    @Test
    fun showsThePayeeGoneBackToFirstWithTheCategoryChosenBefore() {
        val visit = SetupVisit(
            decided = setOf("p:2"),
            skipped = setOf("p:3"),
            revisit = Revisit("p:3", categoryId = 10),
            canGoBack = true
        )
        val step = categoriesStep(queue, null, visit, Pace(), backlog = 0)
        assertEquals("p:3", step.card?.key)
        assertEquals(10L, step.card?.suggestion)
        assertTrue(step.canGoBack)
        // Until the ledger has its transactions without category again, the next payee is shown
        val before = queue.copy(optional = emptyList())
        assertEquals("p:1", categoriesStep(before, null, visit, Pace(), backlog = 0).card?.key)
    }

    /** 1000 spent: 500 rent, 300 cash, 200 cinema; rent and cash reach the target */
    private val withCash = CategoryQueue(
        CategoryCoverage(spent = 1000, categorized = 0),
        required = listOf(payee(1, 500)),
        optional = listOf(payee(3, 200)),
        cash = SetupCash(300, listOf(7, 8), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
        isCashRequired = true
    )

    private fun cashStep(visit: SetupVisit = SetupVisit(), canOpen: Boolean = true) =
        categoriesStep(withCash, null, visit, Pace(), backlog = 0, canOpenCashAccount = canOpen)

    @Test
    fun showsTheCashWithdrawalsWhereTheirMoneyPutsThem() {
        val first = cashStep()
        assertEquals("p:1", first.card?.key)
        assertNull(first.cashCard)
        assertEquals(2, first.left)
        val cash = cashStep(SetupVisit(decided = setOf("p:1")))
        assertNull(cash.card)
        assertEquals(300L, cash.cashCard!!.cash.amount)
        assertTrue(cash.cashCard!!.canOpenAccount)
        assertEquals(1, cash.left)
        // Decided on, their money counts and the target is reached
        val done = cashStep(SetupVisit(decided = setOf("p:1", SetupCash.KEY)))
        assertTrue(done.isTargetReached)
        assertNull(done.cashCard)
        assertEquals(0.8, done.share, 0.001)
        val overview = setupOverview(
            listOf(SetupStep.CATEGORIES),
            SetupProgress(),
            withCash,
            SetupContracts.None
        )
        assertEquals(2, overview.steps.first().decisions)
    }

    @Test
    fun cashWithdrawalsCanBePutOffAndGoneBackTo() {
        val putOff = cashStep(SetupVisit(decided = setOf("p:1"), skipped = setOf(SetupCash.KEY)))
        assertEquals("p:3", putOff.card?.key)
        assertTrue(putOff.hasSkipped)
        // Gone back to, they come first with the category chosen before, unless one was picked
        val revisit = SetupVisit(revisit = Revisit(SetupCash.KEY, categoryId = 10))
        assertEquals(10L, cashStep(revisit).cashCard?.categoryId)
        assertEquals(11L, cashStep(revisit.copy(cashCategory = 11)).cashCard?.categoryId)
    }
}
