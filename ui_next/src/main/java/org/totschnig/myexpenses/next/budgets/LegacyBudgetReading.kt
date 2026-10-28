package org.totschnig.myexpenses.next.budgets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.loadBudgetProgress
import org.totschnig.myexpenses.provider.KEY_BUDGET
import org.totschnig.myexpenses.provider.KEY_BUDGETID
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_SECOND_GROUP
import org.totschnig.myexpenses.provider.KEY_YEAR
import org.totschnig.myexpenses.provider.TransactionProvider.BUDGET_ALLOCATIONS_URI
import org.totschnig.myexpenses.provider.filter.CategoryCriterion
import org.totschnig.myexpenses.provider.filter.FilterPersistence
import org.totschnig.myexpenses.provider.filter.PayeeCriterion
import org.totschnig.myexpenses.provider.filter.TagCriterion
import org.totschnig.myexpenses.provider.filter.asSet
import org.totschnig.myexpenses.provider.getIntOrNull
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.viewmodel.BudgetViewModel
import org.totschnig.myexpenses.viewmodel.data.Budget
import org.totschnig.myexpenses.next.data.budgets.LegacyBudget
import org.totschnig.myexpenses.next.data.budgets.LegacyBudgets
import org.totschnig.myexpenses.next.data.budgets.LegacyPeriod

/** Reads the budgets of the classic screens, which [LegacyBudgets.pots] takes over */
object LegacyBudgetReading {
    /** Reads what [pots] needs of [budgets] */
    suspend fun load(repository: Repository, budgets: List<Budget>): List<LegacyBudget> = withContext(Dispatchers.IO) {
        budgets.mapNotNull { budget ->
            val progress = repository.loadBudgetProgress(budget.id, null) ?: return@mapNotNull null
            val criteria = FilterPersistence(repository.dataStore, BudgetViewModel.prefNameForCriteria(budget.id))
                .getValue().asSet
            LegacyBudget(
                title = budget.title,
                currency = budget.currency,
                grouping = LegacyPeriod.valueOf(budget.grouping.name),
                start = budget.start,
                end = budget.end,
                total = progress.allocated,
                categoryAllocations = categoryAllocations(repository, budget.id, progress.groupInfo.year, progress.groupInfo.second),
                categoryIds = criteria.filterIsInstance<CategoryCriterion>().flatMapTo(HashSet()) { it.values },
                tagIds = criteria.filterIsInstance<TagCriterion>().flatMapTo(HashSet()) { it.values },
                payeeIds = criteria.filterIsInstance<PayeeCriterion>().flatMapTo(HashSet()) { it.values }
            )
        }
    }

    /**
     * The amount per category in the period [year] / [second]: the latest one set up to that period,
     * since an amount applies to the following periods until it is changed
     */
    private fun categoryAllocations(repository: Repository, budgetId: Long, year: Int?, second: Int?): Map<Long, Long> =
        repository.contentResolver.query(
            BUDGET_ALLOCATIONS_URI,
            arrayOf(KEY_CATID, KEY_YEAR, KEY_SECOND_GROUP, KEY_BUDGET),
            "$KEY_BUDGETID = ? AND $KEY_CATID != 0",
            arrayOf(budgetId.toString()),
            null
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Allocation(
                            cursor.getLong(KEY_CATID),
                            cursor.getIntOrNull(KEY_YEAR) ?: 0,
                            cursor.getIntOrNull(KEY_SECOND_GROUP) ?: 0,
                            cursor.getLong(KEY_BUDGET)
                        )
                    )
                }
            }
        }.orEmpty()
            .filter { year == null || it.year < year || it.year == year && it.second <= (second ?: 0) }
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.maxWith(compareBy({ it.year }, { it.second })).amount }

    private class Allocation(val categoryId: Long, val year: Int, val second: Int, val amount: Long)}
