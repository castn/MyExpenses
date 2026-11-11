package org.totschnig.myexpenses.next.portfolio

import android.content.ContentUris
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.TradeSaver
import org.totschnig.myexpenses.db2.insertCurrency
import org.totschnig.myexpenses.db2.loadTrade
import org.totschnig.myexpenses.db2.loadTrades
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.sort.SortDirection
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.portfolio.AccountChoice
import org.totschnig.myexpenses.next.data.portfolio.Asset
import org.totschnig.myexpenses.next.data.portfolio.AssetKind
import org.totschnig.myexpenses.next.data.portfolio.CostCategory
import org.totschnig.myexpenses.next.data.portfolio.CostLeg
import org.totschnig.myexpenses.next.data.portfolio.Funding
import org.totschnig.myexpenses.next.data.portfolio.Holding
import org.totschnig.myexpenses.next.data.portfolio.MatchQuery
import org.totschnig.myexpenses.next.data.portfolio.MatchingTransaction
import org.totschnig.myexpenses.next.data.portfolio.Portfolio
import org.totschnig.myexpenses.next.data.portfolio.Trade
import org.totschnig.myexpenses.next.data.portfolio.TradeAccounts
import org.totschnig.myexpenses.next.data.portfolio.TradeKind
import org.totschnig.myexpenses.next.data.portfolio.TradeStore
import org.totschnig.myexpenses.next.data.portfolio.TradeValues
import org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_SPLIT_PART
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_COMMODITY
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_DISPLAY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TRANSFER_PEER
import org.totschnig.myexpenses.provider.KEY_VALUE
import org.totschnig.myexpenses.provider.PORTFOLIO_CONTAINER
import org.totschnig.myexpenses.provider.PORTFOLIO_NONE
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.getLocalDate
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.util.calculateRealExchangeRate
import org.totschnig.myexpenses.viewmodel.data.CategoryRef
import org.totschnig.myexpenses.viewmodel.data.CostLeg as AppCostLeg
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FullAccount.Companion.fromCursor
import org.totschnig.myexpenses.viewmodel.data.FullAccount.Companion.nest
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.Trade as AppTrade
import org.totschnig.myexpenses.viewmodel.data.TradeIntent
import org.totschnig.myexpenses.viewmodel.data.TradeType
import org.totschnig.myexpenses.viewmodel.data.Transaction2

/**
 * The trades of the portfolios in the database of the app: the accounts with their balances and
 * latest prices as the portfolio's page shows them, saved with [TradeSaver]
 */
class RepositoryTradeStore(
    context: Context,
    private val repository: Repository,
    private val dataStore: DataStore<Preferences>
) : TradeStore {
    private val currencyContext get() = repository.currencyContext
    private val contentResolver get() = repository.contentResolver
    private val tradeSaver by lazy { TradeSaver(repository, context) }

    /** All accounts nested, with the latest prices as on the main screen */
    private val accounts: Flow<List<FullAccount>>
        get() = combine(
            contentResolver.observeQuery(
                uri = TransactionProvider.ACCOUNTS_URI.buildUpon()
                    .appendQueryParameter(
                        TransactionProvider.QUERY_PARAMETER_FULL_PROJECTION_WITH_SUMS,
                        "now"
                    )
                    .build(),
                notifyForDescendants = true
            ).mapToList { it.fromCursor(currencyContext) }.map { it.nest() },
            contentResolver.observeQuery(
                TransactionProvider.LATEST_PRICES_URI
            ).mapToList { cursor ->
                (cursor.getString(KEY_COMMODITY) to cursor.getString(KEY_CURRENCY)) to
                    (
                        cursor.getLocalDate(KEY_DATE) to
                            cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_VALUE))
                        )
            }
        ) { accounts, prices ->
            val priceMap = prices.toMap()
            accounts.map { it.enrich(priceMap, currencyContext.homeCurrencyUnit) }
        }

    private fun fullPortfolio(id: Long): Flow<FullAccount?> =
        accounts.map { list -> list.find { it.id == id && it.isPortfolio } }

    override fun portfolio(id: Long): Flow<Portfolio?> =
        combine(fullPortfolio(id), roundingMode(id)) { account, rounding ->
            account?.toPortfolio(rounding)
        }

    private fun FullAccount.toPortfolio(rounding: RoundingMode) = Portfolio(
        id = id,
        label = label,
        currency = currencyUnit.info,
        isSealed = sealed,
        cashBalance = children.find { it.type.isCashAccount }?.currentBalance ?: 0L,
        holdings = children.filter { it.isPortfolioAsset }.map { child ->
            Holding(
                accountId = child.id,
                asset = child.currencyUnit.asset,
                balance = child.currentBalance,
                price = child.latestExchangeRate?.second?.let {
                    calculateRealExchangeRate(it, child.currencyUnit, currencyUnit)
                }
            )
        },
        rounding = rounding
    )

    override fun trades(portfolioId: Long): Flow<List<Trade>> = contentResolver.observeQuery(
        uri = TransactionProvider.TRANSACTIONS_URI.buildUpon()
            .appendQueryParameter(KEY_ACCOUNTID, portfolioId.toString())
            .build(),
        projection = arrayOf(KEY_ROWID),
        selection = WHERE_NOT_SPLIT_PART,
        notifyForDescendants = true
    ).mapToList { it.getLong(0) }.map { ids ->
        repository.loadTrades(ids, KEY_DATE to SortDirection.DESC).map { it.toTrade() }
    }

    override suspend fun trade(id: Long): Trade? = repository.loadTrade(id)?.toTrade()

    private fun AppTrade.toTrade() = Trade(
        id = id,
        portfolioId = portfolioId,
        kind = when (val type = type) {
            TradeType.AssetTrade.BUY -> TradeKind.BUY
            TradeType.AssetTrade.SELL -> TradeKind.SELL
            TradeType.CashMovement.DEPOSIT -> TradeKind.DEPOSIT
            TradeType.CashMovement.WITHDRAW -> TradeKind.WITHDRAW
            is TradeType.Transfer -> transferKind(type.isIncoming)
        },
        date = date.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
        asset = currencyContext[assetSymbol].asset,
        quantity = quantity.amountMinor,
        price = price,
        principal = principal.amountMinor,
        costs = additionalCosts.map { leg ->
            CostLeg(leg.amount.amountMinor, leg.category?.let { CostCategory(it.id, it.path) })
        }.ifEmpty {
            listOfNotNull(fee?.amountMinor?.takeIf { it > 0L }?.let { CostLeg(it, null) })
        },
        peer = peerAccount?.let { (peerId, label) -> AccountChoice(peerId, label) },
        comment = comment.orEmpty()
    )

    override suspend fun assets(): List<Asset> = currencyContext.getAll().first().filter {
        it.commodityType != CommodityType.FIAT
    }.map { it.asset }

    private fun allAccounts(): List<Account> = contentResolver.query(
        TransactionProvider.ACCOUNTS_URI,
        Account.PROJECTION,
        null,
        null,
        null
    )?.use { cursor ->
        buildList { while (cursor.moveToNext()) add(Account.fromCursor(cursor)) }
    }.orEmpty()

    override suspend fun accounts(portfolio: Portfolio): TradeAccounts {
        val accounts = allAccounts()
        val funding = accounts.filter {
            it.portfolioRole == PORTFOLIO_NONE && it.parentId == null && !it.isSealed &&
                it.currency == portfolio.currency.code
        }
        val portfolios = accounts.filter {
            it.portfolioRole == PORTFOLIO_CONTAINER && it.id != portfolio.id
        }
        return TradeAccounts(
            funding = funding.map { AccountChoice(it.id, it.label) },
            otherPortfolios = portfolios.map { AccountChoice(it.id, it.label) }
        )
    }

    override suspend fun lastFeeCategories(): List<CostCategory> =
        tradeSaver.loadLastFeeCategories().map { CostCategory(it.id, it.path) }

    // As the classic screen kept it
    private fun roundingKey(portfolioId: Long) = stringPreferencesKey("rounding_mode_$portfolioId")

    private fun roundingMode(portfolioId: Long): Flow<RoundingMode> =
        dataStore.data.map { preferences ->
            val name = preferences[roundingKey(portfolioId)]
            RoundingMode.entries.firstOrNull { it.name == name } ?: RoundingMode.HALF_UP
        }

    override suspend fun setRoundingMode(portfolioId: Long, mode: RoundingMode) {
        dataStore.edit { it[roundingKey(portfolioId)] = mode.name }
    }

    override fun matching(query: MatchQuery): Flow<List<MatchingTransaction>> {
        val zone = ZoneId.systemDefault()
        val startOfDay = query.day.atStartOfDay(zone).toEpochSecond()
        val endOfDay = query.day.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1
        return contentResolver.observeQuery(
            uri = TransactionProvider.EXTENDED_URI.buildUpon()
                .appendQueryParameter(TransactionProvider.QUERY_PARAMETER_SEARCH, "1").build(),
            projection = Transaction2.projection(
                isAggregate = false,
                grouping = Grouping.NONE,
                prefHandler = repository.prefHandler
            ),
            selection =
                "$KEY_ACCOUNTID = ? AND $KEY_DISPLAY_AMOUNT = ? AND $KEY_DATE BETWEEN ? AND ? " +
                    "AND $KEY_TRANSFER_PEER IS NULL AND $KEY_PARENTID IS NULL",
            selectionArgs = arrayOf(
                query.accountId.toString(),
                query.amount.toString(),
                startOfDay.toString(),
                endOfDay.toString()
            )
        ).mapToList { cursor ->
            val transaction = Transaction2.fromCursor(
                currencyContext = currencyContext,
                cursor = cursor,
                grouping = Grouping.NONE,
                tags = emptyMap()
            )
            MatchingTransaction(
                id = transaction.id,
                label = transaction.party?.displayName ?: transaction.comment,
                amount = transaction.displayAmount.amountMinor,
                day = LocalDate.ofInstant(
                    java.time.Instant.ofEpochSecond(transaction.dateEpochSeconds),
                    zone
                )
            )
        }
    }

    override suspend fun save(portfolioId: Long, values: TradeValues) {
        val portfolio =
            checkNotNull(fullPortfolio(portfolioId).first()) { "No portfolio $portfolioId" }
        tradeSaver.saveTrades(portfolio, listOf(values.toIntent(portfolio.currencyUnit)))
    }

    private fun TradeValues.toIntent(portfolioCurrency: CurrencyUnit): TradeIntent {
        val asset = currencyContext[assetCode]
        return TradeIntent(
            targetAsset = asset,
            type = when (kind) {
                TradeKind.BUY -> TradeType.AssetTrade.BUY
                TradeKind.SELL -> TradeType.AssetTrade.SELL
                TradeKind.DEPOSIT -> TradeType.CashMovement.DEPOSIT
                TradeKind.WITHDRAW -> TradeType.CashMovement.WITHDRAW
                TradeKind.TRANSFER_IN -> TradeType.Transfer(true)
                TradeKind.TRANSFER_OUT -> TradeType.Transfer(false)
            },
            date = date,
            quantity = Money(asset, quantity),
            price = price,
            principal = Money(portfolioCurrency, principal),
            fundingSource = when (funding) {
                Funding.PORTFOLIO -> FundingSource.PORTFOLIO
                Funding.EXTERNAL -> FundingSource.EXTERNAL
                Funding.ACCOUNT -> FundingSource.ACCOUNT
            },
            peerAccountId = peerAccountId,
            additionalCosts = costs.map { leg ->
                AppCostLeg(
                    Money(portfolioCurrency, leg.amount),
                    leg.category?.let {
                        CategoryRef(it.id, it.path)
                    }
                )
            },
            comment = comment,
            linkedTransactionId = linkedTransactionId,
            tradeId = tradeId
        )
    }

    override suspend fun createAsset(asset: Asset): Asset? {
        if (currencyContext.getAll().first().any { it.code == asset.code }) return null
        val uri = repository.insertCurrency(
            code = asset.code,
            symbol = asset.code,
            label = asset.label,
            fractionDigits = asset.fractionDigits,
            type = when (asset.kind) {
                AssetKind.SECURITY -> CommodityType.SECURITY
                AssetKind.CRYPTO -> CommodityType.CRYPTO
                AssetKind.COMMODITY -> CommodityType.COMMODITY
            }
        ) ?: return null
        return asset.takeIf { ContentUris.parseId(uri) > 0 }
    }
}

private val CurrencyUnit.info get() = CurrencyInfo(code, symbol, fractionDigits)

private val CurrencyUnit.asset
    get() = Asset(
        code = code,
        label = description,
        fractionDigits = fractionDigits,
        kind = when (commodityType) {
            CommodityType.CRYPTO -> AssetKind.CRYPTO
            CommodityType.COMMODITY -> AssetKind.COMMODITY
            else -> AssetKind.SECURITY
        }
    )

private fun transferKind(isIncoming: Boolean) =
    if (isIncoming) TradeKind.TRANSFER_IN else TradeKind.TRANSFER_OUT
