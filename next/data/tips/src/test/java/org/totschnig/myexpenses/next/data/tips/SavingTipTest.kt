package org.totschnig.myexpenses.next.data.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractDirection
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import java.time.LocalDate

class SavingTipTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 29)
    private var nextId = 1L

    /** One payment per month, the last one three days ago */
    private fun contract(
        amounts: List<Long>,
        name: String = "Contract",
        interval: ContractInterval = ContractInterval.MONTHLY,
        direction: ContractDirection = ContractDirection.EXPENSE,
        reserve: Boolean? = null,
    ): Contract {
        val last = today.minusDays(3)
        val transactions = amounts.mapIndexed { i, amount ->
            ContractTransaction(
                id = nextId++,
                date = last.minus(interval.step.multipliedBy(amounts.size - 1 - i)),
                amount = if (direction == ContractDirection.EXPENSE) -amount else amount,
                accountId = 1
            )
        }
        return Contract(
            signature = "r:$name",
            name = name,
            interval = interval,
            transactions = transactions,
            nextExpectedDate = last.plus(interval.step),
            isActive = true,
            direction = direction,
            isConfirmed = true,
            reserveChoice = reserve
        )
    }

    private fun increases(vararg contracts: Contract) = priceIncreases(contracts.toList(), today)

    @Test
    fun sumsIncreasesWithinLastYear() {
        val tip = increases(
            contract(List(10) { 1399L } + List(4) { 1799L }, name = "Streaming"),
            contract(List(14) { 4000L }, name = "Gym"),
            contract(List(6) { 9200L } + List(8) { 9800L }, name = "Power")
        )!!
        assertEquals(listOf("Power", "Streaming"), tip.increases.map { it.contract.name })
        val streaming = tip.increases[1]
        assertEquals(1399L, streaming.before)
        assertEquals(1799L, streaming.after)
        assertEquals(today.minusDays(3).minusMonths(3), streaming.since)
        assertEquals((400L + 600L) * 12, tip.yearlyAmount)
        assertEquals(1000L, tip.monthlyAmount)
    }

    @Test
    fun increaseBeforeLastYearDoesNotCount() {
        assertNull(increases(contract(List(3) { 1399L } + List(14) { 1799L })))
    }

    @Test
    fun youngContractIsComparedWithFirstPayment() {
        val tip = increases(contract(listOf(999L, 999L, 999L, 1299L, 1299L)))!!
        assertEquals(999L, tip.increases.single().before)
    }

    @Test
    fun severalIncreasesCountFromThePriceBefore() {
        val increase = increases(contract(List(6) { 1000L } + List(4) { 1100L } + List(4) { 1200L }))!!.increases.single()
        assertEquals(1000L, increase.before)
        assertEquals(1200L, increase.after)
    }

    @Test
    fun yearlyContract() {
        val increase = increases(contract(listOf(30000L, 36000L), interval = ContractInterval.YEARLY))!!.increases.single()
        assertEquals(6000L, increase.yearlyAmount)
    }

    @Test
    fun noTipForDecreaseVaryingAmountsIncomeOrReserve() {
        assertNull(increases(contract(List(10) { 1799L } + List(4) { 1399L })))
        assertNull(increases(contract(List(10) { 5000L } + listOf(5600L, 6100L))))
        assertNull(increases(contract(List(10) { 5000L } + List(4) { 5010L })))
        assertNull(increases(contract(List(10) { 200000L } + List(4) { 210000L }, direction = ContractDirection.INCOME)))
        assertNull(increases(contract(List(10) { 5000L } + List(4) { 10000L }, reserve = true)))
    }

    @Test
    fun dismissedTipComesBackWithNewIncrease() {
        val streaming = contract(List(10) { 1399L } + List(4) { 1799L }, name = "Streaming")
        val first = increases(streaming)!!
        val decisions = TipDecisions(dismissed = mapOf(first.id to first.facts))
        assertTrue(decisions.isDismissed(first))
        assertTrue(decisions.isDismissed(savingTips(listOf(streaming), emptyList(), today).single()))
        val second = increases(streaming, contract(List(10) { 4000L } + List(4) { 4500L }, name = "Gym"))!!
        assertFalse(decisions.isDismissed(second))
    }

    @Test
    fun seenTipIsNotNew() {
        val tip = increases(contract(List(10) { 1399L } + List(4) { 1799L }))!!
        assertTrue(TipDecisions().isNew(tip))
        assertFalse(TipDecisions(seen = mapOf(tip.id to tip.facts)).isNew(tip))
    }
}
