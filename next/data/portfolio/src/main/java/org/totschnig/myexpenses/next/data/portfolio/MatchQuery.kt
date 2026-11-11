package org.totschnig.myexpenses.next.data.portfolio

import java.math.RoundingMode
import java.time.LocalDate

/**
 * The transactions of the funding account to look for as the other side of the trade: the same
 * amount, money out for a buy, on the same day
 *
 * @param amount in minor units, negative for money out
 */
data class MatchQuery(val accountId: Long, val amount: Long, val day: LocalDate)

/** What to look for, null unless funded from an account of the app */
fun TradeDraft.matchQuery(figures: TradeFigures): MatchQuery? {
    val accountId =
        peerAccountId?.takeIf { funding == Funding.ACCOUNT && !kind.isTransfer } ?: return null
    val total = figures.total ?: return null
    return MatchQuery(accountId, if (kind.isIncoming) -total else total, date.toLocalDate())
}

/**
 * The transaction linked after [matches] were found: the one linked before while it still matches,
 * otherwise the only match
 */
fun TradeDraft.linkAfter(matches: List<MatchingTransaction>): TradeDraft {
    if (matches.any { it.id == linkedTransactionId }) return this
    return copy(linkedTransactionId = matches.singleOrNull()?.id)
}

/** The rounding modes offered for the principal of a trade */
val roundingChoices = listOf(
    RoundingMode.UP,
    RoundingMode.DOWN,
    RoundingMode.HALF_UP,
    RoundingMode.HALF_DOWN,
    RoundingMode.HALF_EVEN
)
