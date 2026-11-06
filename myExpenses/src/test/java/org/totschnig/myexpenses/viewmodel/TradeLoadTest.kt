package org.totschnig.myexpenses.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.adapter.TradePagingSource
import org.totschnig.myexpenses.db2.createSplitTransaction
import org.totschnig.myexpenses.db2.deleteAccount
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.db2.loadAccount
import org.totschnig.myexpenses.db2.loadTrade
import org.totschnig.myexpenses.db2.loadTrades
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.transactionExists
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.model.sort.SortDirection
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.filter.Criterion
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.PageAccount
import org.totschnig.myexpenses.viewmodel.data.Trade
import org.totschnig.myexpenses.viewmodel.data.TradeType

/**
 * Pins how the legs of a saved trade are read back as a trade, what a page of trades looks like,
 * and what deleting a trade or an account of a portfolio leaves behind.
 */
@RunWith(AndroidJUnit4::class)
class TradeLoadTest : BaseTradeTest() {

    private fun savedTrade() = repository.loadTrade(tradeIds().single())!!

    // Reading back

    @Test
    fun buyIsReadBack() {
        save(tradeIntent(fee = 1000, comment = "Note"))
        val trade = savedTrade()
        assertThat(trade.type).isEqualTo(TradeType.AssetTrade.BUY)
        assertThat(trade.quantity).isEqualTo(Money(aapl, 1000))
        assertThat(trade.principal).isEqualTo(Money(home, 150000))
        assertThat(trade.fee).isEqualTo(Money(home, 1000))
        assertThat(trade.assetSymbol).isEqualTo(AAPL)
        assertThat(trade.comment).isEqualTo("Note")
        assertThat(trade.currency).isEqualTo(HOME)
        assertThat(trade.portfolioId).isEqualTo(portfolio.id)
        assertThat(trade.date.toLocalDate()).isEqualTo(TRADE_DATE)
    }

    @Test
    fun sellIsReadBack() {
        save(tradeIntent(type = TradeType.AssetTrade.SELL, fee = 500))
        val trade = savedTrade()
        assertThat(trade.type).isEqualTo(TradeType.AssetTrade.SELL)
        assertThat(trade.quantity).isEqualTo(Money(aapl, 1000))
        assertThat(trade.principal).isEqualTo(Money(home, 150000))
        assertThat(trade.fee).isEqualTo(Money(home, 500))
    }

    @Test
    fun tradeWithoutCostHasNoFee() {
        save(tradeIntent())
        assertThat(savedTrade().fee).isNull()
        assertThat(savedTrade().additionalCosts).isEmpty()
    }

    @Test
    fun priceIsCalculatedFromThePrincipalNotReadFromThePrices() {
        save(tradeIntent(quantity = 300, price = "33.34", principal = 10000))
        // 100.00 / 3.00 with eight decimals, the 33.34 given when saving is not read back.
        assertThat(savedTrade().price).isEqualTo(BigDecimal("33.33333333"))
    }

    @Test
    fun tradeOfNoQuantityIsReadBackAsSellWithoutPrice() {
        save(tradeIntent(quantity = 0, principal = 10000))
        val trade = savedTrade()
        assertThat(trade.price).isEqualTo(BigDecimal.ZERO)
        // Looks like a bug: the direction comes from the sign of the quantity, a buy of 0 is a sell.
        assertThat(trade.type).isEqualTo(TradeType.AssetTrade.SELL)
    }

    // Peer of the trade

    @Test
    fun portfolioFundedTradeHasTheCashAccountAsPeer() {
        save(tradeIntent())
        val cash = cashAccount()
        assertThat(savedTrade().peerAccount).isEqualTo(cash.id to cash.label)
    }

    @Test
    fun externallyFundedTradeHasNoPeer() {
        save(tradeIntent(fundingSource = FundingSource.EXTERNAL))
        assertThat(savedTrade().peerAccount).isNull()
    }

    @Test
    fun accountFundedTradeHasTheAccountAsPeer() {
        val bank = insertAccount("Bank", currency = HOME)
        save(tradeIntent(fundingSource = FundingSource.ACCOUNT, peerAccountId = bank))
        assertThat(savedTrade().peerAccount).isEqualTo(bank to "Bank")
    }

    // Other types

    @Test
    fun depositIsReadBackWithAPriceOfOne() {
        save(cashMovement(TradeType.CashMovement.DEPOSIT, 500000))
        val trade = savedTrade()
        assertThat(trade.type).isEqualTo(TradeType.CashMovement.DEPOSIT)
        assertThat(trade.quantity).isEqualTo(Money(home, 500000))
        assertThat(trade.principal).isEqualTo(Money(home, 500000))
        assertThat(trade.price).isEqualTo(BigDecimal.ONE)
        assertThat(trade.assetSymbol).isEqualTo(HOME)
        assertThat(trade.peerAccount).isNull()
    }

    @Test
    fun withdrawalFromAccountHasTheAccountAsPeer() {
        val bank = insertAccount("Bank", currency = HOME)
        save(
            cashMovement(
                TradeType.CashMovement.WITHDRAW,
                200000,
                fundingSource = FundingSource.ACCOUNT,
                peerAccountId = bank
            )
        )
        val trade = savedTrade()
        assertThat(trade.type).isEqualTo(TradeType.CashMovement.WITHDRAW)
        assertThat(trade.peerAccount).isEqualTo(bank to "Bank")
    }

    @Test
    fun transferIsReadBackFromBothSides() {
        val target = newPortfolio("Target")
        save(tradeIntent(type = TradeType.Transfer(false), peerAccountId = target.id))
        val outgoing = savedTrade()
        assertThat(outgoing.type).isEqualTo(TradeType.Transfer(false))
        assertThat(outgoing.peerAccount).isEqualTo(target.id to "Target")
        val incoming = repository.loadTrade(tradeIds(target).single())!!
        assertThat(incoming.type).isEqualTo(TradeType.Transfer(true))
        assertThat(incoming.peerAccount).isEqualTo(portfolio.id to "Portfolio")
        assertThat(incoming.quantity).isEqualTo(Money(aapl, 1000))
    }

    // What is not a trade

    @Test
    fun transactionsThatAreNotTradesAreLeftOut() {
        val accountId = insertAccount("Cash", currency = HOME)
        val plain = repository.insertTransaction(accountId, 100).id
        val split = repository.createSplitTransaction(
            Transaction(
                accountId = accountId,
                amount = 100,
                categoryId = SPLIT_CATID,
                uuid = generateUuid()
            ),
            listOf(
                Transaction(accountId = accountId, amount = 100, uuid = generateUuid())
            )
        ).id
        assertThat(repository.loadTrades(listOf(plain, split))).isEmpty()
        assertThat(repository.loadTrades(emptyList())).isEmpty()
        assertThat(repository.loadTrade(4711)).isNull()
    }

    // Sorting

    private fun saveThreeTrades() = save(
        tradeIntent(principal = 20000, date = TRADE_DATE.atTime(12, 0)),
        tradeIntent(principal = 30000, date = TRADE_DATE.minusDays(1).atTime(12, 0)),
        tradeIntent(principal = 10000, date = TRADE_DATE.plusDays(1).atTime(12, 0))
    )

    private fun List<Trade>.principals() = map { it.principal.amountMinor }

    @Test
    fun tradesAreSortedByAmountOrDate() {
        saveThreeTrades()
        val ids = tradeIds()
        assertThat(
            repository.loadTrades(ids, KEY_AMOUNT to SortDirection.ASC).principals()
        ).containsExactly(10000L, 20000L, 30000L).inOrder()
        assertThat(
            repository.loadTrades(ids, KEY_AMOUNT to SortDirection.DESC).principals()
        ).containsExactly(30000L, 20000L, 10000L).inOrder()
        assertThat(
            repository.loadTrades(ids, KEY_DATE to SortDirection.DESC).principals()
        ).containsExactly(10000L, 20000L, 30000L).inOrder()
        assertThat(
            repository.loadTrades(ids, KEY_DATE to SortDirection.ASC).principals()
        ).containsExactly(30000L, 20000L, 10000L).inOrder()
    }

    // Paging

    private fun load(
        sortBy: String = KEY_DATE,
        sortDirection: SortDirection = SortDirection.DESC,
        key: Int? = null,
        loadSize: Int = 2
    ): PagingSource.LoadResult.Page<Int, Trade> = runBlocking {
        val source = TradePagingSource(
            application,
            repository,
            PageAccount(
                id = portfolio.id,
                type = AccountType.INVESTMENT,
                sortBy = sortBy,
                sortDirection = sortDirection,
                currencyUnit = home,
                label = portfolio.label
            ),
            MutableStateFlow<Criterion?>(null),
            2,
            viewModel.viewModelScope
        )
        try {
            source.load(PagingSource.LoadParams.Refresh(key, loadSize, true))
                as PagingSource.LoadResult.Page<Int, Trade>
        } finally {
            source.clear()
        }
    }

    @Test
    fun pagesFollowTheDateOrderAndCountWhatIsLeft() {
        saveThreeTrades()
        val first = load()
        assertThat(first.data.principals()).containsExactly(10000L, 20000L).inOrder()
        assertThat(first.prevKey).isNull()
        assertThat(first.nextKey).isEqualTo(2)
        assertThat(first.itemsBefore).isEqualTo(0)
        assertThat(first.itemsAfter).isEqualTo(1)
        val second = load(key = 2)
        assertThat(second.data.principals()).containsExactly(30000L)
        assertThat(second.prevKey).isEqualTo(0)
        assertThat(second.nextKey).isNull()
        assertThat(second.itemsBefore).isEqualTo(2)
        assertThat(second.itemsAfter).isEqualTo(0)
    }

    @Test
    fun sortingByAmountLoadsEverythingOnOnePage() {
        saveThreeTrades()
        val page = load(sortBy = KEY_AMOUNT, sortDirection = SortDirection.ASC)
        assertThat(page.data.principals()).containsExactly(10000L, 20000L, 30000L).inOrder()
        assertThat(page.prevKey).isNull()
        assertThat(page.nextKey).isNull()
    }

    // Deleting

    @Test
    fun deletingATradeRevertsThePositionAndKeepsThePrice() {
        save(tradeIntent(fee = 1000))
        val tradeId = tradeIds().single()
        val assetLegPeer = parts(tradeId)
            .single { it.transferAccountId == assetAccount().id }.transferPeerId!!
        assertThat(repository.deleteTransaction(tradeId)).isTrue()
        assertThat(repository.transactionExists(assetLegPeer)).isFalse()
        assertThat(position()).isEqualTo(0)
        assertThat(cash()).isEqualTo(0)
        // Sub accounts and the price are not touched.
        assertThat(storedPrice()?.toDouble()).isEqualTo(150.0)
    }

    @Test
    fun deletingAnAccountFundedTradeRevertsThePeerAccount() {
        val bank = insertAccount("Bank", currency = HOME)
        save(tradeIntent(fundingSource = FundingSource.ACCOUNT, peerAccountId = bank))
        repository.deleteTransaction(tradeIds().single())
        assertThat(balance(bank)).isEqualTo(0)
    }

    @Test
    fun deletingATransferThroughTheViewModelDeletesBothSides() {
        val target = newPortfolio("Target")
        save(tradeIntent(type = TradeType.Transfer(false), peerAccountId = target.id))
        viewModel.deleteTransactions(longArrayOf(tradeIds().single()))
        assertThat(tradeIds()).isEmpty()
        assertThat(tradeIds(target)).isEmpty()
        assertThat(position()).isEqualTo(0)
        assertThat(position(target)).isEqualTo(0)
    }

    @Test
    fun deletingOneSideOfATransferInTheRepositoryLeavesTheOtherSide() {
        val target = newPortfolio("Target")
        save(tradeIntent(type = TradeType.Transfer(false), peerAccountId = target.id))
        repository.deleteTransaction(tradeIds().single())
        // Finding the sibling is up to the caller, deleteTransactions of the view model does that.
        assertThat(tradeIds()).isEmpty()
        assertThat(tradeIds(target)).hasSize(1)
        assertThat(position()).isEqualTo(0)
        assertThat(position(target)).isEqualTo(1000)
    }

    @Test
    fun deletingThePortfolioTakesTheSubAccountsAndKeepsThePeerAccountsLeg() {
        val bank = insertAccount("Bank", currency = HOME)
        save(tradeIntent(fundingSource = FundingSource.ACCOUNT, peerAccountId = bank))
        val assetId = assetAccount().id
        val tradeId = tradeIds().single()
        val bankLeg = parts(tradeId).single { it.transferAccountId == bank }.transferPeerId!!
        repository.deleteAccount(portfolio.id)
        assertThat(repository.loadAccount(assetId)).isNull()
        assertThat(repository.transactionExists(tradeId)).isFalse()
        assertThat(balance(bank)).isEqualTo(-150000)
        assertThat(repository.loadTransaction(bankLeg).data.transferAccountId).isNull()
        assertThat(storedPrice()?.toDouble()).isEqualTo(150.0)
    }

    @Test
    fun deletingTheFundingAccountLeavesATradeWithoutPeer() {
        val bank = insertAccount("Bank", currency = HOME)
        save(tradeIntent(fundingSource = FundingSource.ACCOUNT, peerAccountId = bank))
        repository.deleteAccount(bank)
        val trade = savedTrade()
        assertThat(trade.type).isEqualTo(TradeType.AssetTrade.BUY)
        assertThat(trade.peerAccount).isNull()
    }

    @Test
    fun deletingTheAssetAccountTurnsTheBuyIntoAWithdrawal() {
        save(tradeIntent())
        repository.deleteAccount(assetAccount().id)
        // Looks like a bug: without the asset leg only the cash leg decides what the trade is.
        val trade = savedTrade()
        assertThat(trade.type).isEqualTo(TradeType.CashMovement.WITHDRAW)
        assertThat(trade.assetSymbol).isEqualTo(HOME)
        assertThat(trade.principal).isEqualTo(Money(home, 150000))
    }
}
