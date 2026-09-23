package org.totschnig.myexpenses.next.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.Period

class OwnTransferTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 25)
    private val giro = 1L
    private val creditCard = 2L
    private val savings = 3L
    private val daily = setOf(giro, creditCard)
    private val ownIbans = mapOf("DE89370400440532013000" to savings, "DE02120300000000202051" to giro)
    private var nextId = 1L

    private fun payment(
        amount: Long,
        account: Long = giro,
        target: Long? = null,
        payeeId: Long? = 9,
        last: LocalDate = today.minusDays(3),
        monthsBack: Int = 0,
    ) = ContractTransaction(
        id = nextId++, date = last.minus(Period.ofMonths(monthsBack)), amount = amount, accountId = account,
        payeeId = payeeId, payeeName = if (target != null) "→ Tagesgeld" else "Payee $payeeId", targetAccountId = target
    )

    @Test
    fun recognizesOwnTransfers() {
        // Transfer in the app
        assertEquals(savings, ownTransferTarget(giro, savings, null, ownIbans))
        // Imported from the bank, IBAN written with spaces and in lower case
        assertEquals(savings, ownTransferTarget(giro, null, "de89 3704 0044 0532 0130 00", ownIbans))
        // Payment to someone else
        assertNull(ownTransferTarget(giro, null, "DE75512108001245126199", ownIbans))
        assertNull(ownTransferTarget(giro, null, null, ownIbans))
        // The own account itself is no target
        assertNull(ownTransferTarget(giro, null, "DE02120300000000202051", ownIbans))
    }

    @Test
    fun keepsOnlyMovementsFromDailyAccountsToOthers() {
        val toSavings = payment(-10000, target = savings)
        val receivedOnSavings = payment(10000, account = savings, target = giro)
        val payingCreditCard = payment(-50000, target = creditCard)
        val withdrawal = payment(20000, target = savings)
        val normal = payment(-4000)
        val kept = listOf(toSavings, receivedOnSavings, payingCreditCard, withdrawal, normal)
            .withoutMovementsOtherThanReserves(daily)
        assertEquals(listOf(toSavings, normal), kept)
    }

    @Test
    fun detectsStandingOrderToSavingsAccountAsReserve() {
        // Payee is the user, as imported from the bank: grouped by target account nevertheless
        val standingOrder = (0 until 6).map { payment(-10000, target = savings, monthsBack = it) }.reversed()
        val contract = ContractDetector(today).detect(standingOrder).single()
        assertEquals("a$savings|MONTHLY", contract.signature)
        assertEquals("→ Tagesgeld", contract.name)
        assertTrue(contract.isOwnTransfer)
        assertTrue(contract.isReserve)
        val rule = ContractRule.of(contract, standingOrder)
        assertEquals(savings, rule.targetAccountId)
        assertTrue(rule.payeeIds.isEmpty())
    }

    @Test
    fun rulesForPayeesAndForAccountsDoNotMix() {
        val byPayee = ContractRule("p", ContractRule.Kind.CONTRACT, ContractDirection.EXPENSE, setOf(9), interval = ContractInterval.MONTHLY)
        val byAccount = ContractRule("a", ContractRule.Kind.CONTRACT, ContractDirection.EXPENSE, targetAccountId = savings, interval = ContractInterval.MONTHLY)
        val toSavings = payment(-10000, target = savings)
        val normal = payment(-4000)
        assertFalse(byPayee.matches(toSavings))
        assertTrue(byPayee.matches(normal))
        assertTrue(byAccount.matches(toSavings))
        assertFalse(byAccount.matches(normal))
    }

    @Test
    fun transferWithoutPayeeCanBeMarked() {
        val transfer = payment(-10000, target = savings, payeeId = null)
        val info = ContractAnalysis.of(listOf(transfer), emptyList(), today).infoFor(transfer.id)
        assertEquals(PaymentContractInfo.Markable(isIncome = false), info)
    }
}
