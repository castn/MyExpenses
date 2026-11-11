package org.totschnig.myexpenses.next.data.portfolio

import java.math.RoundingMode
import kotlinx.coroutines.flow.Flow

/** Loads and saves the trades of portfolios, implemented by the app */
interface TradeStore {
    /** [id] with its cash, holdings and rounding, null if it is no portfolio or was deleted */
    fun portfolio(id: Long): Flow<Portfolio?>

    /** The trades of portfolio [portfolioId], the latest first */
    fun trades(portfolioId: Long): Flow<List<Trade>>

    /** The trade of transaction [id], null if it is none */
    suspend fun trade(id: Long): Trade?

    /** The securities, crypto currencies and commodities known to the app, to buy one not held yet */
    suspend fun assets(): List<Asset>

    /** The accounts a trade of [portfolio] can be funded from and the other portfolios */
    suspend fun accounts(portfolio: Portfolio): TradeAccounts

    /** The categories of the costs of the trade saved last, for the next one */
    suspend fun lastFeeCategories(): List<CostCategory>

    /** How the principal of trades in [portfolioId] is rounded, see [Portfolio.rounding] */
    suspend fun setRoundingMode(portfolioId: Long, mode: RoundingMode)

    /** Transactions of [MatchQuery.accountId] that may be the other side of a trade */
    fun matching(query: MatchQuery): Flow<List<MatchingTransaction>>

    /**
     * Saves [values] in portfolio [portfolioId], creating the account of an asset not held yet, and
     * remembers the categories of its costs, see [lastFeeCategories]
     */
    suspend fun save(portfolioId: Long, values: TradeValues)

    /** Adds a new asset, null if its code is taken */
    suspend fun createAsset(asset: Asset): Asset?
}
