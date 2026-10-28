package org.totschnig.myexpenses.next.data.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.data.budgets.BudgetFacts
import org.totschnig.myexpenses.next.data.budgets.Pot
import org.totschnig.myexpenses.next.data.budgets.PotKind
import org.totschnig.myexpenses.next.data.budgets.PotStatus

class BudgetTipsTest {

    private fun status(id: String, amount: Long, vararg history: Long, kind: PotKind = PotKind.RUNNING) =
        PotStatus(Pot(id = id, name = id, kind = kind, amount = amount), planned = amount, history = history.toList())

    @Test
    fun findsPotsOverspentInMostOfTheLastPeriods() {
        val tip = potsOverspent(
            BudgetFacts(
                listOf(
                    // Long ago within budget does not count, the last four do
                    status("going", 10000, 5000, 12000, 9000, 14000, 13000),
                    status("food", 40000, 41000, 45000, 46000, 50000),
                    // Only two of four
                    status("gifts", 5000, 6000, 4000, 7000, 3000),
                    // Too new
                    status("new", 1000, 2000, 2000, 2000),
                    status("yearly", 1000, 2000, 2000, 2000, 2000, kind = PotKind.YEARLY),
                )
            )
        )!!
        assertEquals(listOf("food", "going"), tip.pots.map { it.potId })
        assertEquals(OverspentPot("going", "going", 3, 3000), tip.pots[1])
        assertEquals(4, tip.pots[0].overspent)
        assertEquals(setOf("food:4", "going:3"), tip.facts)
        assertEquals((5500 + 3000) * 12L, tip.yearlyAmount)
    }

    @Test
    fun noTipWithoutBudget() {
        assertNull(potsOverspent(null))
        assertNull(potsOverspent(BudgetFacts(listOf(status("ok", 10000, 9000, 9000, 9000, 9000)))))
    }
}
