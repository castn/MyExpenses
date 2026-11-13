package org.totschnig.myexpenses.next.data.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupContractsTest {

    @Test
    fun asksAboutIncomesAndTheSalaryOnlyIfThereAreIncomes() {
        val none = SetupContracts.Available(suggestions = 3)
        assertFalse(none.hasIncomes)
        assertEquals(0, none.incomeDecisions)
        // Two to confirm and the salary
        assertEquals(3, SetupContracts.Available(0, incomeSuggestions = 2).incomeDecisions)
        // All confirmed, still the salary to choose
        val confirmed = SetupContracts.Available(0, incomes = 1)
        assertTrue(confirmed.hasIncomes)
        assertEquals(1, confirmed.incomeDecisions)
    }
}
