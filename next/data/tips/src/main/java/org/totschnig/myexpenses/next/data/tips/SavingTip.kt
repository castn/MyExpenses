package org.totschnig.myexpenses.next.data.tips

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.data.contracts.BuiltInArea
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.budgets.BudgetFacts
import org.totschnig.myexpenses.next.data.contracts.ContractFacts
import org.totschnig.myexpenses.next.data.contracts.ContractNews
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.data.contracts.SNAPSHOT_TRANSACTION_ID
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.data.tags.TagFacts
import org.totschnig.myexpenses.next.core.common.strings
import java.time.LocalDate
import kotlin.math.absoluteValue

/**
 * A hint where the user could spend less. Tips only state what was booked, they do not estimate
 * how much could be saved.
 */
sealed interface SavingTip {
    /** Stays the same while the situation changes, e.g. one id for all price increases */
    val id: String

    /** What the tip is about per year, in minor units of the home currency. Tips are sorted by it. */
    val yearlyAmount: Long

    /**
     * What the tip is based on. A tip the user dismissed comes back when a fact is added,
     * e.g. another price increase, see [TipDecisions].
     */
    val facts: Set<String>

    /**
     * Transactions a tip can show, by a key unique among all tips, e.g. the debits of a kind of bank fees
     */
    val transactionGroups: Map<String, List<ContractTransaction>> get() = emptyMap()

    /**
     * Contracts that have become more expensive within the last [PriceIncreases.WINDOW_MONTHS] months
     *
     * @param increases highest first
     */
    data class PriceIncreases(val increases: List<PriceIncrease>) : SavingTip {
        override val id: String get() = ID
        override val yearlyAmount: Long get() = increases.sumOf { it.yearlyAmount }
        val monthlyAmount: Long get() = yearlyAmount / 12
        override val facts: Set<String> get() = increases.mapTo(HashSet()) { "${it.contract.signature}:${it.sinceTransactionId}" }

        companion object {
            const val ID = "PRICE_INCREASES"
            const val WINDOW_MONTHS = 12L
        }
    }

    /**
     * Fees the bank charged within the last [WINDOW_MONTHS] months, by kind. Every further fee is
     * no news, only a new kind or a kind that became much more expensive, see [level].
     *
     * @param groups in the order of [BankFeeKind]
     */
    data class BankFees(val groups: List<BankFeeGroup>) : SavingTip {
        override val id: String get() = ID
        override val yearlyAmount: Long get() = groups.sumOf { it.amount }
        override val facts: Set<String> get() = groups.mapTo(HashSet()) { "${it.kind.name}:${level(it.amount)}" }
        override val transactionGroups: Map<String, List<ContractTransaction>>
            get() = groups.associate { keyOf(it.kind) to it.transactions }

        companion object {
            const val ID = "BANK_FEES"
            const val WINDOW_MONTHS = 12L

            /** Less is not worth a tip, in minor units */
            const val MIN_AMOUNT = 1000L

            fun keyOf(kind: BankFeeKind) = "$ID:${kind.name}"

        }
    }

    /**
     * Several contracts of the same [kind], e.g. two streaming services. One tip per kind, so that
     * the user can keep two streaming services, but still hears about two liability insurances.
     *
     * @param contracts highest yearly amount first
     */
    data class DuplicateContracts(val kind: ContractKind, val contracts: List<Contract>) : SavingTip {
        override val id: String get() = "$ID_PREFIX${kind.name}"
        override val yearlyAmount: Long get() = contracts.sumOf { it.yearlyAmount }
        val monthlyAmount: Long get() = yearlyAmount / 12

        /** Comes back when another contract of the kind is added */
        override val facts: Set<String> get() = contracts.mapTo(HashSet()) { it.signature }

        companion object {
            const val ID_PREFIX = "DUPLICATES:"
        }
    }

    /**
     * Contracts whose providers compete on price, e.g. electricity, paid to the same provider for
     * at least [MIN_YEARS] years
     *
     * @param contracts highest yearly amount first
     */
    data class LongRunningContracts(val contracts: List<LongRunningContract>) : SavingTip {
        override val id: String get() = ID
        override val yearlyAmount: Long get() = contracts.sumOf { it.contract.yearlyAmount }
        override val facts: Set<String> get() = contracts.mapTo(HashSet()) { it.contract.signature }

        companion object {
            const val ID = "LONG_RUNNING"
            const val MIN_YEARS = 2L
        }
    }

    /**
     * Insurances and subscriptions paid more often than once a year, which often costs extra
     *
     * @param contracts highest yearly amount first
     */
    data class FrequentPayments(val contracts: List<Contract>) : SavingTip {
        override val id: String get() = ID
        override val yearlyAmount: Long get() = contracts.sumOf { it.yearlyAmount }
        val monthlyAmount: Long get() = yearlyAmount / 12
        override val facts: Set<String> get() = contracts.mapTo(HashSet()) { it.signature }

        companion object {
            const val ID = "FREQUENT_PAYMENTS"
        }
    }
}

/** Goes up by one each time the amount doubles, so that a much higher amount is a new fact */
internal fun level(amount: Long) = 63 - java.lang.Long.numberOfLeadingZeros(maxOf(amount / 100, 1))

/**
 * @param since the first payment known, the contract may be older: only the last
 * [org.totschnig.myexpenses.next.ledger.Ledger.HISTORY_MONTHS] months are analysed
 */
data class LongRunningContract(val contract: Contract, val since: LocalDate)

/**
 * @param before amount of the last payment before the increase, or the first payment of a younger contract
 * @param after amount of the last payment
 * @param since date of the first payment with the higher amount
 */
data class PriceIncrease(
    val contract: Contract,
    val before: Long,
    val after: Long,
    val since: LocalDate,
    val sinceTransactionId: Long,
) {
    val yearlyAmount: Long get() = (after - before) * contract.interval.perYear
}

/**
 * All tips from what the features know, highest amount first
 */
fun savingTips(contracts: ContractFacts, tags: TagFacts, receipts: ReceiptFacts, today: LocalDate, budget: BudgetFacts? = null) = savingTips(
    contracts.active, contracts.transactions, today, contracts.paymentIds, tags.excludedFromSpending, receipts.items, budget
)

/**
 * All tips, highest amount first
 *
 * @param contracts active, confirmed contracts that are not cancelled
 * @param transactions all transactions of the analysis
 * @param contractPayments ids of the payments of all contracts, suggestions included, see [spending]
 * @param receipts items of scanned receipts by transaction
 * @param budget the pots of the budget, null without
 */
fun savingTips(
    contracts: List<Contract>,
    transactions: List<ContractTransaction>,
    today: LocalDate,
    contractPayments: Set<Long> = emptySet(),
    excludedFromSpending: Set<Long> = emptySet(),
    receipts: Map<Long, List<ScannedItem>> = emptyMap(),
    budget: BudgetFacts? = null,
): List<SavingTip> {
    val spending = spending(transactions, contractPayments, today, excludedFromSpending)
    val scanned = scannedPurchases(spending, receipts)
    return (listOfNotNull(
        priceIncreases(contracts, today),
        bankFees(transactions, today),
        longRunningContracts(contracts, today),
        frequentPayments(contracts),
        smallPurchases(spending, today),
        eatingOut(spending, today),
        supermarkets(spending, today),
        outliers(spending, today),
        depositNotReturned(scanned, today),
        drugstoreInSupermarkets(scanned, today),
        potsOverspent(budget)
    ) + duplicateContracts(contracts) + itemGroupsRising(scanned, today))
        .sortedByDescending { it.yearlyAmount }
}

/** Contracts that cost money: no reserves, not cancelled, still paid */
private fun List<Contract>.costs() = filter { !it.isIncome && !it.isReserve && !it.isCancelled && it.isActive }

/**
 * Contracts that cost money (no reserves, not cancelled), grouped by [ContractKind]
 */
internal fun duplicateContracts(contracts: List<Contract>): List<SavingTip.DuplicateContracts> =
    contracts.costs()
        .groupBy { ContractKind.of(it) }
        .mapNotNull { (kind, group) ->
            if (kind != null && kind.isUsuallyOne && group.size >= 2)
                SavingTip.DuplicateContracts(kind, group.sortedByDescending { it.yearlyAmount })
            else null
        }

/**
 * Compares the last payment of each contract with the last one before the window. Money put aside
 * is left out, paying more into a savings plan is no cost. A contract whose last two payments differ
 * has no stable new price, e.g. a bill depending on consumption.
 */
internal fun priceIncreases(contracts: List<Contract>, today: LocalDate): SavingTip.PriceIncreases? {
    val start = today.minusMonths(SavingTip.PriceIncreases.WINDOW_MONTHS)
    fun differ(a: Long, b: Long) = (a - b).absoluteValue > b * ContractNews.PRICE_CHANGE_TOLERANCE
    val increases = contracts.filter { !it.isIncome && !it.isReserve && !it.isCancelled }.mapNotNull { contract ->
        val payments = contract.transactions.filter { it.id != SNAPSHOT_TRANSACTION_ID && !it.date.isAfter(today) }
        val reference = payments.lastOrNull { !it.date.isAfter(start) } ?: payments.firstOrNull() ?: return@mapNotNull null
        val recent = payments.filter { it.date.isAfter(reference.date) }
        val before = reference.amount.absoluteValue
        val after = recent.lastOrNull()?.amount?.absoluteValue ?: return@mapNotNull null
        if (recent.size >= 2 && differ(recent[recent.size - 2].amount.absoluteValue, after)) return@mapNotNull null
        if (after <= before || !differ(after, before)) return@mapNotNull null
        val since = recent.first { differ(it.amount.absoluteValue, before) }
        PriceIncrease(contract, before, after, since.date, since.id)
    }
    return increases.takeIf { it.isNotEmpty() }
        ?.let { SavingTip.PriceIncreases(it.sortedByDescending(PriceIncrease::yearlyAmount)) }
}

/**
 * What the user did with tips: the [SavingTip.facts] known at that time by [SavingTip.id]. Facts are
 * added up, so that a fact that goes and comes back, e.g. an amount near a [level]
 * boundary, is not new again.
 *
 * @param dismissed tips the user does not care about, until new facts come up
 * @param seen tips the user has seen in the list, the others are new
 */
data class TipDecisions(
    val dismissed: Map<String, Set<String>> = emptyMap(),
    val seen: Map<String, Set<String>> = emptyMap(),
) {
    fun isDismissed(tip: SavingTip) = dismissed.covers(tip)

    fun isNew(tip: SavingTip) = !seen.covers(tip)

    private fun Map<String, Set<String>>.covers(tip: SavingTip) = this[tip.id]?.containsAll(tip.facts) == true

    companion object {
        /** Adds the facts of [tips] to those known */
        fun Map<String, Set<String>>.adding(tips: List<SavingTip>) =
            this + tips.associate { it.id to (this[it.id].orEmpty() + it.facts) }

        /** Maps that cannot be read are taken as empty */
        fun parse(json: String?): Map<String, Set<String>> = try {
            json?.let(::JSONObject)?.let { obj ->
                obj.keys().asSequence().associateWith { key -> obj.getJSONArray(key).strings() }
            } ?: emptyMap()
        } catch (_: JSONException) {
            emptyMap()
        }

        fun serialize(map: Map<String, Set<String>>) =
            JSONObject().apply { map.forEach { (id, facts) -> put(id, JSONArray(facts.toList())) } }.toString()
    }
}

internal fun longRunningContracts(contracts: List<Contract>, today: LocalDate): SavingTip.LongRunningContracts? {
    val limit = today.minusYears(SavingTip.LongRunningContracts.MIN_YEARS)
    return contracts.costs()
        .filter { ContractKind.of(it)?.isSwitchable == true }
        .mapNotNull { contract ->
            contract.transactions.firstOrNull { it.id != SNAPSHOT_TRANSACTION_ID }?.date
                ?.takeIf { !it.isAfter(limit) }
                ?.let { LongRunningContract(contract, it) }
        }
        .sortedByDescending { it.contract.yearlyAmount }
        .takeIf { it.isNotEmpty() }
        ?.let { SavingTip.LongRunningContracts(it) }
}

/** Paid by the month by custom or by law, a yearly payment is no option */
private val MONTHLY_BY_NATURE = listOf("kranken", "pflege", "health", "renten", "pension")

private val INSURANCE_WORDS = listOf("versicherung", "insurance")

internal fun frequentPayments(contracts: List<Contract>): SavingTip.FrequentPayments? =
    contracts.costs()
        .filter { contract ->
            val text = ContractKind.textOf(contract)
            val kind = ContractKind.of(contract)
            val isInsurance = contract.area == BuiltInArea.INSURANCE || INSURANCE_WORDS.any { it in text } ||
                    kind in INSURANCE_KINDS
            val isSubscription = kind?.isSubscription == true || contract.area == BuiltInArea.SOFTWARE
            contract.interval.perYear > 1 && (isInsurance || isSubscription) && MONTHLY_BY_NATURE.none { it in text }
        }
        .sortedByDescending { it.yearlyAmount }
        .takeIf { it.isNotEmpty() }
        ?.let { SavingTip.FrequentPayments(it) }

private val INSURANCE_KINDS = setOf(
    ContractKind.CAR_INSURANCE, ContractKind.LIABILITY_INSURANCE, ContractKind.CONTENTS_INSURANCE, ContractKind.LEGAL_INSURANCE
)
