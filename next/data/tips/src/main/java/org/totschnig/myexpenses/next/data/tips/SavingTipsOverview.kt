package org.totschnig.myexpenses.next.data.tips

/**
 * Tips are found in the analysed transactions, so they are shown as
 * [org.totschnig.myexpenses.next.ledger.AnalysisState]
 *
 * @param tips shown, highest amount first
 * @param dismissed by the user, and nothing new came up since
 * @param newIds ids of the [tips] the user has not seen yet
 */
data class SavingTipsOverview(
    val tips: List<SavingTip>,
    val dismissed: List<SavingTip> = emptyList(),
    val newIds: Set<String> = emptySet(),
)
