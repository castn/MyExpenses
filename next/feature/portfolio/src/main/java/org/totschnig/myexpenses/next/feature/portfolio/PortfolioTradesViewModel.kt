package org.totschnig.myexpenses.next.feature.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.portfolio.Portfolio
import org.totschnig.myexpenses.next.data.portfolio.Trade
import org.totschnig.myexpenses.next.data.portfolio.TradeStore

/** A portfolio with its trades, the latest first */
data class PortfolioTrades(val portfolio: Portfolio, val trades: List<Trade>)

/** The trades on the pages of the portfolios, one state per portfolio */
class PortfolioTradesViewModel(
    private val store: TradeStore,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val pages = mutableMapOf<Long, StateFlow<PortfolioTrades?>>()

    /** The trades of [portfolioId], null while they load or if it is no portfolio */
    fun trades(portfolioId: Long): StateFlow<PortfolioTrades?> = pages.getOrPut(portfolioId) {
        combine(store.portfolio(portfolioId), store.trades(portfolioId)) { portfolio, trades ->
            portfolio?.let { PortfolioTrades(it, trades) }
        }.flowOn(io).stateWhileShown(viewModelScope, null)
    }
}
