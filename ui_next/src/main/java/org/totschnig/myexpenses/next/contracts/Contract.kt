package org.totschnig.myexpenses.next.contracts

import java.time.LocalDate
import java.time.Period

/**
 * How often a contract is debited.
 *
 * @param minDays / [maxDays] range of days between two debits that still counts as this interval.
 * Generous enough to cover debits moved by weekends and bank holidays.
 * @param minOccurrences how many debits are needed at least before a series is accepted as contract
 * @param perYear number of debits per year, used to compute monthly and yearly totals
 * @param step period added to the last debit to get the next expected one
 */
enum class ContractInterval(
    val minDays: Int,
    val maxDays: Int,
    val minOccurrences: Int,
    val perYear: Int,
    val step: Period,
) {
    WEEKLY(6, 8, 4, 52, Period.ofWeeks(1)),
    BIWEEKLY(13, 16, 3, 26, Period.ofWeeks(2)),
    MONTHLY(26, 35, 3, 12, Period.ofMonths(1)),
    BIMONTHLY(55, 67, 3, 6, Period.ofMonths(2)),
    QUARTERLY(82, 100, 3, 4, Period.ofMonths(3)),
    HALF_YEARLY(170, 195, 2, 2, Period.ofMonths(6)),
    YEARLY(345, 385, 2, 1, Period.ofYears(1));

    operator fun contains(days: Long) = days in minDays..maxDays

    companion object {
        fun forDays(days: Long) = entries.firstOrNull { days in it }
    }
}

/**
 * Debit as input for [ContractDetector]. Amounts are in minor units of the home currency;
 * debits are negative.
 */
data class ContractTransaction(
    val id: Long,
    val date: LocalDate,
    val amount: Long,
    val accountId: Long,
    val accountLabel: String? = null,
    val payeeId: Long? = null,
    val payeeName: String? = null,
    val comment: String? = null,
    val categoryPath: String? = null,
    val categoryIcon: String? = null,
    val templateId: Long? = null,
)

/**
 * A detected recurring payment.
 *
 * All amounts are positive and in minor units of the home currency.
 */
data class Contract(
    /**
     * Identifies the contract across detections, also when further debits are added or the price changes.
     * Built from the payee (or template) and the interval. Used to store decisions of the user.
     */
    val signature: String,
    /** Name derived from the debits */
    val name: String,
    /** Name given by the user, overrides [name] */
    val customName: String? = null,
    /** How the user wants the [area] to be determined */
    val areaChoice: AreaChoice = AreaChoice.Automatic,
    val interval: ContractInterval,
    /** Debits that make up this contract, oldest first */
    val transactions: List<ContractTransaction>,
    val nextExpectedDate: LocalDate,
    /** False, if the contract has not been debited for longer than its interval */
    val isActive: Boolean,
) {
    val displayName: String get() = customName ?: name

    /** Area suggested by the category of the last debit */
    val suggestedArea: ContractArea? get() = BuiltInArea.suggest(categoryPath)

    val area: ContractArea?
        get() = when (val choice = areaChoice) {
            AreaChoice.Automatic -> suggestedArea
            is AreaChoice.Fixed -> choice.area
        }

    val lastTransaction: ContractTransaction get() = transactions.last()
    val lastDate: LocalDate get() = lastTransaction.date
    val lastAmount: Long get() = -lastTransaction.amount

    /** Amount of the debit before the last one, null if there is none */
    val previousAmount: Long? get() = transactions.getOrNull(transactions.size - 2)?.amount?.let { -it }

    val categoryPath: String? get() = lastTransaction.categoryPath
    val categoryIcon: String? get() = lastTransaction.categoryIcon
    val accountLabel: String? get() = lastTransaction.accountLabel

    val yearlyAmount: Long get() = lastAmount * interval.perYear
    val monthlyAmount: Long get() = yearlyAmount / 12
}
