package org.totschnig.myexpenses.next.data.guidance

import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.data.merchants.supermarketOf

/**
 * What a hint is about. The user closes a hint for its topic, so that it does not come back in
 * another place: once the user knows about receipts, no hint tells about them again.
 */
enum class HintTopic {
    /** Expenses without a precise category are missing from the analyses */
    CATEGORY,

    /** Scanned receipts show what was bought */
    RECEIPT;

    companion object {
        /** A hint after saving is shown so many times at most, also if the user does not close it */
        const val MAX_AFTER_SAVE = 3
    }
}

/**
 * The hint after saving [transaction], null if there is none or the user does not want it
 *
 * @param hasReceipt a receipt is scanned for it
 * @param hidden topics closed by the user or shown often enough after saving
 * @param canScanReceipts whether receipts can be scanned at all, without no hint asks for one
 */
fun hintAfterSave(
    transaction: LedgerTransaction,
    hasReceipt: Boolean,
    hidden: Set<HintTopic>,
    canScanReceipts: Boolean = true,
): HintTopic? {
    val isPurchase = transaction.amount < 0 && transaction.transferAccountId == null &&
            transaction.targetAccountId == null && !transaction.isSplit && !transaction.isSealed
    if (!isPurchase) return null
    return when {
        transaction.categoryId == null && HintTopic.CATEGORY !in hidden -> HintTopic.CATEGORY
        canScanReceipts && isSupermarket(transaction.payeeName) && !hasReceipt && HintTopic.RECEIPT !in hidden ->
            HintTopic.RECEIPT
        else -> null
    }
}

/** Receipts are worth most at supermarkets, where one purchase holds many kinds of goods */
fun isSupermarket(payeeName: String?) = supermarketOf(payeeName) != null

/** The topic a gap of the analysis is about, null if there is no hint for it */
val DataGap.topic: HintTopic?
    get() = when (this) {
        is DataGap.Uncategorized, is DataGap.VagueCategories -> HintTopic.CATEGORY
        is DataGap.FewReceipts -> HintTopic.RECEIPT.takeIf { unscanned.isNotEmpty() }
        DataGap.NoTags -> null
    }

/**
 * The gap the overview tells about, the most useful first, null if none or all are closed
 *
 * @param dismissed topics the user closed
 */
fun overviewHint(readiness: DataReadiness?, dismissed: Set<HintTopic>): DataGap? =
    readiness?.gaps?.firstOrNull { gap -> gap.topic?.let { it !in dismissed } == true }
