package org.totschnig.myexpenses.next

import android.content.Context
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.model.AccountGroupingKey
import org.totschnig.myexpenses.model.PREDEFINED_NAME_BANK
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CASH
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CCARD
import org.totschnig.myexpenses.viewmodel.data.FullAccount

/**
 * Account types that are shown together in a single "Daily accounts" section of the overview,
 * when accounts are grouped by type.
 */
private val dailyAccountTypes = setOf(PREDEFINED_NAME_CASH, PREDEFINED_NAME_BANK, PREDEFINED_NAME_CCARD)

/**
 * Groups [accounts] into titled sections for the overview, following the user's [grouping].
 * With grouping by type, cash, bank and credit card accounts are merged into one section
 * that comes first, all other types keep their own section.
 */
fun groupAccountsForOverview(
    accounts: List<FullAccount>,
    grouping: AccountGrouping<*>,
    context: Context,
): List<Pair<String, List<FullAccount>>> {
    val (daily, others) = if (grouping == AccountGrouping.TYPE)
        accounts.partition { it.type.name in dailyAccountTypes }
    else emptyList<FullAccount>() to accounts

    @Suppress("UNCHECKED_CAST")
    val comparator = grouping.comparator as Comparator<in AccountGroupingKey>
    val grouped = others.groupBy { grouping.getGroupKey(it) }
    val sections = grouped.keys.sortedWith(comparator).map { it.title(context) to grouped.getValue(it) }

    return if (daily.isEmpty()) sections
    else listOf(context.getString(R.string.next_daily_accounts) to daily) + sections
}
