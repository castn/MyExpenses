package org.totschnig.myexpenses.next.contracts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ContractsStateTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 25)

    private fun contract(signature: String, amount: Long, isActive: Boolean = true, last: LocalDate = today) = Contract(
        signature = signature,
        name = signature,
        interval = ContractInterval.MONTHLY,
        transactions = listOf(ContractTransaction(id = 1, date = last, amount = -amount, accountId = 1)),
        nextExpectedDate = last.plusMonths(1),
        isActive = isActive
    )

    @Test
    fun partitionsIntoActiveEndedAndDismissed() {
        val state = buildContractsState(
            listOf(
                contract("small", 100),
                contract("big", 900),
                contract("oldEnded", 500, isActive = false, last = today.minusMonths(6)),
                contract("newEnded", 500, isActive = false, last = today.minusMonths(3)),
                contract("wrong", 300),
            ),
            ContractSettings(consent = true, dismissed = setOf("wrong", "notDetectedAnyMore"))
        )
        assertEquals(listOf("big", "small"), state.active.map { it.signature })
        assertEquals(listOf("newEnded", "oldEnded"), state.ended.map { it.signature })
        assertEquals(listOf("wrong"), state.dismissed.map { it.signature })
    }

    @Test
    fun appliesCustomNames() {
        val state = buildContractsState(
            listOf(contract("a", 100), contract("b", 200)),
            ContractSettings(consent = true, names = mapOf("a" to "Strom"))
        )
        assertEquals(setOf("Strom", "b"), state.active.map { it.displayName }.toSet())
        assertEquals("a", state.active.single { it.signature == "a" }.name)
    }
}
