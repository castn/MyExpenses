package org.totschnig.myexpenses.next.contracts

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.max

/**
 * Finds recurring debits (contracts, subscriptions, …) and recurring credits (salary, …)
 * in a list of transactions.
 *
 * 1. Payments are grouped by direction and by the template they were created from, otherwise
 *    by payee. Payments without either are ignored.
 * 2. If the whole group is debited regularly with similar amounts (price changes allowed),
 *    it is one contract. Otherwise the group is split into clusters of similar amounts
 *    (e.g. two different subscriptions from the same payee) and each cluster is checked.
 * 3. A series is regular, if the median of the days between its debits matches a
 *    [ContractInterval] and at least [MIN_REGULAR_SHARE] of all gaps match that interval.
 */
class ContractDetector(private val today: LocalDate = LocalDate.now()) {

    /**
     * Debits and credits are analysed separately, so that e.g. a refund never joins the series
     * of debits of the same payee.
     */
    fun detect(transactions: List<ContractTransaction>): List<Contract> =
        transactions
            .filter { it.amount != 0L }
            .mapNotNull { transaction -> transaction.groupKey()?.let { it to transaction } }
            .groupBy({ it.first }, { it.second })
            .flatMap { (key, group) ->
                val sorted = group.sortedWith(compareBy({ it.date }, { it.id }))
                detectInGroup(key, sorted)
            }
            .sortedByDescending { it.monthlyAmount }

    private data class GroupKey(val direction: ContractDirection, val isTemplate: Boolean, val id: Long) {
        /**
         * Contracts keep the signatures they had before credits were analysed,
         * so that the stored decisions of the user still apply
         */
        override fun toString() = (if (direction == ContractDirection.INCOME) INCOME_PREFIX else "") +
                (if (isTemplate) TEMPLATE_PREFIX else PAYEE_PREFIX) + id
    }

    private fun ContractTransaction.groupKey(): GroupKey? {
        val direction = ContractDirection.of(amount)
        return templateId?.let { GroupKey(direction, true, it) }
            ?: payeeId?.let { GroupKey(direction, false, it) }
    }

    private fun detectInGroup(
        groupKey: GroupKey,
        group: List<ContractTransaction>,
    ): List<Contract> {
        val key = groupKey.toString()
        val isTemplate = groupKey.isTemplate
        // Transactions created from a template belong together, whatever their amount
        val series = if (isTemplate || group.hasSimilarAmounts(GROUP_AMOUNT_TOLERANCE)) {
            detectSeries(group)?.let { listOf(it) }
        } else null
        return (series ?: if (isTemplate) emptyList() else
            group.clusterByAmount(CLUSTER_AMOUNT_TOLERANCE).mapNotNull { detectSeries(it) })
            .withSignatures(key, groupKey.direction)
    }

    private class Series(val interval: ContractInterval, val transactions: List<ContractTransaction>)

    /**
     * @param transactions sorted by date
     */
    private fun detectSeries(transactions: List<ContractTransaction>): Series? {
        if (transactions.size < 2) return null
        val gaps = transactions.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val median = gaps.sorted()[gaps.size / 2]
        val interval = ContractInterval.forDays(median) ?: return null
        if (transactions.size < interval.minOccurrences) return null
        if (gaps.count { it in interval } < gaps.size * MIN_REGULAR_SHARE) return null
        return Series(interval, transactions)
    }

    /**
     * The signature is made of group key and interval. In the rare case that a payee has several
     * contracts with the same interval, they are told apart by the order of their amounts.
     */
    private fun List<Series>.withSignatures(key: String, direction: ContractDirection) =
        groupBy { it.interval }.flatMap { (interval, sameInterval) ->
            sameInterval.sortedBy { series -> series.transactions.minOf { it.amount.absoluteValue } }
                .mapIndexed { index, series ->
                    toContract(
                        signature = "$key|${interval.name}" + if (index > 0) "|$index" else "",
                        series = series,
                        direction = direction
                    )
                }
        }

    private fun toContract(signature: String, series: Series, direction: ContractDirection): Contract {
        val transactions = series.transactions
        val interval = series.interval
        val last = transactions.last()
        val daysSinceLast = ChronoUnit.DAYS.between(last.date, today)
        val grace = max(MIN_GRACE_DAYS, interval.maxDays / 10)
        return Contract(
            signature = signature,
            name = transactions.asReversed().firstNotNullOfOrNull { it.payeeName?.takeIf(String::isNotBlank) }
                ?: last.comment?.takeIf(String::isNotBlank)
                ?: last.categoryPath
                ?: "",
            interval = interval,
            transactions = transactions,
            nextExpectedDate = last.date.plus(interval.step),
            isActive = daysSinceLast <= interval.maxDays + grace,
            direction = direction
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
        private const val INCOME_PREFIX = "in:"

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
