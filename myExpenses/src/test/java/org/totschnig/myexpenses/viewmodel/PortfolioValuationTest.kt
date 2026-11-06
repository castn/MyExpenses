package org.totschnig.myexpenses.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.math.RoundingMode
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.findAccountType
import org.totschnig.myexpenses.db2.loadAccount
import org.totschnig.myexpenses.db2.savePrice
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.provider.KEY_COMMODITY
import org.totschnig.myexpenses.provider.PORTFOLIO_ASSET
import org.totschnig.myexpenses.provider.PORTFOLIO_CASH
import org.totschnig.myexpenses.provider.PORTFOLIO_CONTAINER
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.TradeType

/**
 * Pins how the value of a portfolio comes about: quantity of each sub account times the latest
 * price against the currency of the portfolio. Cost basis and gains are not calculated anywhere.
 */
@RunWith(AndroidJUnit4::class)
class PortfolioValuationTest : BaseTradeTest() {

    private val eur = CurrencyUnit("EUR", "€", 2)
    private val usd = CurrencyUnit("USD", "$", 2)
    private val share = CurrencyUnit("SHARE", "SHARE", 2)
    private val metal = CurrencyUnit("XAU", "XAU", 8)
    private val today = LocalDate.now()

    private fun account(
        currency: CurrencyUnit,
        balance: Long = 0,
        role: Int = PORTFOLIO_ASSET,
        dynamic: Boolean = true,
        initialExchangeRate: Double? = null,
        children: List<FullAccount> = emptyList()
    ) = FullAccount(
        id = 1,
        label = currency.code,
        currencyUnit = currency,
        type = AccountType.INVESTMENT,
        currentBalance = balance,
        dynamic = dynamic,
        initialExchangeRate = initialExchangeRate,
        portfolioRole = role,
        children = children
    )

    private fun portfolioOf(
        vararg children: FullAccount,
        currency: CurrencyUnit = eur,
        dynamic: Boolean = false,
        initialExchangeRate: Double? = null
    ) = account(
        currency,
        role = PORTFOLIO_CONTAINER,
        dynamic = dynamic,
        initialExchangeRate = initialExchangeRate,
        children = children.toList()
    )

    // enrich

    @Test
    fun childrenAreValuedAtTheirPriceAgainstThePortfolioCurrency() {
        val result = portfolioOf(
            account(share, 1000),
            account(eur, -5000, role = PORTFOLIO_CASH, dynamic = false)
        ).enrich(mapOf(("SHARE" to "EUR") to (today to 160.0)), eur)
        assertThat(result.children.map { it.equivalentCurrentBalance })
            .containsExactly(160000L, -5000L)
        assertThat(result.childrenValuation).isEqualTo(155000)
        assertThat(result.effectiveBalance).isEqualTo(155000)
        assertThat(result.equivalentChildrenValuation).isEqualTo(155000)
    }

    @Test
    fun priceIsInMinorUnitsSoTheFractionDigitsOfTheAssetDoNotMatter() {
        val result = portfolioOf(account(metal, 250000000))
            .enrich(mapOf(("XAU" to "EUR") to (today to 0.00002)), eur)
        assertThat(result.childrenValuation).isEqualTo(5000)
    }

    @Test
    fun priceAgainstAnotherCurrencyThanThePortfolioIsNotUsed() {
        val result = portfolioOf(account(share, 1000))
            .enrich(mapOf(("SHARE" to "USD") to (today to 160.0)), eur)
        // Looks like a bug: without a price the quantity counts one to one as money, so that 10
        // shares are valued at 10 EUR.
        assertThat(result.childrenValuation).isEqualTo(1000)
    }

    @Test
    fun staticChildIsValuedAtItsInitialRate() {
        val result = portfolioOf(account(share, 1000, dynamic = false, initialExchangeRate = 2.0))
            .enrich(mapOf(("SHARE" to "EUR") to (today to 160.0)), eur)
        assertThat(result.childrenValuation).isEqualTo(2000)
    }

    @Test
    fun portfolioInForeignCurrencyIsConvertedOnceMore() {
        val children = arrayOf(account(share, 1000), account(usd, 500, dynamic = false))
        val prices = mapOf(
            ("SHARE" to "USD") to (today to 100.0),
            ("USD" to "EUR") to (today to 0.5)
        )
        val dynamic = portfolioOf(*children, currency = usd, dynamic = true).enrich(prices, eur)
        assertThat(dynamic.childrenValuation).isEqualTo(100500)
        assertThat(dynamic.equivalentChildrenValuation).isEqualTo(50250)
        val static = portfolioOf(*children, currency = usd, initialExchangeRate = 0.25)
            .enrich(prices, eur)
        assertThat(static.equivalentChildrenValuation).isEqualTo(25125)
    }

    @Test
    fun plainAccountHasNoChildrenValuation() {
        val result = account(share, 1000, role = 0).enrich(emptyMap(), eur)
        assertThat(result.childrenValuation).isEqualTo(0)
        assertThat(result.effectiveBalance).isEqualTo(1000)
    }

    // From the database

    private fun loadedPortfolio(): FullAccount = viewModel.accountDataV2.awaitMatching { result ->
        result?.getOrNull()?.single { it.id == portfolio.id }?.children?.isNotEmpty() == true
    }!!.getOrThrow().single { it.id == portfolio.id }

    @Test
    fun accountListNestsAndValuesAPortfolio() {
        save(tradeIntent(fee = 1000))
        repository.savePrice(HOME, AAPL, LocalDate.now(), ExchangeRateSource.User, 160.0)
        val loaded = loadedPortfolio()
        assertThat(loaded.isPortfolio).isTrue()
        assertThat(loaded.children.map { it.portfolioRole })
            .containsExactly(PORTFOLIO_ASSET, PORTFOLIO_CASH)
        assertThat(loaded.children.single { it.isPortfolioAsset }.equivalentCurrentBalance)
            .isEqualTo(160000)
        assertThat(loaded.children.single { it.isPortfolioCash }.equivalentCurrentBalance)
            .isEqualTo(-151000)
        assertThat(loaded.currentBalance).isEqualTo(0)
        assertThat(loaded.childrenValuation).isEqualTo(9000)
    }

    @Test
    fun priceOfTheTradeItselfValuesThePosition() {
        save(tradeIntent(fundingSource = FundingSource.EXTERNAL))
        assertThat(loadedPortfolio().childrenValuation).isEqualTo(150000)
    }

    @Test
    fun priceInTheFutureIsNotUsed() {
        save(tradeIntent(fundingSource = FundingSource.EXTERNAL))
        val tomorrow = LocalDate.now().plusDays(1)
        repository.savePrice(HOME, AAPL, tomorrow, ExchangeRateSource.User, 999.0)
        assertThat(loadedPortfolio().childrenValuation).isEqualTo(150000)
    }

    @Test
    fun sellingEverythingLeavesTheCash() {
        save(tradeIntent(), tradeIntent(type = TradeType.AssetTrade.SELL, principal = 200000))
        assertThat(loadedPortfolio().childrenValuation).isEqualTo(50000)
    }

    // Which currencies are updated

    @Test
    fun assetIsDownloadedAgainstThePortfolioCurrency() {
        val foreign = newPortfolio("Foreign", FOREIGN)
        save(tradeIntent(account = foreign), account = foreign)
        val pairs = contentResolver.query(
            TransactionProvider.DYNAMIC_CURRENCIES_URI,
            null,
            null,
            null,
            null
        )!!.useAndMapToList {
            it.getString(it.getColumnIndexOrThrow(KEY_COMMODITY)) to
                it.getString(it.getColumnIndexOrThrow("base"))
        }
        assertThat(pairs).containsExactly(AAPL to FOREIGN)
    }

    // Creating and updating

    private val investmentType by lazy {
        repository.findAccountType(AccountType.INVESTMENT.name)!!
    }

    private fun createdPortfolio(
        currency: String,
        dynamic: Boolean = false,
        rate: Double = 2.0
    ): Long {
        viewModel.createPortfolio("Created", currency, 1, rate, dynamic, investmentType)
        return viewModel.selectedAccountId.value
    }

    @Test
    fun createdPortfolioIsSelectedAndAContainer() {
        val id = createdPortfolio(HOME)
        val account = repository.loadAccount(id)!!
        assertThat(account.label).isEqualTo("Created")
        assertThat(account.portfolioRole).isEqualTo(PORTFOLIO_CONTAINER)
        assertThat(account.exchangeRate).isEqualTo(1.0)
    }

    @Test
    fun createdPortfolioInForeignCurrencyStoresTheRate() {
        val id = createdPortfolio(FOREIGN, rate = 0.5)
        assertThat(repository.loadAccount(id)!!.exchangeRate).isEqualTo(0.5)
    }

    @Test
    fun updatingAPortfolioStoresARateUnlessItIsDynamic() {
        val id = createdPortfolio(FOREIGN, rate = 0.5)
        viewModel.updatePortfolio(id, "New", FOREIGN, 2, 0.75, true, investmentType)
        val dynamic = repository.loadAccount(id)!!
        assertThat(dynamic.label).isEqualTo("New")
        assertThat(dynamic.dynamicExchangeRates).isTrue()
        assertThat(dynamic.exchangeRate).isEqualTo(0.5)
        viewModel.updatePortfolio(id, "New", FOREIGN, 2, 0.75, false, investmentType)
        assertThat(repository.loadAccount(id)!!.exchangeRate).isEqualTo(0.75)
    }

    // Rounding of imported and entered amounts

    @Test
    fun roundingModeIsHalfUpAndKeptPerAccount() {
        assertThat(viewModel.getRoundingMode(portfolio.id).awaitMatching { true })
            .isEqualTo(RoundingMode.HALF_UP)
        viewModel.setRoundingMode(portfolio.id, RoundingMode.DOWN)
        assertThat(
            viewModel.getRoundingMode(portfolio.id).awaitMatching { it == RoundingMode.DOWN }
        ).isEqualTo(RoundingMode.DOWN)
        assertThat(viewModel.getRoundingMode(portfolio.id + 1).awaitMatching { true })
            .isEqualTo(RoundingMode.HALF_UP)
    }

    // Lists of accounts

    @Test
    fun minimalListHidesPortfoliosAndSubAccountsUnlessAsked() {
        save(tradeIntent())
        val plain = insertAccount("Plain", currency = HOME)
        val default = viewModel.accountsMinimal(withAggregates = false)
            .awaitMatching { it.isNotEmpty() }
        assertThat(default.map { it.id }).containsExactly(plain)
        val withPortfolios = viewModel
            .accountsMinimal(withAggregates = false, withPortfolios = true)
            .awaitMatching { it.isNotEmpty() }
        assertThat(withPortfolios.map { it.id }).containsExactly(plain, portfolio.id)
    }
}
