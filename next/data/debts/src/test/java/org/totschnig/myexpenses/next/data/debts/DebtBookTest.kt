package org.totschnig.myexpenses.next.data.debts

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/**
 * Overview, history and form of debts. Unlike the classic history (DebtViewModelTest of
 * :myExpenses), payments in other currencies are converted and voided ones left out.
 */
class DebtBookTest {

    private val euro = CurrencyInfo("EUR", "€", 2)
    private val dollar = CurrencyInfo("USD", "$", 2)
    private val day = LocalDate.of(2025, 1, 10)

    private fun debt(
        id: Long,
        label: String,
        amount: Long,
        payee: String = "Alice",
        currency: CurrencyInfo = euro,
        equivalentAmount: Long? = null,
        isSealed: Boolean = false
    ) = Debt(
        id,
        label,
        payee.length.toLong(),
        payee,
        currency,
        amount,
        equivalentAmount,
        isSealed,
        date = day
    )

    private fun payment(debtId: Long, amount: Long, homeAmount: Long = amount, days: Long = 1) =
        DebtTransaction(
            id = debtId * 100 + days,
            amount = amount,
            currency = "EUR",
            homeAmount = homeAmount,
            debtId = debtId,
            date = day.plusDays(days)
        )

    private fun labels(overview: DebtOverview) = overview.debts.map { it.debt.label }

    @Test
    fun `overview leaves out closed and settled debts unless all are shown`() {
        val debts = listOf(
            debt(1, "Open", 1000),
            debt(2, "Settled", 500),
            debt(3, "Closed", 300, isSealed = true)
        )
        val payments = listOf(payment(2, 500))

        val open = debtOverview(debts, payments, "EUR", DebtSort.Label, showAll = false)
        val all = debtOverview(debts, payments, "EUR", DebtSort.Label, showAll = true)

        assertEquals(listOf("Open"), open.debts.map { it.debt.label })
        assertEquals(listOf("Closed", "Open", "Settled"), all.debts.map { it.debt.label })
        assertEquals(1000L, open.total)
        assertEquals(1000L, all.total)
    }

    @Test
    fun `the total counts foreign debts by their home value`() {
        val debts = listOf(
            debt(1, "Home", 1000),
            debt(2, "Dollars", -100, currency = dollar, equivalentAmount = -90)
        )
        assertEquals(910L, debtOverview(debts, emptyList(), "EUR", DebtSort.Label, false).total)
    }

    @Test
    fun `sorting by person groups payees, by amount puts what is owed to me first`() {
        val debts = listOf(
            debt(1, "B", 100, payee = "Bob"),
            debt(2, "A", -300, payee = "Alice"),
            debt(3, "C", 200, payee = "Alice")
        )
        assertEquals(
            listOf("A", "C", "B"),
            labels(debtOverview(debts, emptyList(), "EUR", DebtSort.Payee, false))
        )
        assertEquals(
            listOf("C", "B", "A"),
            labels(debtOverview(debts, emptyList(), "EUR", DebtSort.Balance, false))
        )
    }

    @Test
    fun `history starts with the debt and runs by date without voided payments`() {
        val debt = debt(1, "Loan", 10000)
        val payments = listOf(
            payment(1, 3000, days = 5),
            payment(1, -500, days = 2),
            payment(1, 100, days = 3).copy(isVoid = true)
        )
        assertEquals(
            listOf(
                DebtEntry(null, day, null, 10000),
                DebtEntry(102, day.plusDays(2), -500, 10500),
                DebtEntry(105, day.plusDays(5), 3000, 7500)
            ),
            debt.history(payments, "EUR")
        )
    }

    @Test
    fun `a payment that cannot be converted keeps the balance`() {
        val debt = debt(1, "Dollars", 1000, currency = dollar)
        assertEquals(
            listOf(DebtEntry(null, day, null, 1000), DebtEntry(101, day.plusDays(1), null, 1000)),
            debt.history(listOf(payment(1, 50)), "EUR")
        )
    }

    @Test
    fun `a valid draft gives signed values`() {
        val draft = DebtDraft(
            payee = " Alice ",
            label = "Bike",
            isOwedToMe = false,
            amountText = "1.234,5",
            currency = "USD",
            equivalentText = "1100",
            date = day
        )
        assertEquals(
            DebtCheck.Valid(DebtValues(0, "Alice", "Bike", "", "USD", -123450, -110000, day)),
            draft.validate(2, euro)
        )
    }

    @Test
    fun `an incomplete draft lists what is missing`() {
        val draft = DebtDraft(amountText = "0", currency = "USD", date = day)
        assertEquals(
            DebtCheck.Invalid(
                DebtProblem.entries.toSet()
            ),
            draft.validate(2, euro)
        )
    }

    @Test
    fun `a saved debt goes back into the form as typed`() {
        val debt = debt(1, "Trip", -12345, currency = dollar, equivalentAmount = -11000)
        assertEquals(
            DebtDraft(1, "Alice", "Trip", "", false, "123.45", "USD", "110.00", day),
            debt.toDraft(euro)
        )
    }
}
