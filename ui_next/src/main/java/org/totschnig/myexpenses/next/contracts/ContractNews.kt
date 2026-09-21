package org.totschnig.myexpenses.next.contracts

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

/**
 * Something that happened with a confirmed contract (or regular income) that the user should know
 * about. News are stored when they happen, so that they stay in the history even when the situation
 * changes afterwards.
 *
 * @param id made of [type], rule and payment, so that the same news is never stored twice
 * @param ruleId the [ContractRule] of the contract
 * @param transactionId the payment that caused the news
 * @param date of that payment
 * @param createdAt when the news was detected
 * @param contractName name of the contract when the news was detected, for the history
 * @param amount of the payment, absolute, in minor units of the home currency
 * @param previousAmount for [Type.PRICE_CHANGE]: the amount before
 * @param cancelledOn for [Type.PAYMENT_AFTER_CANCELLATION]: when the contract had been cancelled
 */
data class ContractNews(
    val id: String,
    val type: Type,
    val ruleId: String,
    val transactionId: Long,
    val date: LocalDate,
    val createdAt: LocalDate,
    val contractName: String,
    val isIncome: Boolean,
    val amount: Long,
    val previousAmount: Long? = null,
    val cancelledOn: LocalDate? = null,
    val isRead: Boolean = false,
) {
    enum class Type {
        /** A cancelled contract was paid again, which lifted the cancellation */
        PAYMENT_AFTER_CANCELLATION,

        /** The amount changed after it had been stable */
        PRICE_CHANGE
    }

    /** Key of the contract, see [ContractRule.key] */
    val contractKey: String get() = "${ContractRule.KEY_PREFIX}$ruleId"

    companion object {
        /** A change of the amount below this share is no price change, e.g. rounding of exchange rates */
        const val PRICE_CHANGE_TOLERANCE = 0.01

        /** Only a payment this recent reports a price change, not the history found on the first analysis */
        const val PRICE_CHANGE_MAX_AGE_DAYS = 40L

        /** How long news are kept in the history */
        const val KEEP_MONTHS = 24L

        /** At most this many news are kept */
        const val MAX_COUNT = 200
    }
}

/**
 * @param news found in the analysis, including those stored already
 * @param reactivated rules of cancelled contracts that were paid again
 */
class NewsDetection(val news: List<ContractNews>, val reactivated: Set<String>)

/**
 * Finds news in the confirmed contracts of [analysis]. Payments dated in the future, e.g. booked
 * in advance by a plan, have not happened yet and are left out.
 */
fun ContractAnalysis.detectNews(today: LocalDate): NewsDetection {
    val news = mutableListOf<ContractNews>()
    val reactivated = mutableSetOf<String>()
    contracts.filter { it.isConfirmed }.forEach { contract ->
        val rule = contract.rule ?: return@forEach
        val payments = contract.transactions.filter { it.id != SNAPSHOT_TRANSACTION_ID && !it.date.isAfter(today) }
        fun newsOf(type: ContractNews.Type, payment: ContractTransaction, previousAmount: Long? = null) = ContractNews(
            id = "${type.name}:${rule.id}:${payment.id}",
            type = type,
            ruleId = rule.id,
            transactionId = payment.id,
            date = payment.date,
            createdAt = today,
            contractName = contract.displayName,
            isIncome = contract.isIncome,
            amount = payment.amount.absoluteValue,
            previousAmount = previousAmount,
            cancelledOn = rule.cancelledOn
        )

        val cancelledOn = rule.cancelledOn
        if (cancelledOn != null) {
            payments.lastOrNull { it.date.isAfter(cancelledOn) }?.let {
                news += newsOf(ContractNews.Type.PAYMENT_AFTER_CANCELLATION, it)
                reactivated += rule.id
            }
        }

        if (payments.size >= 3) {
            val (beforePrevious, previous, last) = payments.takeLast(3).map { it.amount.absoluteValue }
            fun differ(a: Long, b: Long) = (a - b).absoluteValue > b * ContractNews.PRICE_CHANGE_TOLERANCE
            val lastPayment = payments.last()
            if (!differ(previous, beforePrevious) && differ(last, previous) &&
                ChronoUnit.DAYS.between(lastPayment.date, today) <= ContractNews.PRICE_CHANGE_MAX_AGE_DAYS
            ) {
                news += newsOf(ContractNews.Type.PRICE_CHANGE, lastPayment, previousAmount = previous)
            }
        }
    }
    return NewsDetection(news, reactivated)
}

/**
 * Adds the news not stored yet, drops those older than [ContractNews.KEEP_MONTHS] and keeps at
 * most [ContractNews.MAX_COUNT], newest first
 */
fun List<ContractNews>.adding(found: List<ContractNews>, today: LocalDate): List<ContractNews> {
    val known = mapTo(HashSet()) { it.id }
    val limit = today.minusMonths(ContractNews.KEEP_MONTHS)
    return (this + found.filter { it.id !in known })
        .filter { !it.createdAt.isBefore(limit) }
        .sortedWith(compareByDescending<ContractNews> { it.createdAt }.thenByDescending { it.date })
        .take(ContractNews.MAX_COUNT)
}

/**
 * Lifts the cancellation of the rules of contracts that were paid again
 */
fun List<ContractRule>.reactivating(ruleIds: Set<String>): List<ContractRule> =
    if (ruleIds.isEmpty()) this
    else map { if (it.id in ruleIds) it.copy(cancelledOn = null, snapshot = null) else it }
