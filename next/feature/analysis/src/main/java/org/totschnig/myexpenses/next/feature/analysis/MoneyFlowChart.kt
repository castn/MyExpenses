package org.totschnig.myexpenses.next.feature.analysis

import org.totschnig.myexpenses.charts.ChartColor
import org.totschnig.myexpenses.charts.ChartPalette
import org.totschnig.myexpenses.charts.SankeyFlow
import org.totschnig.myexpenses.charts.SankeyNode
import org.totschnig.myexpenses.charts.layout.SankeyHit
import org.totschnig.myexpenses.next.data.analysis.FlowGroup
import org.totschnig.myexpenses.next.data.analysis.MoneyFlow

/**
 * What a node or band of the money flow stands for, shown when it is tapped
 *
 * @param title e.g. "Eating out → McDonald's"
 * @param count of the transactions, null for what is no group of transactions, e.g. what is left
 */
data class FlowItem(val title: String, val amount: Long, val count: Int?, val transactionIds: List<Long>)

/** The texts of a [MoneyFlowChart] that do not come from the data */
data class FlowLabels(
    val accounts: String,
    val noCategory: String,
    val noPayee: String,
    val others: String,
    /** E.g. "3 more" */
    val otherActors: (Int) -> String,
    val left: String,
    val savings: String,
)

/**
 * A [MoneyFlow] as Sankey diagram: incomes, the accounts, the expenses with what is left, and the
 * actors of each expense. Incomes are green, each category keeps its color in all periods, see
 * [MoneyFlow.expenseOrder], its actors share it. Rests and totals are neutral.
 */
class MoneyFlowChart private constructor(
    val nodes: List<SankeyNode>,
    val flows: List<SankeyFlow>,
    private val nodeItems: List<FlowItem>,
    private val flowItems: List<FlowItem>,
) {
    fun itemOf(hit: SankeyHit): FlowItem? = when (hit) {
        is SankeyHit.Node -> nodeItems.getOrNull(hit.index)
        is SankeyHit.Link -> flowItems.getOrNull(hit.index)
    }

    companion object {
        fun of(flow: MoneyFlow, labels: FlowLabels, format: (Long) -> String): MoneyFlowChart {
            val nodes = mutableListOf<SankeyNode>()
            val items = mutableListOf<FlowItem>()
            val flows = mutableListOf<SankeyFlow>()
            val flowItems = mutableListOf<FlowItem>()
            fun node(label: String, value: Long, column: Int, color: ChartColor, item: FlowItem): Int {
                nodes += SankeyNode(label, format(value), column, color, "${item.title}: ${format(value)}")
                items += item
                return nodes.lastIndex
            }
            fun link(source: Int, target: Int, value: Long, item: FlowItem) {
                flows += SankeyFlow(source, target, value.toFloat())
                flowItems += item.copy(title = "${nodes[source].label} → ${nodes[target].label}")
            }
            fun FlowGroup.name() = if (isRest) labels.others else label ?: labels.noCategory
            fun FlowGroup.item() = FlowItem(name(), amount, count, transactionIds)

            val incomeNodes = flow.incomes.map { income ->
                node(income.name(), income.amount, 0, ChartColor.Series(ChartPalette.GREEN), income.item()) to income
            }
            val savings = (-flow.left).takeIf { it > 0 }?.let {
                node(labels.savings, it, 0, ChartColor.Neutral, FlowItem(labels.savings, it, null, emptyList()))
            }
            val total = maxOf(flow.earned, flow.spent)
            val accounts = node(
                labels.accounts, total, 1, ChartColor.Neutral,
                FlowItem(labels.accounts, total, null, emptyList())
            )
            incomeNodes.forEach { (index, income) -> link(index, accounts, income.amount, income.item()) }
            savings?.let { link(it, accounts, -flow.left, items[it]) }

            val colors = colorsOf(flow.expenses, flow.expenseOrder)
            flow.expenses.forEach { expense ->
                val color = colors.getValue(expense.key)
                val category = node(expense.name(), expense.amount, 2, color, expense.item())
                link(accounts, category, expense.amount, expense.item())
                expense.actors.forEach { actor ->
                    val name = if (actor.isRest) labels.otherActors(actor.others) else actor.name ?: labels.noPayee
                    val item = FlowItem(name, actor.amount, actor.count, actor.transactionIds)
                    val target = node(name, actor.amount, 3, color, item.copy(title = "${expense.name()} → $name"))
                    link(category, target, actor.amount, item)
                }
            }
            flow.left.takeIf { it > 0 }?.let { left ->
                val node = node(labels.left, left, 2, ChartColor.Neutral, FlowItem(labels.left, left, null, emptyList()))
                link(accounts, node, left, items[node])
            }
            return MoneyFlowChart(nodes, flows, items, flowItems)
        }

        /**
         * The color of each expense: the largest of the whole ledger take the colors in their
         * order, the others the colors that are free, the rest is neutral
         */
        internal fun colorsOf(expenses: List<FlowGroup>, order: List<Long>): Map<Long, ChartColor> {
            val slots = ChartPalette.withoutGreen
            val fixed = expenses.filterNot { it.isRest }
                .mapNotNull { expense -> order.indexOf(expense.key).takeIf { it in slots.indices }?.let { expense.key to slots[it] } }
                .toMap()
            val free = (slots - fixed.values.toSet()).iterator()
            return expenses.associate { expense ->
                expense.key to when {
                    expense.isRest -> ChartColor.Neutral
                    expense.key in fixed -> ChartColor.Series(fixed.getValue(expense.key))
                    free.hasNext() -> ChartColor.Series(free.next())
                    else -> ChartColor.Neutral
                }
            }
        }
    }
}
