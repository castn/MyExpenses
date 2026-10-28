package org.totschnig.myexpenses.next.data.categories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDecisionsTest {

    private fun certain(transactionId: Long, categoryId: Long = 1) =
        CategorySuggestion(transactionId, categoryId, SuggestionSource.PAYEE, isCertain = true)

    private fun uncertain(transactionId: Long, categoryId: Long = 1) =
        CategorySuggestion(transactionId, categoryId, SuggestionSource.WORDS, isCertain = false)

    @Test
    fun certainForNewTransactionsAreApplied() {
        val plan = CategoryDecisions(firstNewId = 100).plan(listOf(certain(99), certain(100), uncertain(101)))
        assertEquals(listOf(certain(100)), plan.apply)
        assertEquals(listOf(certain(99)), plan.backlog)
        assertEquals(setOf(99L, 101L), plan.suggestions.keys)
    }

    @Test
    fun beforeFirstAnalysisEverythingIsBacklog() {
        val plan = CategoryDecisions().plan(listOf(certain(5), certain(6)))
        assertTrue(plan.apply.isEmpty())
        assertEquals(2, plan.backlog.size)
    }

    @Test
    fun answeredBacklogIsNotAskedAgain() {
        val plan = CategoryDecisions(firstNewId = 100, backlogAnswered = true).plan(listOf(certain(99)))
        assertTrue(plan.backlog.isEmpty())
        // Still suggested in the details
        assertEquals(certain(99), plan.suggestions[99])
    }

    @Test
    fun rejectedSuggestionIsDropped() {
        val decisions = CategoryDecisions(firstNewId = 100, rejected = mapOf(100L to 1L, 101L to 1L))
        val plan = decisions.plan(listOf(certain(100), uncertain(101), uncertain(102)))
        assertTrue(plan.apply.isEmpty())
        assertEquals(setOf(102L), plan.suggestions.keys)
    }

    @Test
    fun anotherCategoryThanTheRejectedOneIsSuggested() {
        val plan = CategoryDecisions(firstNewId = 100, rejected = mapOf(100L to 1L)).plan(listOf(certain(100, categoryId = 2)))
        assertEquals(listOf(certain(100, categoryId = 2)), plan.apply)
    }

    @Test
    fun keepsTheMostRecentAutomaticAssignments() {
        val applied = (1L..CategoryDecisions.MAX_AUTOMATIC + 10L).map { certain(it) }
        val automatic = CategoryDecisions().withAutomatic(applied).automatic
        assertEquals(CategoryDecisions.MAX_AUTOMATIC, automatic.size)
        assertTrue(11L in automatic && 10L !in automatic)
    }
}
