package org.totschnig.myexpenses.next.data.tips

import org.totschnig.myexpenses.next.core.model.words.Words
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.data.merchants.EATING_OUT
import org.totschnig.myexpenses.next.data.merchants.supermarketOf
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

/*
 * Tips about spending outside of contracts. They look at the debits of the analysis without
 * contract payments and movements between own accounts.
 */

/** Absolute, in minor units of the home currency */
private val List<ContractTransaction>.total: Long get() = sumOf { it.amount.absoluteValue }

/**
 * Transactions of one payee, or one category
 *
 * @param transactions oldest first
 */
data class SpendingGroup(val key: String, val label: String, val transactions: List<ContractTransaction>) {
    val amount: Long get() = transactions.total
    val count: Int get() = transactions.size
    val average: Long get() = amount / count
}

/**
 * Many small purchases, e.g. coffee to go or at the bakery, which add up
 *
 * @param payees the ones spent most at, highest first
 */
data class SmallPurchases(val count: Int, override val yearlyAmount: Long, val payees: List<SpendingGroup>) : SavingTip {
    override val id: String get() = ID
    val monthlyAmount: Long get() = yearlyAmount / 12
    override val facts: Set<String> get() = setOf("level:${level(yearlyAmount)}")
    override val transactionGroups get() = payees.associate { it.key to it.transactions }

    companion object {
        const val ID = "SMALL_PURCHASES"

        /** A purchase below this is small, in minor units */
        const val LIMIT = 1000L

        /** On average five small purchases a month */
        const val MIN_COUNT = 60

        const val MIN_AMOUNT = 10000L
        const val MAX_PAYEES = 5
    }
}

/**
 * Eating out and delivery services cost more within the last [DAYS] days than usual
 *
 * @param recent spent within the last [DAYS] days, in minor units
 * @param usual spent within [DAYS] days on average over the year before
 */
data class EatingOut(
    val recent: Long,
    val usual: Long,
    override val yearlyAmount: Long,
    val transactions: List<ContractTransaction>,
    val month: YearMonth,
) : SavingTip {
    override val id: String get() = ID

    /** Comes back in another month that is above average */
    override val facts: Set<String> get() = setOf(month.toString())
    override val transactionGroups get() = mapOf(ID to transactions)

    companion object {
        const val ID = "EATING_OUT"
        const val DAYS = 30L

        /** How much more than usual is worth a tip */
        const val MIN_FACTOR = 1.3
        const val MIN_DIFFERENCE = 2000L

        /** Fewer visits before give no usual amount */
        const val MIN_COUNT_BEFORE = 6
    }
}

/**
 * What a purchase costs on average at each supermarket chain the user shops at regularly
 *
 * @param chains most expensive purchase on average first
 */
data class Supermarkets(val chains: List<SpendingGroup>) : SavingTip {
    override val id: String get() = ID
    override val yearlyAmount: Long get() = chains.sumOf { it.amount }

    /** Comes back when the user shops at another chain */
    override val facts: Set<String> get() = chains.mapTo(HashSet()) { it.label }
    override val transactionGroups get() = chains.associate { it.key to it.transactions }

    companion object {
        const val ID = "SUPERMARKETS"

        /** Purchases within the year needed at a chain */
        const val MIN_COUNT = 5
    }
}

/**
 * Categories the user spent much more on in the last complete month than on average
 *
 * @param categories the transactions of [month], highest difference first
 * @param averages spent per month on average in the year before, by [SpendingGroup.key]
 */
data class Outliers(val month: YearMonth, val categories: List<SpendingGroup>, val averages: Map<String, Long>) : SavingTip {
    override val id: String get() = ID
    override val yearlyAmount: Long get() = categories.sumOf { it.amount - averages.getValue(it.key) }
    override val facts: Set<String> get() = categories.mapTo(HashSet()) { "$month:${it.label}" }
    override val transactionGroups get() = categories.associate { it.key to it.transactions }

    companion object {
        const val ID = "OUTLIERS"
        const val MIN_FACTOR = 1.5
        const val MIN_DIFFERENCE = 5000L

        /** Months with spending needed before [month] to know the average */
        const val MIN_MONTHS = 3
    }
}

/**
 * Debits that are no contract payments and no movements between own accounts, until [today]
 *
 * @param contractPayments ids of the payments of all contracts, suggestions included
 * @param excluded ids of debits that do not recur or were paid back, see
 * [org.totschnig.myexpenses.next.tags.TagMeaning.isExcludedFromSpending]
 */
internal fun spending(
    transactions: List<ContractTransaction>,
    contractPayments: Set<Long>,
    today: LocalDate,
    excluded: Set<Long> = emptySet(),
) = transactions.filter {
    it.amount < 0 && it.targetAccountId == null && it.id !in contractPayments && it.id !in excluded &&
            !it.date.isAfter(today)
}

/** The last year, as other tips */
private fun List<ContractTransaction>.lastYear(today: LocalDate) = filter { it.date.isAfter(today.minusYears(1)) }

/** Payee, or purpose if there is none */
private fun ContractTransaction.payeeLabel() = payeeName?.takeIf { it.isNotBlank() } ?: comment?.takeIf { it.isNotBlank() }

/**
 * @param spending see [spending]
 */
internal fun smallPurchases(spending: List<ContractTransaction>, today: LocalDate): SmallPurchases? {
    val small = spending.lastYear(today).filter { it.amount.absoluteValue < SmallPurchases.LIMIT }
    if (small.size < SmallPurchases.MIN_COUNT || small.total < SmallPurchases.MIN_AMOUNT) return null
    val payees = small.filter { it.payeeLabel() != null }
        .groupBy { it.payeeLabel()!!.trim() }
        .map { (payee, transactions) -> SpendingGroup("${SmallPurchases.ID}:$payee", payee, transactions) }
        .sortedByDescending { it.amount }
        .take(SmallPurchases.MAX_PAYEES)
    return SmallPurchases(small.size, small.total, payees)
}

internal fun isEatingOut(transaction: ContractTransaction): Boolean {
    val text = Words.textOf(transaction.payeeName, transaction.comment, transaction.categoryPath)
    return EATING_OUT.any { Words.startsWord(text, it) }
}

/**
 * Compares the last [EatingOut.DAYS] days with the average of the year before
 */
internal fun eatingOut(spending: List<ContractTransaction>, today: LocalDate): EatingOut? {
    val eatingOut = spending.filter(::isEatingOut)
    val recentStart = today.minusDays(EatingOut.DAYS)
    val (recent, before) = eatingOut.partition { it.date.isAfter(recentStart) }
    val beforeStart = recentStart.minusYears(1)
    val usualBefore = before.filter { it.date.isAfter(beforeStart) }
    if (recent.isEmpty() || usualBefore.size < EatingOut.MIN_COUNT_BEFORE) return null
    // The days before, from the first visit known, at most a year
    val days = ChronoUnit.DAYS.between(maxOf(beforeStart, usualBefore.minOf { it.date }.minusDays(1)), recentStart)
    val usual = usualBefore.total * EatingOut.DAYS / days.coerceAtLeast(EatingOut.DAYS)
    val recentAmount = recent.total
    if (recentAmount < usual * EatingOut.MIN_FACTOR || recentAmount - usual < EatingOut.MIN_DIFFERENCE) return null
    return EatingOut(recentAmount, usual, eatingOut.lastYear(today).total, recent, YearMonth.from(today))
}

/**
 * Needs at least two chains with [Supermarkets.MIN_COUNT] purchases each within the last year
 */
internal fun supermarkets(spending: List<ContractTransaction>, today: LocalDate): Supermarkets? {
    val chains = spending.lastYear(today)
        .groupBy { supermarketOf(it.payeeName) }
        .mapNotNull { (chain, transactions) ->
            chain?.takeIf { transactions.size >= Supermarkets.MIN_COUNT }
                ?.let { SpendingGroup("${Supermarkets.ID}:$it", it, transactions) }
        }
        .sortedByDescending { it.average }
    return chains.takeIf { it.size >= 2 }?.let { Supermarkets(it) }
}

/** The main category, e.g. "Essen" of "Essen > Restaurant" */
private fun ContractTransaction.mainCategory() = categoryPath?.substringBefore('>')?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Compares each main category in the last complete month with its average of the year before.
 * Months before the first spending known do not count, so that a short history gives no low average.
 */
internal fun outliers(spending: List<ContractTransaction>, today: LocalDate): Outliers? {
    val month = YearMonth.from(today).minusMonths(1)
    val start = month.minusYears(1)
    val byMonth = spending.filter { YearMonth.from(it.date) in start..month }.groupBy { YearMonth.from(it.date) }
    val firstMonth = byMonth.keys.minOrNull() ?: return null
    val monthsBefore = ChronoUnit.MONTHS.between(firstMonth, month)
    if (monthsBefore < Outliers.MIN_MONTHS) return null
    val before = byMonth.filterKeys { it != month }.values.flatten()
    val averages = mutableMapOf<String, Long>()
    val categories = byMonth[month].orEmpty()
        .groupBy { it.mainCategory() }
        .mapNotNull { (category, transactions) ->
            category ?: return@mapNotNull null
            val average = before.filter { it.mainCategory() == category }.total / monthsBefore
            val group = SpendingGroup("${Outliers.ID}:$category", category, transactions)
            if (group.amount >= average * Outliers.MIN_FACTOR && group.amount - average >= Outliers.MIN_DIFFERENCE) {
                averages[group.key] = average
                group
            } else null
        }
        .sortedByDescending { it.amount - averages.getValue(it.key) }
    return categories.takeIf { it.isNotEmpty() }?.let { Outliers(month, it, averages) }
}
