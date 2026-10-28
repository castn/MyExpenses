package org.totschnig.myexpenses.next.data.budgets

import java.time.LocalDate
import java.util.UUID
import org.totschnig.myexpenses.next.core.common.icons.IconCatalog

/**
 * How a [Pot] is filled and spent from
 */
enum class PotKind {
    /** The same amount for each period of the budget, e.g. Ausgehen 150 € a month */
    RUNNING,

    /**
     * An amount per year for what is bought now and then, e.g. Geschenke 600 € a year. A twelfth
     * of it is added each month, what is left stays in the pot.
     */
    YEARLY,

    /** Money saved towards something, e.g. a new game for 70 € until December */
    GOAL,

    /** A total for a trip or project, e.g. Urlaub Italien 1500 € */
    PROJECT,

    /** A share of what is bought on scanned receipts, e.g. sweets at most 8 %, only to watch */
    ITEM_GROUP;

    /**
     * Whether the pot stands for a purpose of its own, so that what it holds is paid from it and not
     * from the everyday pots, see [PotMatcher]
     */
    val isPurpose: Boolean get() = this == GOAL || this == PROJECT
}

/**
 * A part of the budget, defined by the user. Which transactions it holds is defined by
 * [categoryIds], [tagIds], [payeeIds] and [contractRuleIds]; a transaction matching any of them
 * belongs to the pot, see [PotMatcher].
 *
 * @param id stable across changes, also when renamed
 * @param amount in minor units of the home currency: per period for [PotKind.RUNNING], per year for
 * [PotKind.YEARLY], the target for [PotKind.GOAL], the total for [PotKind.PROJECT], unused for
 * [PotKind.ITEM_GROUP]
 * @param categoryIds categories with all their subcategories
 * @param contractRuleIds ids of [org.totschnig.myexpenses.next.contracts.ContractRule]s whose
 * payments belong to the pot, e.g. the gym in Fitness
 * @param rollover for [PotKind.RUNNING]: what is left, or overspent, is carried to the next period
 * @param start from when the pot counts: for [PotKind.RUNNING] the first period that is carried
 * over (it holds the whole period, also what was spent before the pot was made), for
 * [PotKind.YEARLY] the day the first share is added, for [PotKind.GOAL] the day saving began, for
 * [PotKind.PROJECT] the first day; null for always
 * @param end for [PotKind.GOAL] the day the money is needed, for [PotKind.PROJECT] the last day
 * @param reserveRuleIds for [PotKind.GOAL]: reserve contracts whose payments are saved for it
 * @param savingAccountIds for [PotKind.GOAL]: money moved to these own accounts is saved for it
 * @param itemGroups for [PotKind.ITEM_GROUP]: ids of [org.totschnig.myexpenses.next.receipts.ItemGroup]s
 * @param maxSharePercent for [PotKind.ITEM_GROUP]: the share of the scanned purchases not to exceed
 * @param icon name of a category icon, null for the icon of the kind
 */
data class Pot(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: PotKind,
    val amount: Long = 0,
    val categoryIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val payeeIds: Set<Long> = emptySet(),
    val contractRuleIds: Set<String> = emptySet(),
    val rollover: Boolean = false,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val reserveRuleIds: Set<String> = emptySet(),
    val savingAccountIds: Set<Long> = emptySet(),
    val itemGroups: Set<String> = emptySet(),
    val maxSharePercent: Int? = null,
    val icon: String? = null,
) {
    /** The icon chosen, else one of the [IconCatalog] that fits the name, null if none does */
    val iconName: String? get() = icon ?: IconCatalog.bestFor(name)

    /** Whether the pot holds transactions at all, unlike [PotKind.ITEM_GROUP] */
    val holdsTransactions: Boolean
        get() = kind != PotKind.ITEM_GROUP &&
                (categoryIds.isNotEmpty() || tagIds.isNotEmpty() || payeeIds.isNotEmpty() || contractRuleIds.isNotEmpty())

    /**
     * Whether a transaction on [date] can belong to the pot: from [start] and, for a project, until
     * [end]. A running pot holds whole periods, so it is always open.
     */
    fun isOpenOn(date: LocalDate): Boolean = kind == PotKind.RUNNING ||
            (start == null || !date.isBefore(start)) && (kind != PotKind.PROJECT || end == null || !date.isAfter(end))
}
