package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.isCashWithdrawal
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.data.categories.categoryInput
import org.totschnig.myexpenses.next.data.categories.counterpart

/**
 * A payee to decide on in the guided setup: its debits without category get the category the
 * user chooses
 *
 * @param amount spent at the payee by the debits without category, in minor units of the home
 * currency, positive
 * @param transactionIds the debits without category
 * @param since the date of the first of them
 * @param transactions the debits without category, the latest first, to assign them one by one if
 * they are for different things
 */
data class SetupPayee(
    val counterpart: Counterpart,
    val amount: Long,
    val transactionIds: List<Long>,
    val since: LocalDate,
    val transactions: List<SetupTransaction> = emptyList()
) {
    /**
     * The last [RECENT] [transactions], whose purposes help to tell what the payee is, e.g. the
     * merchant behind PayPal
     */
    val recent: List<SetupTransaction> get() = transactions.take(RECENT)

    companion object {
        const val RECENT = 3
    }
}

/**
 * A debit of a [SetupPayee]
 *
 * @param amount in minor units of the home currency, positive
 * @param purpose as the bank wrote it
 */
data class SetupTransaction(
    val id: Long,
    val date: LocalDate,
    val amount: Long,
    val purpose: String? = null
)

/**
 * How much of the money spent has a category, in minor units of the home currency, positive
 *
 * @param spent by all debits that can have a category
 * @param categorized of [spent] by debits with a category
 * @param unclear of [spent] by debits of payees the user knows no category for, which do not count
 */
data class CategoryCoverage(val spent: Long, val categorized: Long, val unclear: Long = 0) {
    /** The money that can get a category */
    val countable: Long get() = spent - unclear

    /** Share of [countable] with a category, 1 if there is nothing to categorize */
    val share: Double get() = if (countable <= 0) 1.0 else categorized.toDouble() / countable

    /** Whether [share] is at least [target] */
    fun reaches(target: Double) = share >= target
}

/**
 * The cash withdrawals without category, decided on once for all of them rather than with their
 * payee, e.g. the own bank
 *
 * @param amount taken, in minor units of the home currency, positive
 * @param since the date of the first withdrawal
 * @param until the date of the last one
 */
data class SetupCash(
    val amount: Long,
    val transactionIds: List<Long>,
    val since: LocalDate,
    val until: LocalDate
) {
    /** The calendar months from [since] to [until], both counted */
    val months: Long
        get() = ChronoUnit.MONTHS.between(YearMonth.from(since), YearMonth.from(until)) + 1

    /** About the money taken in a month */
    val perMonth: Long get() = amount / months

    companion object {
        /** Stands for the cash withdrawals where the setup keeps keys of payees, see [Counterpart.key] */
        const val KEY = "cash"
    }
}

/**
 * The payees of the categories step, the most money first
 *
 * @param required decided on, they bring [coverage] up to the target of [categoryQueue]
 * @param optional the others, decided on to come closer to all of the money
 * @param cash the cash withdrawals without category, null if there are none
 * @param isCashRequired [cash] is needed for the target, like the payees in [required]
 */
data class CategoryQueue(
    val coverage: CategoryCoverage,
    val required: List<SetupPayee>,
    val optional: List<SetupPayee>,
    val cash: SetupCash? = null,
    val isCashRequired: Boolean = false
) {
    val all: List<SetupPayee> get() = required + optional
}

/** Of the money spent, this share is to have a category at the end of the categories step */
const val TARGET_SHARE = 0.8

/**
 * The payees whose debits have no category, by the money spent there over the whole ledger, so that
 * a few decisions cover most of the money: rent and supermarket come first. Payees the user knows
 * no category for ([unclear], see [Counterpart.key]) are left out and their money does not count.
 * Debits without counterpart, e.g. through a payment service without merchant, count, but cannot be
 * decided on by payee. Cash withdrawals are not decided on with their payee, e.g. the own bank,
 * but once for all of them, see [SetupCash].
 */
fun categoryQueue(
    ledger: Ledger,
    unclear: Set<String>,
    target: Double = TARGET_SHARE
): CategoryQueue {
    val debits = ledger.transactions.filter { it.isCategorizableDebit() }
    val (cashDebits, open) = debits.filter { it.categoryId == null }
        .partition { it.isCashWithdrawal }
    val (unclearDebits, toDecide) = open.map { it to it.categoryInput().counterpart() }
        .partition { (_, counterpart) -> counterpart?.key in unclear }
    val coverage = CategoryCoverage(
        spent = debits.sumOf { -it.homeAmount },
        categorized = debits.filter { it.categoryId != null }.sumOf { -it.homeAmount },
        unclear = unclearDebits.sumOf { (transaction, _) -> -transaction.homeAmount }
    )
    val payees = setupPayees(toDecide)
    val cash = cashDebits.takeIf { it.isNotEmpty() }?.let { withdrawals ->
        SetupCash(
            amount = withdrawals.sumOf { -it.homeAmount },
            transactionIds = withdrawals.map { it.id },
            since = withdrawals.minOf { it.date },
            until = withdrawals.maxOf { it.date }
        )
    }
    // The payees and the cash withdrawals, the most money first, until the target is reached
    val goal = target * coverage.countable
    var covered = coverage.categorized.toDouble()
    val required = mutableListOf<SetupPayee>()
    var isCashRequired = false
    while (covered < goal) {
        val payee = payees.getOrNull(required.size)
        if (cash != null && !isCashRequired && cash.amount >= (payee?.amount ?: 0)) {
            isCashRequired = true
            covered += cash.amount
        } else if (payee != null) {
            required += payee
            covered += payee.amount
        } else {
            break
        }
    }
    return CategoryQueue(coverage, required, payees.drop(required.size), cash, isCashRequired)
}

private fun setupPayees(debits: List<Pair<LedgerTransaction, Counterpart?>>): List<SetupPayee> {
    val byLatest = compareByDescending<LedgerTransaction> { it.date }.thenByDescending { it.id }
    return debits
        .mapNotNull { (transaction, counterpart) -> counterpart?.let { transaction to it } }
        .groupBy { (_, counterpart) -> counterpart.key }
        .map { (_, group) ->
            SetupPayee(
                counterpart = group.first().second,
                amount = group.sumOf { (transaction, _) -> -transaction.homeAmount },
                transactionIds = group.map { (transaction, _) -> transaction.id },
                since = group.minOf { (transaction, _) -> transaction.date },
                transactions = group.map { it.first }
                    .sortedWith(byLatest)
                    .map {
                        SetupTransaction(
                            it.id,
                            it.date,
                            -it.homeAmount,
                            it.comment?.takeIf { comment -> comment.isNotBlank() }
                        )
                    }
            )
        }
        .sortedWith(compareByDescending<SetupPayee> { it.amount }.thenBy { it.counterpart.name })
}

/**
 * Debits that can have a category, as in [org.totschnig.myexpenses.next.data.categories.categoryInputs]:
 * no transfers, also not between own accounts, no splits, none the database would not change
 */
internal fun LedgerTransaction.isCategorizableDebit() = amount < 0 && transferAccountId == null &&
    targetAccountId == null && !isSplit && !isSealed
