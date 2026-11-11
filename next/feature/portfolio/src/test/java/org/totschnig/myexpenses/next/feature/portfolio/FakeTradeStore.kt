package org.totschnig.myexpenses.next.feature.portfolio

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.portfolio.AccountChoice
import org.totschnig.myexpenses.next.data.portfolio.Asset
import org.totschnig.myexpenses.next.data.portfolio.AssetKind
import org.totschnig.myexpenses.next.data.portfolio.CostCategory
import org.totschnig.myexpenses.next.data.portfolio.CostLeg
import org.totschnig.myexpenses.next.data.portfolio.Holding
import org.totschnig.myexpenses.next.data.portfolio.MatchQuery
import org.totschnig.myexpenses.next.data.portfolio.MatchingTransaction
import org.totschnig.myexpenses.next.data.portfolio.Portfolio
import org.totschnig.myexpenses.next.data.portfolio.Trade
import org.totschnig.myexpenses.next.data.portfolio.TradeAccounts
import org.totschnig.myexpenses.next.data.portfolio.TradeKind
import org.totschnig.myexpenses.next.data.portfolio.TradeStore
import org.totschnig.myexpenses.next.data.portfolio.TradeValues

/** A portfolio with Apple shares and cash, a giro account to fund trades and a second portfolio */
internal class FakeTradeStore : TradeStore {
    val apple = Asset("AAPL", "Apple", 0, AssetKind.SECURITY)
    val bitcoin = Asset("BTC", "Bitcoin", 8, AssetKind.CRYPTO)
    val fees = CostCategory(7, "Gebühren")
    val giro = AccountChoice(3, "Giro")

    val portfolio = MutableStateFlow<Portfolio?>(
        Portfolio(
            id = 1,
            label = "Depot",
            currency = CurrencyInfo("EUR", "€", 2),
            isSealed = false,
            cashBalance = 50000,
            holdings = listOf(Holding(11, apple, 10, BigDecimal("180.5")))
        )
    )
    val trades = MutableStateFlow(
        listOf(
            Trade(
                id = 5,
                portfolioId = 1,
                kind = TradeKind.SELL,
                date = LocalDateTime.of(2026, 3, 1, 10, 0),
                asset = apple,
                quantity = 4,
                price = BigDecimal("175.25"),
                principal = 70100,
                costs = listOf(CostLeg(495, fees)),
                peer = giro,
                comment = "Teilverkauf"
            )
        )
    )
    val matches = MutableStateFlow<Map<MatchQuery, List<MatchingTransaction>>>(emptyMap())
    val saved = mutableListOf<Pair<Long, TradeValues>>()
    val createdAssets = mutableListOf<Asset>()
    var lastFees = listOf(fees)
    var failSave = false

    override fun portfolio(id: Long): Flow<Portfolio?> =
        portfolio.map { current -> current?.takeIf { it.id == id } }

    override fun trades(portfolioId: Long): Flow<List<Trade>> =
        trades.map { list -> list.filter { it.portfolioId == portfolioId } }

    override suspend fun trade(id: Long) = trades.value.find { it.id == id }

    override suspend fun assets() = listOf(apple, bitcoin) + createdAssets

    override suspend fun accounts(portfolio: Portfolio) =
        TradeAccounts(listOf(giro), listOf(AccountChoice(2, "Zweitdepot")))

    override suspend fun lastFeeCategories() = lastFees

    override suspend fun setRoundingMode(portfolioId: Long, mode: RoundingMode) {
        portfolio.value = portfolio.value?.copy(rounding = mode)
    }

    override fun matching(query: MatchQuery): Flow<List<MatchingTransaction>> =
        matches.map { it[query].orEmpty() }

    override suspend fun save(portfolioId: Long, values: TradeValues) {
        check(!failSave) { "closed" }
        saved += portfolioId to values
        lastFees = values.costs.mapNotNull { it.category }
    }

    override suspend fun createAsset(asset: Asset): Asset? {
        if (assets().any { it.code == asset.code }) return null
        createdAssets += asset
        return asset
    }
}
