package org.totschnig.myexpenses.next.budgets

import org.totschnig.myexpenses.next.OverviewBudget
import org.totschnig.myexpenses.next.data.budgets.BudgetOverview
import org.totschnig.myexpenses.next.feature.budgets.icon

/** The pots for the rings of the overview */
fun BudgetOverview.overviewBudgets(): List<OverviewBudget> = pots.map { status ->
    OverviewBudget(
        key = status.pot.id, title = status.pot.name, progress = status.progress, icon = status.pot.kind.icon,
        catalogIcon = status.pot.iconName
    )
}
