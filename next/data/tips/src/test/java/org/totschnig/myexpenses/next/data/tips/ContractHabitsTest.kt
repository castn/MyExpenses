package org.totschnig.myexpenses.next.data.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.data.contracts.AreaChoice
import org.totschnig.myexpenses.next.data.contracts.BuiltInArea
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import java.time.LocalDate

class ContractHabitsTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 29)
    private var nextId = 1L

    /** Paid every [interval] from [since] until a few days ago */
    private fun contract(
        payee: String,
        comment: String? = null,
        since: LocalDate = today.minusMonths(6),
        interval: ContractInterval = ContractInterval.MONTHLY,
        amount: Long = 5000,
        area: BuiltInArea? = null,
        reserve: Boolean? = null,
    ): Contract {
        val transactions = generateSequence(since) { it.plus(interval.step) }
            .takeWhile { !it.isAfter(today.minusDays(3)) }
            .map { ContractTransaction(nextId++, it, -amount, 1, payeeName = payee, comment = comment) }
            .toList()
        return Contract(
            signature = "r:$nextId",
            name = payee,
            areaChoice = area?.let { AreaChoice.Fixed(it) } ?: AreaChoice.Automatic,
            interval = interval,
            transactions = transactions,
            nextExpectedDate = transactions.last().date.plus(interval.step),
            isActive = true,
            isConfirmed = true,
            reserveChoice = reserve
        )
    }

    @Test
    fun carInsuranceIsSwitchableButNoDuplicate() {
        assertEquals(ContractKind.CAR_INSURANCE, ContractKind.of(contract("HUK-COBURG", "Kfz-Versicherung KA-AB 123")))
        assertEquals(ContractKind.CAR_INSURANCE, ContractKind.of(contract("Allianz", "Kfz-Haftpflicht")))
        assertNull(ContractKind.of(contract("Bundeskasse", "Kfz-Steuer")))
        assertTrue(
            duplicateContracts(
                listOf(contract("HUK", "Kfz-Versicherung Golf"), contract("HUK", "Kfz-Versicherung Polo"))
            ).isEmpty()
        )
    }

    @Test
    fun longRunningSwitchableContracts() {
        val power = contract("Vattenfall", since = today.minusMonths(25))
        val tip = longRunningContracts(
            listOf(
                power,
                contract("congstar", since = today.minusMonths(12)),
                contract("Netflix", since = today.minusMonths(25)),
                contract("Stadtwerke", "Abschlag Gas", since = today.minusMonths(25), amount = 9000)
            ),
            today
        )!!
        assertEquals(listOf("Stadtwerke", "Vattenfall"), tip.contracts.map { it.contract.name })
        assertEquals(power.transactions.first().date, tip.contracts[1].since)
        assertEquals((9000L + 5000L) * 12, tip.yearlyAmount)
    }

    @Test
    fun noLongRunningTipForYoungContracts() {
        assertNull(longRunningContracts(listOf(contract("Vattenfall", since = today.minusMonths(20))), today))
    }

    @Test
    fun insurancesAndSubscriptionsPaidMonthly() {
        val tip = frequentPayments(
            listOf(
                contract("HUK", "Privathaftpflicht", amount = 500),
                contract("ARAG", comment = "Beitrag", area = BuiltInArea.INSURANCE, interval = ContractInterval.QUARTERLY),
                contract("Spotify", amount = 1099),
                contract("Allianz", "Hausratversicherung", interval = ContractInterval.YEARLY, since = today.minusMonths(13)),
                contract("Vattenfall"),
                contract("Techniker Krankenkasse", "Krankenversicherung"),
                contract("Allianz", "Pflegezusatzversicherung"),
                contract("Adobe", area = BuiltInArea.SOFTWARE, amount = 2399)
            )
        )!!
        assertEquals(listOf("Adobe", "ARAG", "Spotify", "HUK"), tip.contracts.map { it.name })
    }

    @Test
    fun reservesAreNoFrequentPayments() {
        assertNull(frequentPayments(listOf(contract("Allianz", "Rentenversicherung", reserve = true))))
        assertNull(frequentPayments(listOf(contract("Allianz", "Lebensversicherung", reserve = true))))
    }

    @Test
    fun dismissedTipComesBackWithAnotherContract() {
        val huk = contract("HUK", "Privathaftpflicht")
        val tip = frequentPayments(listOf(huk))!!
        val decisions = TipDecisions(dismissed = mapOf(tip.id to tip.facts))
        assertTrue(decisions.isDismissed(frequentPayments(listOf(huk))!!))
        assertFalse(decisions.isDismissed(frequentPayments(listOf(huk, contract("Spotify")))!!))
    }
}
