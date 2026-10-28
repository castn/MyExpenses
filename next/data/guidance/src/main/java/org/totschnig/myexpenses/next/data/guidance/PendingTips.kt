package org.totschnig.myexpenses.next.data.guidance

import org.totschnig.myexpenses.next.data.tips.DepositNotReturned
import org.totschnig.myexpenses.next.data.tips.DrugstoreInSupermarkets
import org.totschnig.myexpenses.next.data.tips.ItemGroupRising
import org.totschnig.myexpenses.next.data.tips.Outliers

/**
 * A saving tip that cannot say anything yet, since data is missing, not since there is nothing to
 * say. Shown below the tips, so that the user sees what precise data would bring.
 */
enum class PendingTip {
    /** Needs scanned receipts spread over the year, see [ItemGroupRising] */
    ITEM_GROUPS_RISING,

    /** Needs scanned receipts of supermarkets, see [DrugstoreInSupermarkets] */
    DRUGSTORE_IN_SUPERMARKETS,

    /** Needs scanned receipts, see [DepositNotReturned] */
    DEPOSIT_NOT_RETURNED,

    /** Needs precise categories, see [Outliers] */
    OUTLIERS;

    companion object {
        /** With fewer scanned receipts within a year, a missing tip about them tells nothing */
        const val MIN_RECEIPTS = 5
    }
}

/**
 * The tips that lack data, in the order of [PendingTip]. A tip that is there, shown or dismissed,
 * is not pending, nor is one that needs receipts when they cannot be scanned at all.
 *
 * @param tipIds of all tips, dismissed ones included, see [org.totschnig.myexpenses.next.tips.SavingTip.id]
 */
fun pendingTips(readiness: DataReadiness, tipIds: Set<String>): List<PendingTip> = PendingTip.entries.filter { tip ->
    when (tip) {
        PendingTip.ITEM_GROUPS_RISING -> readiness.canScanReceipts &&
                tipIds.none { it.startsWith(ItemGroupRising.ID_PREFIX) } &&
                !ItemGroupRising.hasEnough(readiness.scannedRecent, readiness.scannedUsual)

        PendingTip.DRUGSTORE_IN_SUPERMARKETS -> readiness.canScanReceipts &&
                DrugstoreInSupermarkets.ID !in tipIds &&
                readiness.scannedSupermarketLastYear < PendingTip.MIN_RECEIPTS

        PendingTip.DEPOSIT_NOT_RETURNED -> readiness.canScanReceipts &&
                DepositNotReturned.ID !in tipIds &&
                readiness.scannedLastYear < PendingTip.MIN_RECEIPTS

        PendingTip.OUTLIERS -> Outliers.ID !in tipIds &&
                readiness.gaps.any { it is DataGap.Uncategorized || it is DataGap.VagueCategories }
    }
}
