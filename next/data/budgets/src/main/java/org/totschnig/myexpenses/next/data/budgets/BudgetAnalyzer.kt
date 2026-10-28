package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem
import org.totschnig.myexpenses.next.data.balance.BalancePeriod
import org.totschnig.myexpenses.next.data.balance.MonthlyBalance
import org.totschnig.myexpenses.next.data.balance.balanceTransactions
import org.totschnig.myexpenses.next.data.balance.upcomingPayments
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.data.tags.TagMeaning

/**
 * Where a pot stands in a period. Amounts are positive and in minor units of the home currency.
 *
 * @param planned what the pot holds: for [PotKind.RUNNING] the amount of the period, for
 * [PotKind.YEARLY] the shares added since its start, for [PotKind.GOAL] the target, for
 * [PotKind.PROJECT] the total, for [PotKind.ITEM_GROUP] the largest share allowed of the scanned
 * purchases
 * @param carried for [PotKind.RUNNING] with rollover: left from the periods before, negative if
 * they were overspent
 * @param spent for [PotKind.RUNNING] in the period without one-off purchases, for the others since
 * the start of the pot until the end of the period; returns are taken off
 * @param spentInPeriod spent in the period, for all kinds
 * @param fixedSpent the part of [spent] paid for contracts of the pot
 * @param upcoming payments of contracts of the pot still expected in the period
 * @param oneOff for [PotKind.RUNNING]: purchases marked as one-off in the period, which do not
 * count, see [oneOffIds]
 * @param saved for [PotKind.GOAL]: what was put aside for it since its start
 * @param projected for [PotKind.RUNNING] in the current period: what will have been spent at its
 * end at the pace so far, null if it is too early to tell
 * @param history for [PotKind.RUNNING]: spent in the whole periods before, oldest first, since the
 * start of the pot, at most [BudgetAnalyzer.HISTORY_PERIODS]
 * @param transactionIds of the transactions counted, newest first
 * @param share for [PotKind.ITEM_GROUP]: what its item groups took of the scanned purchases, from 0 to 1
 * @param scannedPurchases for [PotKind.ITEM_GROUP]: number of purchases with a receipt [share] is based on
 */
data class PotStatus(
    val pot: Pot,
    val planned: Long,
    val carried: Long = 0,
    val spent: Long = 0,
    val spentInPeriod: Long = 0,
    val fixedSpent: Long = 0,
    val upcoming: Long = 0,
    val oneOff: Long = 0,
    val saved: Long = 0,
    val projected: Long? = null,
    val history: List<Long> = emptyList(),
    val transactionIds: List<Long> = emptyList(),
    val oneOffIds: List<Long> = emptyList(),
    val share: Float? = null,
    val scannedPurchases: Int = 0,
) {
    /**
     * What can still be spent: for a goal what is still to be saved. Negative if the pot is
     * overspent. Contract payments still expected are taken off already.
     */
    val left: Long
        get() = if (pot.kind == PotKind.GOAL) pot.amount - saved else planned + carried - spent - upcoming

    val isOverspent: Boolean get() = pot.kind != PotKind.GOAL && left < 0

    /** Share of [planned] spent or, for a goal, saved, 1f = all of it */
    val progress: Float
        get() {
            val total = if (pot.kind == PotKind.GOAL) pot.amount else planned + carried
            val used = if (pot.kind == PotKind.GOAL) saved else spent + upcoming
            return when {
                total > 0 -> used.toFloat() / total
                used > 0 -> 1f
                else -> 0f
            }
        }
}

/**
 * The frame of the budget in a period, from the monthly balance: what comes in, what is fixed, what
 * is planned in pots and what is left. Amounts are positive and in minor units of the home currency.
 *
 * @param income booked and expected salaries and other income
 * @param fixed contracts and reserves, booked and expected
 * @param planned put into pots for the period, without their contracts, which are in [fixed]
 * already: running pots, a twelfth of yearly pots and the monthly rate of goals no reserve is
 * linked to
 * @param spentOutsidePots spent in the period on what no pot holds
 * @param free what can still be spent in the period without touching what pots hold, negative if
 * there is less than planned
 */
data class BudgetFrame(
    val income: Long,
    val fixed: Long,
    val planned: Long,
    val spentOutsidePots: Long,
    val free: Long,
) {
    /** What is not planned in pots, negative if more is planned than there is */
    val unplanned: Long get() = income - fixed - planned
}

/**
 * The budget in a period
 *
 * @param offset of [period]: 0 for the current one, 1 for the one before and so on
 * @param hasEarlier whether there is a period before [period] in the ledger
 * @param pots in the order of the user
 */
data class BudgetOverview(
    val period: BalancePeriod,
    val offset: Int,
    val hasEarlier: Boolean,
    val frame: BudgetFrame,
    val pots: List<PotStatus>,
) {
    val isCurrent: Boolean get() = offset == 0
}

/**
 * What the budget is computed from
 *
 * @param contracts confirmed contracts, see [org.totschnig.myexpenses.next.contracts.ContractFacts.confirmed]
 * @param salaries see [org.totschnig.myexpenses.next.contracts.ContractFacts.salaries]
 * @param meanings of the tags, see [org.totschnig.myexpenses.next.tags.TagFacts.meanings]
 * @param receipts items of scanned receipts by transaction, see [org.totschnig.myexpenses.next.receipts.ReceiptFacts]
 */
data class BudgetInput(
    val ledger: Ledger,
    val pots: List<Pot>,
    val categories: Map<Long, CategoryInfo>,
    val contracts: List<Contract>,
    val salaries: List<Contract>,
    val meanings: Map<Long, TagMeaning>,
    val today: LocalDate,
    val offset: Int = 0,
    val receipts: Map<Long, List<ScannedItem>> = emptyMap(),
)

object BudgetAnalyzer {

    /** How many periods before the shown one are kept in [PotStatus.history] */
    const val HISTORY_PERIODS = 12

    /** Scanned purchases a pot of item groups needs in a period, else earlier periods are added */
    const val MIN_SCANNED = 3

    /** Before so many days of a period have passed, no projection is made */
    private const val MIN_DAYS_FOR_PROJECTION = 5L

    /**
     * Reimbursed transactions count nowhere. Purchases marked as one-off do not count in running
     * pots, which are for everyday spending, but in all other pots.
     */
    fun analyse(input: BudgetInput): BudgetOverview {
        val (ledger, pots, categories, contracts, salaries, meanings, today, offset, receipts) = input
        val periods = budgetPeriods(salaries.firstOrNull(), today, today.minusMonths(Ledger.HISTORY_MONTHS))
        val index = (periods.size - 1 - offset).coerceIn(0, periods.size - 1)
        val period = periods[index]
        val isCurrent = index == periods.size - 1

        val contractRules = contracts.mapNotNull { contract -> contract.rule?.id?.let { contract to it } }
        val paymentRules = contractRules.flatMap { (contract, ruleId) -> contract.transactions.map { it.id to ruleId } }.toMap()
        val matcher = PotMatcher(pots, categories, paymentRules)

        fun hasMeaning(transaction: LedgerTransaction, meaning: TagMeaning) =
            transaction.tagIds.any { meanings[it] == meaning }

        // Each counted part with its pot, newest transactions first
        val counted = ledger.transactions
            .filter { !hasMeaning(it, TagMeaning.REIMBURSED) }
            .sortedByDescending { it.date }
            .flatMap { transaction ->
                matcher.potsOf(transaction).map { (part, pot) ->
                    CountedPart(transaction, -part.homeAmount, pot, hasMeaning(transaction, TagMeaning.ONE_OFF), transaction.id in paymentRules)
                }
            }
        val byPot = counted.filter { it.pot != null }.groupBy { it.pot!!.id }

        val statuses = pots.map { pot ->
            val parts = byPot[pot.id].orEmpty()
            val potContracts = contractRules.filter { (_, ruleId) -> ruleId in pot.contractRuleIds }.map { it.first }
            val upcoming = if (isCurrent) potContracts
                .filter { it.isActive && !it.isCancelled }
                .sumOf { it.lastAmount * it.upcomingPayments(period) } else 0
            when (pot.kind) {
                PotKind.RUNNING -> running(pot, parts, period, periods.subList(0, index), upcoming, today.takeIf { isCurrent })
                PotKind.YEARLY -> yearly(pot, parts, period, upcoming)
                PotKind.GOAL -> goal(pot, parts, period, ledger, contractRules.filter { (_, ruleId) -> ruleId in pot.reserveRuleIds }.map { it.first })
                PotKind.PROJECT -> project(pot, parts, period, upcoming)
                PotKind.ITEM_GROUP -> itemGroup(pot, ledger, receipts, periods, index) { hasMeaning(it, TagMeaning.REIMBURSED) }
            }
        }

        val balance = MonthlyBalance.compute(
            period, balanceTransactions(ledger, period), ledger.ownAccounts.dailyIds, contracts, salaries
        )
        val inPeriod = counted.filter { it.transaction.date in period }
        val otherIds = balance.otherTransactionIds.toSet()
        val planned = statuses.map { plannedPerPeriod(it, period, potContractsMonthly(it.pot, contractRules)) }
        val frame = BudgetFrame(
            income = balance.income,
            fixed = -(balance.contracts + balance.reserves),
            planned = planned.sum(),
            spentOutsidePots = inPeriod.filter { it.pot == null && it.transaction.id in otherIds }.sumOf { it.amount },
            free = balance.available - statuses.zip(planned).sumOf { (status, plannedInPeriod) -> reserved(status, plannedInPeriod) }
        )
        return BudgetOverview(period, periods.size - 1 - index, index > 0, frame, statuses)
    }

    /** A part of a transaction, [amount] positive for spending */
    private class CountedPart(
        val transaction: LedgerTransaction,
        val amount: Long,
        val pot: Pot?,
        val isOneOff: Boolean,
        val isContractPayment: Boolean,
    )

    private fun List<CountedPart>.ids() = map { it.transaction.id }.distinct()

    private fun List<CountedPart>.within(start: LocalDate?, end: LocalDate) =
        filter { (start == null || !it.transaction.date.isBefore(start)) && it.transaction.date.isBefore(end) }

    private fun running(
        pot: Pot,
        parts: List<CountedPart>,
        period: BalancePeriod,
        before: List<BalancePeriod>,
        upcoming: Long,
        today: LocalDate?,
    ): PotStatus {
        val (oneOffs, everyday) = parts.filter { it.transaction.date in period }.partition { it.isOneOff }
        val spent = everyday.sumOf { it.amount }
        val fixedSpent = everyday.filter { it.isContractPayment }.sumOf { it.amount }
        val history = before
            .filter { pot.start == null || it.end.isAfter(pot.start) }
            .takeLast(HISTORY_PERIODS)
            .map { earlier -> parts.filter { !it.isOneOff && it.transaction.date in earlier }.sumOf { it.amount } }
        val carried = if (pot.rollover && pot.start != null) history.sumOf { pot.amount - it } else 0
        val projected = today?.let {
            val days = ChronoUnit.DAYS.between(period.start, period.end)
            val passed = ChronoUnit.DAYS.between(period.start, today) + 1
            if (passed < MIN_DAYS_FOR_PROJECTION) null
            else (spent - fixedSpent) * days / passed + fixedSpent + upcoming
        }
        return PotStatus(
            pot = pot,
            planned = pot.amount,
            carried = carried,
            spent = spent,
            spentInPeriod = spent,
            fixedSpent = fixedSpent,
            upcoming = upcoming,
            oneOff = oneOffs.sumOf { it.amount },
            projected = projected,
            history = history,
            transactionIds = everyday.ids(),
            oneOffIds = oneOffs.ids()
        )
    }

    private fun yearly(pot: Pot, parts: List<CountedPart>, period: BalancePeriod, upcoming: Long): PotStatus {
        val counted = parts.within(pot.start, period.end)
        val start = pot.start ?: period.start.withDayOfYear(1)
        // A share is added at the start of each month, the first one in the month the pot starts
        val months = max(0L, ChronoUnit.MONTHS.between(start.withDayOfMonth(1), period.end.minusDays(1).withDayOfMonth(1)) + 1)
        return PotStatus(
            pot = pot,
            planned = pot.amount * months / 12,
            spent = counted.sumOf { it.amount },
            spentInPeriod = counted.filter { it.transaction.date in period }.sumOf { it.amount },
            fixedSpent = counted.filter { it.isContractPayment }.sumOf { it.amount },
            upcoming = upcoming,
            transactionIds = counted.ids()
        )
    }

    private fun project(pot: Pot, parts: List<CountedPart>, period: BalancePeriod, upcoming: Long): PotStatus {
        val counted = parts.within(pot.start, period.end)
        return PotStatus(
            pot = pot,
            planned = pot.amount,
            spent = counted.sumOf { it.amount },
            spentInPeriod = counted.filter { it.transaction.date in period }.sumOf { it.amount },
            fixedSpent = counted.filter { it.isContractPayment }.sumOf { it.amount },
            upcoming = upcoming,
            transactionIds = counted.ids()
        )
    }

    /**
     * Saved are the payments of the linked reserves and money moved to the saving accounts, each
     * transaction once, less money moved back from them
     */
    private fun goal(
        pot: Pot,
        parts: List<CountedPart>,
        period: BalancePeriod,
        ledger: Ledger,
        reserves: List<Contract>,
    ): PotStatus {
        val counted = parts.within(pot.start, period.end)
        fun inRange(date: LocalDate) = (pot.start == null || !date.isBefore(pot.start)) && date.isBefore(period.end)
        val reservePayments = reserves.flatMap { it.transactions }.filter { inRange(it.date) }.associate { it.id to it.amount }
        val movements = ledger.transactions.filter {
            inRange(it.date) && it.id !in reservePayments &&
                    it.accountId !in pot.savingAccountIds && it.targetAccountId in pot.savingAccountIds
        }
        return PotStatus(
            pot = pot,
            planned = pot.amount,
            spent = counted.sumOf { it.amount },
            spentInPeriod = counted.filter { it.transaction.date in period }.sumOf { it.amount },
            saved = -(reservePayments.values.sum() + movements.sumOf { it.homeAmount }),
            transactionIds = counted.ids()
        )
    }

    /**
     * The share of the item groups of [pot] in the purchases with a receipt in the period of
     * [index]. With fewer than [MIN_SCANNED] of them, the two periods before are added, so that a
     * single receipt does not decide. Amounts are taken in the home currency, by the share of the
     * items in their receipt.
     */
    private fun itemGroup(
        pot: Pot,
        ledger: Ledger,
        receipts: Map<Long, List<ScannedItem>>,
        periods: List<BalancePeriod>,
        index: Int,
        isReimbursed: (LedgerTransaction) -> Boolean,
    ): PotStatus {
        fun purchasesFrom(start: LocalDate) = ledger.transactions.filter {
            it.amount < 0 && it.targetAccountId == null && !isReimbursed(it) && it.id in receipts &&
                    !it.date.isBefore(start) && it.date.isBefore(periods[index].end)
        }
        val purchases = purchasesFrom(periods[index].start).takeIf { it.size >= MIN_SCANNED }
            ?: purchasesFrom(periods[(index - 2).coerceAtLeast(0)].start)
        var basis = 0L
        var spent = 0L
        val ids = mutableListOf<Long>()
        purchases.sortedByDescending { it.date }.forEach { purchase ->
            val items = receipts.getValue(purchase.id)
            val total = items.sumOf { it.amount }
            if (total <= 0) return@forEach
            val inGroups = items.filter { it.group in pot.itemGroups }.sumOf { it.amount }
            basis += -purchase.homeAmount
            if (inGroups > 0) {
                spent += -purchase.homeAmount * inGroups / total
                ids += purchase.id
            }
        }
        return PotStatus(
            pot = pot,
            planned = basis * (pot.maxSharePercent ?: 0) / 100,
            spent = spent,
            spentInPeriod = spent,
            share = if (basis > 0) spent.toFloat() / basis else null,
            scannedPurchases = purchases.size,
            transactionIds = ids
        )
    }

    /** Monthly amount of the contracts of [pot], which are part of the fixed costs */
    private fun potContractsMonthly(pot: Pot, contractRules: List<Pair<Contract, String>>) =
        contractRules.filter { (contract, ruleId) -> ruleId in pot.contractRuleIds && contract.isActive && !contract.isCancelled }
            .sumOf { it.first.monthlyAmount }

    /** What [status] takes from the income of [period], without its contracts */
    private fun plannedPerPeriod(status: PotStatus, period: BalancePeriod, contractsMonthly: Long): Long {
        val pot = status.pot
        val gross = when (pot.kind) {
            PotKind.RUNNING -> pot.amount
            PotKind.YEARLY -> pot.amount / 12
            PotKind.GOAL -> if (pot.reserveRuleIds.isNotEmpty() || pot.savingAccountIds.isNotEmpty() || pot.end == null) 0
            else {
                // Months left including the current one
                val months = max(1L, ChronoUnit.MONTHS.between(period.start.withDayOfMonth(1), pot.end.withDayOfMonth(1)) + 1)
                max(0L, pot.amount - status.saved) / months
            }
            PotKind.PROJECT, PotKind.ITEM_GROUP -> 0
        }
        return max(0L, gross - contractsMonthly)
    }

    /**
     * What [status] still holds back from the money left in the period: for a running pot what can
     * still be spent from it, without what was carried over from earlier periods, for the others
     * their share of the period less what was spent from them in it. Contracts still expected are
     * taken off by the monthly balance already.
     */
    private fun reserved(status: PotStatus, plannedInPeriod: Long): Long = when (status.pot.kind) {
        PotKind.RUNNING -> max(0L, status.pot.amount - status.spent - status.upcoming)
        // Only watched, the money is in other pots or none
        PotKind.ITEM_GROUP -> 0L
        else -> max(0L, plannedInPeriod - status.spentInPeriod)
    }
}

/**
 * The running pot that will be overspent most at the end of the current period at the pace so far,
 * null if none will be
 */
fun BudgetOverview.paceWarning(): PotStatus? = pots
    .filter { it.pot.kind == PotKind.RUNNING && !it.isOverspent }
    .mapNotNull { status -> status.projected?.let { status to it - (status.planned + status.carried) } }
    .filter { (_, over) -> over > 0 }
    .maxByOrNull { (_, over) -> over }
    ?.first
