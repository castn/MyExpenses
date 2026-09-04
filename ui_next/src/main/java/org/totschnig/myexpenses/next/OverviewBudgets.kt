package org.totschnig.myexpenses.next

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.viewmodel.BudgetListViewModel

/**
 * All budgets for the overview, each with the share of its allocation that has been spent
 * in the current period. Uses the same amounts as the budget list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun BudgetListViewModel.overviewBudgets(): Flow<List<OverviewBudget>> =
    data.flatMapLatest { budgets ->
        if (budgets.isEmpty()) flowOf(emptyList())
        else combine(budgets.map { budget ->
            budgetAmounts(budget).map { (spent, allocated) ->
                OverviewBudget(
                    id = budget.id,
                    title = budget.title,
                    // Expenses are negative
                    progress = when {
                        allocated != 0L -> -spent.toFloat() / allocated
                        spent < 0 -> 1f
                        else -> 0f
                    }
                )
            }
        }) { it.toList() }
    }
