package org.totschnig.myexpenses.next.data.debts

import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.absoluteValue
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.transactions.parseAmountText

/**
 * A debt with what is left of it
 *
 * @param homeBalance [balance] in the home currency: the home value of the debt minus the home
 * values of its payments
 */
data class DebtSummary(val debt: Debt, val balance: DebtBalance, val homeBalance: Long) {
    val isSettled: Boolean get() = balance.balance == 0L
}

fun Debt.summary(payments: List<DebtTransaction>, homeCurrency: String): DebtSummary {
    val counted = payments.filter { it.debtId == id && !it.isVoid }
    val homeValue = if (currency.code == homeCurrency) amount else equivalentAmount ?: amount
    val homeBalance = homeValue - counted.sumOf { it.homeAmount }
    return DebtSummary(this, balance(payments, homeCurrency), homeBalance)
}

/** Order of the debts in the overview */
enum class DebtSort {
    /** By payee, then label; debts of the same payee are grouped */
    Payee,

    /** By label */
    Label,

    /** Largest first, what is owed to me before what I owe */
    Balance
}

/**
 * The debts in the overview
 *
 * @param total what is owed to me minus what I owe, of the open debts, in the home currency
 */
data class DebtOverview(val debts: List<DebtSummary>, val total: Long)

/**
 * The overview: without [showAll] closed and settled debts are left out, the total counts open
 * debts only
 */
fun debtOverview(
    debts: List<Debt>,
    payments: List<DebtTransaction>,
    homeCurrency: String,
    sort: DebtSort,
    showAll: Boolean
): DebtOverview {
    val summaries = debts.map { it.summary(payments, homeCurrency) }
    val shown = summaries.filter { showAll || (!it.debt.isSealed && !it.isSettled) }
    val sorted = when (sort) {
        DebtSort.Payee -> shown.sortedWith(
            compareBy<DebtSummary>({ it.debt.payeeName.lowercase() }, { it.debt.label.lowercase() })
        )

        DebtSort.Label -> shown.sortedBy { it.debt.label.lowercase() }

        DebtSort.Balance -> shown.sortedByDescending { it.homeBalance }
    }
    return DebtOverview(sorted, summaries.filter { !it.debt.isSealed }.sumOf { it.homeBalance })
}

/**
 * A line of the history of a debt: the debt itself first, then its payments by date
 *
 * @param transactionId null for the line of the debt itself
 * @param payment in the currency of the debt, positive if I receive money; null for the debt
 * itself and for a payment that cannot be converted
 * @param balance what is left after the line
 */
data class DebtEntry(
    val transactionId: Long?,
    val date: LocalDate?,
    val payment: Long?,
    val balance: Long
)

/** The debt and its payments, voided ones left out, with what is left after each */
fun Debt.history(payments: List<DebtTransaction>, homeCurrency: String): List<DebtEntry> {
    var balance = amount
    val counted = payments.filter { it.debtId == id && !it.isVoid }.sortedBy { it.date }
    return listOf(DebtEntry(null, date, null, amount)) + counted.map {
        val payment = inDebtCurrency(it, homeCurrency)
        balance -= payment ?: 0L
        DebtEntry(it.id, it.date, payment, balance)
    }
}

/**
 * A debt as it is entered, the texts as typed
 *
 * @param id 0 for a new debt
 * @param isOwedToMe true if the payee owes me, false if I owe the payee
 * @param currency the code of the currency
 * @param equivalentText the value in the home currency, only for a debt in another currency;
 * [Serializable] for the saved state
 */
data class DebtDraft(
    val id: Long = 0,
    val payee: String = "",
    val label: String = "",
    val description: String = "",
    val isOwedToMe: Boolean = true,
    val amountText: String = "",
    val currency: String,
    val equivalentText: String = "",
    val date: LocalDate
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** A debt as it is saved, the amounts signed like [Debt.amount] */
data class DebtValues(
    val id: Long,
    val payee: String,
    val label: String,
    val description: String,
    val currency: String,
    val amount: Long,
    val equivalentAmount: Long?,
    val date: LocalDate
)

/** What is missing or wrong in a [DebtDraft] */
enum class DebtProblem { Payee, Label, Amount, Equivalent }

/** The draft of [debt], to change it */
fun Debt.toDraft(home: CurrencyInfo) = DebtDraft(
    id = id,
    payee = payeeName,
    label = label,
    description = description,
    isOwedToMe = amount >= 0,
    amountText = amountText(amount.absoluteValue, currency.fractionDigits),
    currency = currency.code,
    equivalentText = equivalentAmount
        ?.let { amountText(it.absoluteValue, home.fractionDigits) }
        .orEmpty(),
    date = date ?: LocalDate.now()
)

/** E.g. "12.50" for 1250 with two fraction digits, as typed into a field */
private fun amountText(minor: Long, fractionDigits: Int) =
    BigDecimal.valueOf(minor, fractionDigits).toPlainString()

/**
 * The values to save, or what is missing or wrong: payee, label and an amount other than zero are
 * needed, and for a debt in another currency its value in the home currency
 *
 * @param fractionDigits of the currency of the draft
 */
fun DebtDraft.validate(fractionDigits: Int, home: CurrencyInfo): DebtCheck {
    val amount = parseAmountText(amountText, fractionDigits)?.takeIf { it != 0L }
    val isForeign = currency != home.code
    val equivalent = if (isForeign) parseAmountText(equivalentText, home.fractionDigits) else null
    val problems = buildSet {
        if (payee.isBlank()) add(DebtProblem.Payee)
        if (label.isBlank()) add(DebtProblem.Label)
        if (amount == null) add(DebtProblem.Amount)
        if (isForeign && equivalent == null) add(DebtProblem.Equivalent)
    }
    if (problems.isNotEmpty() || amount == null) return DebtCheck.Invalid(problems)
    val sign = if (isOwedToMe) 1 else -1
    return DebtCheck.Valid(
        DebtValues(
            id = id,
            payee = payee.trim(),
            label = label.trim(),
            description = description.trim(),
            currency = currency,
            amount = sign * amount,
            equivalentAmount = equivalent?.let { sign * it },
            date = date
        )
    )
}

sealed interface DebtCheck {
    data class Valid(val values: DebtValues) : DebtCheck

    data class Invalid(val problems: Set<DebtProblem>) : DebtCheck
}
