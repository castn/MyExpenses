package org.totschnig.myexpenses.next.contracts

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.max

/**
 * Finds recurring debits (contracts, subscriptions, …) in a list of transactions.
 *
 * 1. Debits are grouped by the template they were created from, otherwise by payee.
 *    Debits without either are ignored.
 * 2. If the whole group is debited regularly with similar amounts (price changes allowed),
 *    it is one contract. Otherwise the group is split into clusters of similar amounts
 *    (e.g. two different subscriptions from the same payee) and each cluster is checked.
 * 3. A series is regular, if the median of the days between its debits matches a
 *    [ContractInterval] and at least [MIN_REGULAR_SHARE] of all gaps match that interval.
 */
class ContractDetector(private val today: LocalDate = LocalDate.now()) {

    fun detect(transactions: List<ContractTransaction>): List<Contract> =
        transactions
            .filter { it.amount < 0 }
            .mapNotNull { transaction -> transaction.groupKey()?.let { it to transaction } }
            .groupBy({ it.first }, { it.second })
            .flatMap { (key, group) ->
                val sorted = group.sortedWith(compareBy({ it.date }, { it.id }))
                detectInGroup(key, sorted, isTemplate = key.startsWith(TEMPLATE_PREFIX))
            }
            .sortedByDescending { it.monthlyAmount }

    private fun ContractTransaction.groupKey() =
        templateId?.let { "$TEMPLATE_PREFIX$it" } ?: payeeId?.let { "$PAYEE_PREFIX$it" }

    private fun detectInGroup(
        key: String,
        group: List<ContractTransaction>,
        isTemplate: Boolean,
    ): List<Contract> {
        // Transactions created from a template belong together, whatever their amount
        if (isTemplate || group.hasSimilarAmounts(GROUP_AMOUNT_TOLERANCE)) {
            detectSeries(key, group)?.let { return listOf(it) }
        }
        if (isTemplate) return emptyList()
        return group.clusterByAmount(CLUSTER_AMOUNT_TOLERANCE)
            .mapIndexedNotNull { index, cluster -> detectSeries("$key:$index", cluster) }
    }

    /**
     * @param series sorted by date
     */
    private fun detectSeries(key: String, series: List<ContractTransaction>): Contract? {
        if (series.size < 2) return null
        val gaps = series.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val median = gaps.sorted()[gaps.size / 2]
        val interval = ContractInterval.forDays(median) ?: return null
        if (series.size < interval.minOccurrences) return null
        if (gaps.count { it in interval } < gaps.size * MIN_REGULAR_SHARE) return null

        val last = series.last()
        val daysSinceLast = ChronoUnit.DAYS.between(last.date, today)
        val grace = max(MIN_GRACE_DAYS, interval.maxDays / 10)
        return Contract(
            key = key,
            name = series.asReversed().firstNotNullOfOrNull { it.payeeName?.takeIf(String::isNotBlank) }
                ?: last.comment?.takeIf(String::isNotBlank)
                ?: last.categoryPath
                ?: "",
            interval = interval,
            transactions = series,
            nextExpectedDate = last.date.plus(interval.step),
            isActive = daysSinceLast <= interval.maxDays + grace
        )
    }

    private fun List<ContractTransaction>.hasSimilarAmounts(tolerance: Double): Boolean {
        val amounts = map { it.amount.absoluteValue }.sorted()
        val median = amounts[amounts.size / 2]
        return amounts.first() >= median * (1 - tolerance) && amounts.last() <= median * (1 + tolerance)
    }

    /**
     * Splits into clusters of similar amounts, each sorted by date.
     */
    private fun List<ContractTransaction>.clusterByAmount(tolerance: Double): List<List<ContractTransaction>> {
        val clusters = mutableListOf<MutableList<ContractTransaction>>()
        var clusterStart = 0L
        sortedBy { it.amount.absoluteValue }.forEach {
            val amount = it.amount.absoluteValue
            if (clusters.isEmpty() || amount > clusterStart * (1 + tolerance)) {
                clusters.add(mutableListOf())
                clusterStart = amount
            }
            clusters.last().add(it)
        }
        return clusters.map { cluster -> cluster.sortedWith(compareBy({ it.date }, { it.id })) }
    }

    companion object {
        private const val TEMPLATE_PREFIX = "t"
        private const val PAYEE_PREFIX = "p"

        /** Share of gaps between debits that must match the detected interval */
        const val MIN_REGULAR_SHARE = 0.75

        /** Maximum deviation from the median amount for a whole payee to count as one contract */
        const val GROUP_AMOUNT_TOLERANCE = 0.35

        /** Maximum deviation of amounts within one cluster */
        const val CLUSTER_AMOUNT_TOLERANCE = 0.2

        /** A contract counts as ended, if it has not been debited for its interval plus this grace period */
        const val MIN_GRACE_DAYS = 7
    }
}
