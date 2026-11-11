package org.totschnig.myexpenses.next.data.debts

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.absoluteValue
import kotlin.math.sign
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/**
 * A debt with a payee. The amounts are in minor units of [currency], positive if the payee owes
 * me, negative if I owe the payee.
 *
 * @param equivalentAmount [amount] in the home currency, for a debt in another currency
 * @param isSealed closed: the database refuses changes of the debt and of its transactions
 * @param date the day the debt was taken on, null if not known
 */
data class Debt(
    val id: Long,
    val label: String,
    val payeeId: Long,
    val payeeName: String,
    val currency: CurrencyInfo,
    val amount: Long,
    val equivalentAmount: Long? = null,
    val isSealed: Boolean = false,
    val description: String = "",
    val date: LocalDate? = null
)

/**
 * A transaction as the debts see it: an income reduces what the payee owes me, an expense what I
 * owe the payee
 *
 * @param currency the code of the currency of its account
 * @param homeAmount [amount] in the home currency
 * @param debtId the debt it pays, null if none
 * @param date null if not known
 */
data class DebtTransaction(
    val id: Long,
    val amount: Long,
    val currency: String,
    val homeAmount: Long,
    val payeeId: Long? = null,
    val debtId: Long? = null,
    val isVoid: Boolean = false,
    val date: LocalDate? = null
)

/**
 * [amount] of a transaction in [currency] in the currency of the debt: unchanged in the same
 * currency, its value in the home currency for a debt in the home currency, else the value in the
 * home currency at the rate of the debt. Null if the debt in another currency has no value in the
 * home currency.
 */
fun Debt.inDebtCurrency(transaction: DebtTransaction, homeCurrency: String): Long? = when {
    transaction.currency == currency.code -> transaction.amount

    currency.code == homeCurrency -> transaction.homeAmount

    equivalentAmount == null || equivalentAmount == 0L || amount == 0L -> null

    else ->
        BigDecimal.valueOf(transaction.homeAmount)
            .multiply(BigDecimal.valueOf(amount.absoluteValue))
            .divide(BigDecimal.valueOf(equivalentAmount.absoluteValue), 0, RoundingMode.HALF_UP)
            .toLong()
}

/**
 * What is left of the debt after its [payments]
 *
 * @param unconverted payments left out, as they could not be converted, see [inDebtCurrency]
 */
data class DebtBalance(val balance: Long, val unconverted: Int = 0)

/**
 * The amount of the debt minus its payments, in its currency. Voided transactions do not count,
 * nor [without], the transaction that is about to change.
 */
fun Debt.balance(
    payments: List<DebtTransaction>,
    homeCurrency: String,
    without: Long? = null
): DebtBalance {
    val counted = payments.filter { it.debtId == id && !it.isVoid && it.id != without }
    val converted = counted.map { inDebtCurrency(it, homeCurrency) }
    return DebtBalance(amount - converted.sumOf { it ?: 0L }, converted.count { it == null })
}

/** What a payment does to a debt, from the point of view of the user */
enum class DebtStep {
    /** The payee pays back */
    Receive,

    /** I borrow more from the payee */
    BorrowMore,

    /** I pay back */
    Pay,

    /** I lend the payee more */
    LendMore
}

/**
 * A payment of a debt
 *
 * @param before the balance without the payment
 * @param payment the payment in the currency of the debt, positive if I receive money
 */
data class DebtOutcome(val before: Long, val payment: Long) {
    val after: Long get() = before - payment

    /** Null for a payment of nothing */
    val step: DebtStep?
        get() = when (payment.sign) {
            1 -> if (before > 0) DebtStep.Receive else DebtStep.BorrowMore
            -1 -> if (before < 0) DebtStep.Pay else DebtStep.LendMore
            else -> null
        }

    /** The payment settles what was owed, possibly turning the debt around */
    val isPaidOff: Boolean get() = before != 0L && after.sign != before.sign
}

/** What the details of a transaction offer about debts, see [debtChoice] */
sealed interface DebtChoice {
    /** No debt fits the transaction */
    data object None : DebtChoice

    /**
     * The transaction pays [debt]
     *
     * @param outcome null if the payment cannot be converted to the currency of the debt
     */
    data class Linked(val debt: Debt, val outcome: DebtOutcome?) : DebtChoice

    /** Open debts the transaction could pay, each with what it would change */
    data class Offered(val options: List<Linked>) : DebtChoice
}

/**
 * Which debts [transaction] pays or could pay. Only open debts of its payee are offered, in the
 * currency of its account or in the home currency, like the classic transaction form did.
 *
 * @param linked the debt the transaction pays, also if it is closed
 * @param debtsOfPayee the debts of the payee of the transaction
 * @param payments the transactions linked to these debts
 */
fun debtChoice(
    transaction: DebtTransaction,
    linked: Debt?,
    debtsOfPayee: List<Debt>,
    payments: List<DebtTransaction>,
    homeCurrency: String
): DebtChoice {
    fun option(debt: Debt) = DebtChoice.Linked(
        debt,
        debt.inDebtCurrency(transaction, homeCurrency)?.let { payment ->
            val before = debt.balance(payments, homeCurrency, without = transaction.id).balance
            DebtOutcome(before, payment)
        }
    )
    if (linked != null) return option(linked)
    val options = debtsOfPayee
        .filter { !it.isSealed && it.payeeId == transaction.payeeId }
        .filter { it.currency.code == transaction.currency || it.currency.code == homeCurrency }
        .map(::option)
    return if (options.isEmpty()) DebtChoice.None else DebtChoice.Offered(options)
}
