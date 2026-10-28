package org.totschnig.myexpenses.next.data.analysis

import java.time.LocalDate
import kotlin.math.abs
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.data.merchants.Merchants

/**
 * Who money was paid to or came from, as the money flow shows it: the merchant if it is a known one,
 * e.g. "REWE" for "REWE Markt GmbH Fil. 4711", else the payee, null without payee
 */
fun actorOf(transaction: LedgerTransaction): String? =
    transaction.payeeName?.let { Merchants.of(it)?.name ?: it }

/**
 * Who received money within a category of a [MoneyFlow]
 *
 * @param name see [actorOf], null without payee or for the rest
 * @param amount net, in minor units of the home currency, always positive
 * @param count of the transactions
 * @param others how many actors are summed up in this rest, 0 for a single actor
 */
data class FlowActor(
    val name: String?,
    val amount: Long,
    val count: Int,
    val transactionIds: List<Long>,
    val others: Int = 0,
) {
    val isRest: Boolean get() = others > 0
}

/**
 * Where money came from or went to in a [MoneyFlow]: a main category, the transactions without
 * category, or the rest of the smaller categories
 *
 * @param key the id of the main category, [CategorySpending.NO_CATEGORY] or [MoneyFlow.REST]
 * @param label of the main category, null without category or for the rest
 * @param amount net, in minor units of the home currency, always positive
 * @param count of the transactions
 * @param actors who received the money, largest first, the rest last; empty for incomes
 */
data class FlowGroup(
    val key: Long,
    val label: String?,
    val amount: Long,
    val count: Int,
    val transactionIds: List<Long>,
    val actors: List<FlowActor> = emptyList(),
) {
    val isRest: Boolean get() = key == MoneyFlow.REST
}

/**
 * Where the money of a period came from and went to, in minor units of the home currency: from the
 * [incomes] to the user and from there to the [expenses] and their actors. Each side is complete,
 * so that [earned] minus [spent] is what is [left] of the period.
 *
 * @param offset of [period], see [SpendingPeriod.of]
 * @param hasEarlier whether the period before is fully in the ledger, so that it can be shown
 * @param incomes largest first, the rest last
 * @param expenses largest first, the rest last
 * @param expenseOrder the keys of the expenses over the whole ledger, largest first, so that a
 * category keeps its color in all periods
 */
data class MoneyFlow(
    val period: SpendingPeriod,
    val offset: Int,
    val hasEarlier: Boolean,
    val incomes: List<FlowGroup>,
    val expenses: List<FlowGroup>,
    val expenseOrder: List<Long> = emptyList(),
) {
    val earned: Long get() = incomes.sumOf { it.amount }
    val spent: Long get() = expenses.sumOf { it.amount }

    /** Negative if more was spent than earned */
    val left: Long get() = earned - spent

    val isCurrent: Boolean get() = offset == 0
    val isEmpty: Boolean get() = incomes.isEmpty() && expenses.isEmpty()

    companion object {
        /** [FlowGroup.key] of the rest of the smaller categories */
        const val REST = -2L
        const val MAX_INCOMES = 5
        const val MAX_EXPENSES = 8
        const val MAX_ACTORS = 6
    }
}

object MoneyFlowAnalyzer {

    /**
     * Where the money of a period came from and went to, counted like [CategoryAnalyzer]: without
     * movements between own accounts and transactions in [excluded], split transactions with their
     * parts, credits in a category for expenses as returns.
     *
     * Each main category is a group, the transactions without category two: the debits and the
     * credits. Its net amount tells the side: a category for expenses with more returns than
     * purchases is an income, a category for income with more debits than credits an expense.
     * The smaller groups of a side are summed up into a rest, see [MoneyFlow.MAX_INCOMES] and
     * [MoneyFlow.MAX_EXPENSES].
     *
     * @param categories of the user by id, see [org.totschnig.myexpenses.next.ledger.Catalog.categoriesById]
     */
    fun analyse(
        ledger: Ledger,
        excluded: Set<Long>,
        categories: Map<Long, CategoryInfo>,
        kind: PeriodKind,
        offset: Int,
        today: LocalDate,
    ): MoneyFlow {
        val period = SpendingPeriod.of(kind, offset, today)
        val (incomes, expenses) = groups(countedParts(ledger, excluded, categories, period))
            .partition { it.net > 0 }
        val wholeLedger = SpendingPeriod(kind, today.minusMonths(Ledger.HISTORY_MONTHS), today)
        val expenseOrder = groups(countedParts(ledger, excluded, categories, wholeLedger))
            .filter { it.net < 0 }
            .sortedBy { it.net }
            .map { it.key }
        val actors = ActorNames()
        return MoneyFlow(
            period = period,
            offset = offset,
            hasEarlier = period.previous().isInLedger(today),
            incomes = incomes.largest(MoneyFlow.MAX_INCOMES).map { it.toFlowGroup(categories, null) },
            expenses = expenses.largest(MoneyFlow.MAX_EXPENSES).map { it.toFlowGroup(categories, actors) },
            expenseOrder = expenseOrder,
        )
    }

    /** Each main category, the debits and the credits without category, without those that net to 0 */
    private fun groups(parts: List<CountedPart>) = parts
        .groupBy { part ->
            part.mainId ?: if (part.isSpending) NO_CATEGORY_DEBITS else NO_CATEGORY_CREDITS
        }
        .map { (key, parts) -> Group(key, parts) }
        .filter { it.net != 0L }

    /** Keys of the transactions without category before they are told apart by side */
    private const val NO_CATEGORY_DEBITS = CategorySpending.NO_CATEGORY
    private const val NO_CATEGORY_CREDITS = -3L

    /**
     * @param net positive for money that came in
     */
    private class Group(val key: Long, val parts: List<CountedPart>) {
        val net: Long = parts.sumOf { it.part.homeAmount }
    }

    /** The largest groups by amount, the others summed up into one rest if there are too many */
    private fun List<Group>.largest(limit: Int): List<Group> {
        val sorted = sortedByDescending { abs(it.net) }
        if (sorted.size <= limit) return sorted
        return sorted.take(limit - 1) + Group(MoneyFlow.REST, sorted.drop(limit - 1).flatMap { it.parts })
    }

    private fun Group.toFlowGroup(categories: Map<Long, CategoryInfo>, actors: ActorNames?): FlowGroup {
        val amount = abs(net)
        return FlowGroup(
            key = when (key) {
                NO_CATEGORY_CREDITS -> CategorySpending.NO_CATEGORY
                else -> key
            },
            label = categories[key]?.label,
            amount = amount,
            count = parts.transactionIds().size,
            transactionIds = parts.transactionIds(),
            actors = actors?.let { actorsOf(parts, amount, it) } ?: emptyList()
        )
    }

    private fun List<CountedPart>.transactionIds() = map { it.transaction.id }.distinct()

    /**
     * Who received the [amount] of an expense: the largest actors by their net amount, and the
     * others as rest. Actors with more returns than payments are part of the rest, so the actors
     * shown never add up to more than [amount], and the rest is what remains.
     */
    private fun actorsOf(parts: List<CountedPart>, amount: Long, names: ActorNames): List<FlowActor> {
        fun spentBy(parts: List<CountedPart>) = -parts.sumOf { it.part.homeAmount }
        val byActor = parts.groupBy { names.of(it.transaction) }.toList()
            .sortedByDescending { (_, parts) -> spentBy(parts) }
        val positive = byActor.filter { (_, parts) -> spentBy(parts) > 0 }
        var shown = if (byActor.size <= MoneyFlow.MAX_ACTORS) positive
        else positive.take(MoneyFlow.MAX_ACTORS - 1)
        while (shown.sumOf { spentBy(it.second) } > amount) shown = shown.dropLast(1)
        val result = shown.map { (name, parts) ->
            FlowActor(name, spentBy(parts), parts.transactionIds().size, parts.transactionIds())
        }
        val restAmount = amount - result.sumOf { it.amount }
        if (restAmount <= 0) return result
        val shownNames = shown.map { it.first }.toSet()
        val others = byActor.filter { it.first !in shownNames }
        val otherParts = others.flatMap { it.second }
        return result + if (others.size == 1) {
            FlowActor(others.single().first, restAmount, otherParts.transactionIds().size, otherParts.transactionIds())
        } else {
            FlowActor(null, restAmount, otherParts.transactionIds().size, otherParts.transactionIds(), others.size)
        }
    }

    /** [actorOf], looked up once per payee name */
    private class ActorNames {
        private val names = HashMap<String, String>()
        fun of(transaction: LedgerTransaction): String? =
            transaction.payeeName?.let { payee -> names.getOrPut(payee) { actorOf(transaction)!! } }
    }
}
