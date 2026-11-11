package org.totschnig.myexpenses.next.data.debts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/**
 * Balance and payments of debts. Unlike the classic screens (see DebtSumTest and DebtViewModelTest
 * of :myExpenses), voided transactions do not count and payments in a third currency are
 * converted at the rate of the debt instead of being added as they are.
 */
class DebtsTest {

    private val euro = CurrencyInfo("EUR", "€", 2)
    private val dollar = CurrencyInfo("USD", "$", 2)

    private fun debt(
        id: Long = 1,
        amount: Long = 10000,
        currency: CurrencyInfo = euro,
        equivalentAmount: Long? = null,
        payeeId: Long = 7,
        isSealed: Boolean = false
    ) = Debt(id, "Loan $id", payeeId, "Alice", currency, amount, equivalentAmount, isSealed)

    private fun payment(
        amount: Long,
        debtId: Long? = 1,
        currency: String = "EUR",
        homeAmount: Long = amount,
        id: Long = amount,
        isVoid: Boolean = false
    ) = DebtTransaction(id, amount, currency, homeAmount, 7, debtId, isVoid)

    @Test
    fun `balance is the amount minus the linked payments`() {
        val payments =
            listOf(payment(3000), payment(-1000), payment(77, debtId = 2), payment(88, null))
        assertEquals(DebtBalance(8000), debt().balance(payments, "EUR"))
    }

    @Test
    fun `voided payments do not count`() {
        assertEquals(DebtBalance(10000), debt().balance(listOf(payment(100, isVoid = true)), "EUR"))
    }

    @Test
    fun `the transaction about to change is left out`() {
        val payments = listOf(payment(100, id = 1), payment(30, id = 2))
        assertEquals(DebtBalance(9970), debt().balance(payments, "EUR", without = 1))
    }

    @Test
    fun `a debt in the home currency counts foreign payments by their home value`() {
        val payments = listOf(payment(1000, currency = "USD", homeAmount = 2000))
        assertEquals(DebtBalance(8000), debt().balance(payments, "EUR"))
    }

    @Test
    fun `a foreign debt converts payments of other currencies at its rate`() {
        // 1000 dollars worth 2000 euros: 10 francs worth 300 euros pay 150 dollars
        val debt = debt(amount = 1000, currency = dollar, equivalentAmount = 2000)
        val payments = listOf(
            payment(100, currency = "USD", homeAmount = 200),
            payment(10, currency = "CHF", homeAmount = 300)
        )
        assertEquals(DebtBalance(1000 - 100 - 150), debt.balance(payments, "EUR"))
    }

    @Test
    fun `a foreign debt without home value leaves other currencies out`() {
        val debt = debt(amount = 1000, currency = dollar)
        val payments = listOf(payment(100, currency = "USD"), payment(10, currency = "EUR"))
        assertEquals(DebtBalance(900, unconverted = 1), debt.balance(payments, "EUR"))
    }

    @Test
    fun `steps of a payment follow the signs`() {
        assertEquals(DebtStep.Receive, DebtOutcome(before = 70, payment = 30).step)
        assertEquals(DebtStep.BorrowMore, DebtOutcome(before = -70, payment = 30).step)
        assertEquals(DebtStep.BorrowMore, DebtOutcome(before = 0, payment = 30).step)
        assertEquals(DebtStep.Pay, DebtOutcome(before = -70, payment = -30).step)
        assertEquals(DebtStep.LendMore, DebtOutcome(before = 70, payment = -30).step)
        assertEquals(DebtStep.LendMore, DebtOutcome(before = 0, payment = -30).step)
        assertNull(DebtOutcome(before = 70, payment = 0).step)
    }

    @Test
    fun `a payment that settles or turns the debt around pays it off`() {
        assertEquals(40, DebtOutcome(before = 70, payment = 30).after)
        assertFalse(DebtOutcome(before = 70, payment = 30).isPaidOff)
        assertTrue(DebtOutcome(before = 70, payment = 70).isPaidOff)
        assertTrue(DebtOutcome(before = 70, payment = 100).isPaidOff)
        assertTrue(DebtOutcome(before = -70, payment = -70).isPaidOff)
        assertFalse(DebtOutcome(before = 0, payment = 30).isPaidOff)
    }

    @Test
    fun `offers open debts of the payee in the currency of the account or the home currency`() {
        val transaction = payment(3000, debtId = null, id = 99)
        val debts = listOf(
            debt(id = 1),
            debt(id = 2, isSealed = true),
            debt(id = 3, currency = dollar, equivalentAmount = 20000),
            debt(id = 4, payeeId = 8)
        )
        val choice = debtChoice(transaction, null, debts, emptyList(), "EUR")
        assertEquals(
            DebtChoice.Offered(listOf(DebtChoice.Linked(debts[0], DebtOutcome(10000, 3000)))),
            choice
        )
    }

    @Test
    fun `a debt in the home currency is offered for a foreign transaction`() {
        val transaction = payment(1000, debtId = null, currency = "USD", homeAmount = 2000, id = 99)
        val choice = debtChoice(transaction, null, listOf(debt()), emptyList(), "EUR")
        assertEquals(
            DebtChoice.Offered(listOf(DebtChoice.Linked(debt(), DebtOutcome(10000, 2000)))),
            choice
        )
    }

    @Test
    fun `nothing is offered without a fitting debt`() {
        val transaction = payment(3000, debtId = null, id = 99)
        val choice = debtChoice(transaction, null, emptyList(), emptyList(), "EUR")
        assertEquals(DebtChoice.None, choice)
    }

    @Test
    fun `a linked transaction shows its debt without counting itself twice`() {
        val linked = debt(isSealed = true)
        val transaction = payment(3000, id = 99)
        val payments = listOf(transaction, payment(1000, id = 5))
        assertEquals(
            DebtChoice.Linked(linked, DebtOutcome(9000, 3000)),
            debtChoice(transaction, linked, listOf(linked), payments, "EUR")
        )
    }

    @Test
    fun `a payment that cannot be converted has no outcome`() {
        val linked = debt(currency = dollar)
        val transaction = payment(3000, id = 99)
        assertEquals(
            DebtChoice.Linked(linked, null),
            debtChoice(transaction, linked, listOf(linked), emptyList(), "EUR")
        )
    }
}
