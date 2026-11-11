package org.totschnig.myexpenses.next.feature.portfolio

import org.totschnig.myexpenses.next.data.portfolio.TradeKind

/** What the screen of a trade starts with */
sealed interface TradeStart {
    /** A new trade of [kind] in portfolio [portfolioId] */
    data class New(val portfolioId: Long, val kind: TradeKind) : TradeStart

    /** Changing trade [tradeId], the id of its transaction in the portfolio */
    data class Edit(val tradeId: Long) : TradeStart
}
