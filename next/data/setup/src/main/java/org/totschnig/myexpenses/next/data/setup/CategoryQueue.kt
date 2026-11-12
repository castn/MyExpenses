package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
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
 * @param purpose of the last of them that has one, as the bank wrote it, which helps to tell what
 * the payee is, e.g. the merchant behind PayPal
 */
data class SetupPayee(
    val counterpart: Counterpart,
    val amount: Long,
    val transactionIds: List<Long>,
    val since: LocalDate,
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
 * The payees of the categories step, the most money first
 *
 * @param required decided on, they bring [coverage] up to the target of [categoryQueue]
 * @param optional the others, decided on to come closer to all of the money
 */
data class CategoryQueue(
    val coverage: CategoryCoverage,
    val required: List<SetupPayee>,
    val optional: List<SetupPayee>
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
 * decided on by payee.
 */
fun categoryQueue(
    ledger: Ledger,
    unclear: Set<String>,
    target: Double = TARGET_SHARE
): CategoryQueue {
    val debits = ledger.transactions.filter { it.isCategorizableDebit() }
    val open = debits.filter { it.categoryId == null }
        .map { it to it.categoryInput().counterpart() }
    val (unclearDebits, toDecide) = open.partition { (_, counterpart) ->
        counterpart?.key in unclear
    }
    val coverage = CategoryCoverage(
        spent = debits.sumOf { -it.homeAmount },
        categorized = debits.filter { it.categoryId != null }.sumOf { -it.homeAmount },
        unclear = unclearDebits.sumOf { (transaction, _) -> -transaction.homeAmount }
    )
    val payees = toDecide
        .mapNotNull { (transaction, counterpart) -> counterpart?.let { transaction to it } }
        .groupBy { (_, counterpart) -> counterpart.key }
        .map { (_, group) ->
            SetupPayee(
                counterpart = group.first().second,
                amount = group.sumOf { (transaction, _) -> -transaction.homeAmount },
                transactionIds = group.map { (transaction, _) -> transaction.id },
                since = group.minOf { (transaction, _) -> transaction.date },
                purpose = group.map { it.first }
                    .filter { !it.comment.isNullOrBlank() }
                    .maxByOrNull { it.date }?.comment
            )
        }
        .sortedWith(compareByDescending<SetupPayee> { it.amount }.thenBy { it.counterpart.name })
    val goal = target * coverage.countable
    var covered = coverage.categorized.toDouble()
    val required = payees.takeWhile { payee ->
        (covered < goal).also { covered += payee.amount }
    }
    return CategoryQueue(coverage, required, payees.drop(required.size))
}

/**
 * Debits that can have a category, as in [org.totschnig.myexpenses.next.data.categories.categoryInputs]:
 * no transfers, also not between own accounts, no splits, none the database would not change
 */
internal fun LedgerTransaction.isCategorizableDebit() = amount < 0 && transferAccountId == null &&
    targetAccountId == null && !isSplit && !isSealed
