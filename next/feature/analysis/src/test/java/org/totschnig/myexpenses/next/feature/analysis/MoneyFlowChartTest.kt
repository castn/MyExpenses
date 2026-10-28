package org.totschnig.myexpenses.next.feature.analysis

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.charts.ChartColor
import org.totschnig.myexpenses.charts.ChartPalette
import org.totschnig.myexpenses.charts.SankeyFlow
import org.totschnig.myexpenses.charts.layout.SankeyHit
import org.totschnig.myexpenses.next.data.analysis.FlowActor
import org.totschnig.myexpenses.next.data.analysis.FlowGroup
import org.totschnig.myexpenses.next.data.analysis.MoneyFlow
import org.totschnig.myexpenses.next.data.analysis.PeriodKind
import org.totschnig.myexpenses.next.data.analysis.SpendingPeriod

class MoneyFlowChartTest {

    private val labels = FlowLabels(
        accounts = "Accounts", noCategory = "No category", noPayee = "No payee", others = "Others",
        otherActors = { "$it more" }, left = "Left", savings = "Savings",
    )
    private val period = SpendingPeriod.of(PeriodKind.TWELVE_MONTHS, 0, LocalDate.of(2026, 10, 13))

    private fun flow(incomes: List<FlowGroup>, expenses: List<FlowGroup>, order: List<Long> = expenses.map { it.key }) =
        MoneyFlow(period, 0, true, incomes, expenses, order)

    private val salary = FlowGroup(30, "Salary", 3000, 1, listOf(1))
    private val food = FlowGroup(
        10, "Food", 700, 3, listOf(2, 3, 4),
        listOf(FlowActor("REWE", 500, 1, listOf(2)), FlowActor(null, 200, 2, listOf(3, 4), others = 2))
    )
    private val home = FlowGroup(20, "Home", 1200, 1, listOf(5), listOf(FlowActor(null, 1200, 1, listOf(5))))

    private fun chart(flow: MoneyFlow) = MoneyFlowChart.of(flow, labels) { "$it €" }

    @Test
    fun flowsFromIncomesOverTheAccountsToCategoriesAndActors() {
        val chart = chart(flow(listOf(salary), listOf(home, food)))
        assertEquals(
            listOf("Salary", "Accounts", "Home", "No payee", "Food", "REWE", "2 more", "Left"),
            chart.nodes.map { it.label }
        )
        assertEquals(listOf(0, 1, 2, 3, 2, 3, 3, 2), chart.nodes.map { it.column })
        assertEquals(
            listOf(
                SankeyFlow(0, 1, 3000f), SankeyFlow(1, 2, 1200f), SankeyFlow(2, 3, 1200f),
                SankeyFlow(1, 4, 700f), SankeyFlow(4, 5, 500f), SankeyFlow(4, 6, 200f), SankeyFlow(1, 7, 1100f)
            ),
            chart.flows
        )
        assertEquals("1100 €", chart.nodes.last().valueLabel)
    }

    @Test
    fun tellsWhatWasTapped() {
        val chart = chart(flow(listOf(salary), listOf(home, food)))
        assertEquals(FlowItem("Food → REWE", 500, 1, listOf(2)), chart.itemOf(SankeyHit.Link(4)))
        assertEquals(FlowItem("Food → REWE", 500, 1, listOf(2)), chart.itemOf(SankeyHit.Node(5)))
        assertEquals(FlowItem("Food", 700, 3, listOf(2, 3, 4)), chart.itemOf(SankeyHit.Node(4)))
        assertEquals(FlowItem("Left", 1100, null, emptyList()), chart.itemOf(SankeyHit.Node(7)))
        assertEquals("Food → REWE: 500 €", chart.nodes[5].description)
    }

    @Test
    fun takesFromSavingsWhenMoreWasSpent() {
        val chart = chart(flow(listOf(salary.copy(amount = 1000)), listOf(home)))
        assertEquals(listOf("Salary", "Savings", "Accounts", "Home", "No payee"), chart.nodes.map { it.label })
        assertEquals(SankeyFlow(1, 2, 200f), chart.flows[1])
        assertEquals(ChartColor.Neutral, chart.nodes[1].color)
        assertEquals(ChartColor.Series(ChartPalette.GREEN), chart.nodes[0].color)
    }

    @Test
    fun categoriesKeepTheirColorsInAllPeriods() {
        val rest = FlowGroup(MoneyFlow.REST, null, 50, 1, listOf(9))
        val colors = MoneyFlowChart.colorsOf(listOf(food, home, rest), order = listOf(20, 99, 10))
        val slots = ChartPalette.withoutGreen
        assertEquals(ChartColor.Series(slots[0]), colors[20])
        assertEquals(ChartColor.Series(slots[2]), colors[10])
        assertEquals(ChartColor.Neutral, colors[MoneyFlow.REST])
        // Not among the largest of the ledger: the first free color
        val other = FlowGroup(40, "Other", 10, 1, listOf(8))
        assertEquals(ChartColor.Series(slots[1]), MoneyFlowChart.colorsOf(listOf(home, other), listOf(20))[40])
    }
}
