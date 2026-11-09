package org.totschnig.myexpenses.viewmodel

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.db2.findAccountType
import org.totschnig.myexpenses.db2.insertCurrency
import org.totschnig.myexpenses.db2.loadPrice
import org.totschnig.myexpenses.db2.loadSplitParts
import org.totschnig.myexpenses.db2.loadSubAccounts
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.loadTransactions
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.isOnboardingCompleted
import org.totschnig.myexpenses.provider.KEY_CURRENT_BALANCE
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.PORTFOLIO_ASSET
import org.totschnig.myexpenses.provider.PORTFOLIO_CASH
import org.totschnig.myexpenses.provider.PORTFOLIO_CONTAINER
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.viewmodel.data.CostLeg
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.TradeIntent
import org.totschnig.myexpenses.viewmodel.data.TradeType

/**
 * Fixtures for the tests that pin what the trade logic does today. A portfolio is a container
 * account with sub accounts: one per asset, holding the quantity, and at most one for cash.
 * Comments starting with "Looks like a bug" mark behaviour that is pinned, not endorsed.
 */
abstract class BaseTradeTest : BaseViewModelTest() {

    protected lateinit var viewModel: MainViewModel
    protected lateinit var portfolio: FullAccount

    protected val home by lazy { currencyContext[HOME] }
    protected val aapl by lazy { currencyContext[AAPL] }

    @Before
    fun setUpTradeTest() {
        prefHandler.putString(PrefKey.HOME_CURRENCY, HOME)
        prefHandler.isOnboardingCompleted = true
        viewModel = MainViewModel(application, SavedStateHandle()).also {
            application.appComponent.inject(it)
        }
        insertAsset(AAPL, "Apple Inc.")
        portfolio = newPortfolio("Portfolio")
    }

    @After
    fun tearDownTradeTest() {
        viewModel.viewModelScope.cancel()
    }

    protected fun insertAsset(
        code: String,
        label: String = code,
        fractionDigits: Int = 2,
        type: CommodityType = CommodityType.SECURITY
    ) = runBlocking {
        repository.insertCurrency(code, code, label, fractionDigits, type)
    }

    protected fun newPortfolio(label: String, currency: String = HOME): FullAccount {
        val account = Account(
            label = label,
            currency = currency,
            type = repository.findAccountType(AccountType.INVESTMENT.name)!!,
            portfolioRole = PORTFOLIO_CONTAINER
        ).createIn(repository)
        return FullAccount(
            id = account.id,
            label = label,
            currencyUnit = currencyContext[currency],
            type = AccountType.INVESTMENT,
            portfolioRole = PORTFOLIO_CONTAINER
        )
    }

    protected fun tradeIntent(
        type: TradeType = TradeType.AssetTrade.BUY,
        quantity: Long = 1000,
        price: String = "150",
        principal: Long = 150000,
        fee: Long = 0,
        asset: CurrencyUnit = aapl,
        account: FullAccount = portfolio,
        fundingSource: FundingSource = FundingSource.PORTFOLIO,
        peerAccountId: Long? = null,
        date: LocalDateTime = TRADE_DATE.atTime(12, 0),
        comment: String = "",
        linkedTransactionId: Long? = null,
        tradeId: Long? = null
    ) = TradeIntent(
        targetAsset = asset,
        type = type,
        date = date,
        quantity = Money(asset, quantity),
        price = BigDecimal(price),
        principal = Money(account.currencyUnit, principal),
        fundingSource = fundingSource,
        peerAccountId = peerAccountId,
        additionalCosts = if (fee == 0L) {
            emptyList()
        } else {
            listOf(CostLeg(Money(account.currencyUnit, fee)))
        },
        comment = comment,
        linkedTransactionId = linkedTransactionId,
        tradeId = tradeId
    )

    protected fun cashMovement(
        type: TradeType.CashMovement,
        amount: Long,
        fee: Long = 0,
        fundingSource: FundingSource = FundingSource.EXTERNAL,
        peerAccountId: Long? = null
    ) = tradeIntent(
        type = type,
        quantity = amount,
        price = "1",
        principal = amount,
        fee = fee,
        asset = home,
        fundingSource = fundingSource,
        peerAccountId = peerAccountId
    )

    protected fun save(vararg intents: TradeIntent, account: FullAccount = portfolio) {
        runBlocking { viewModel.saveTrades(account, intents.toList()) }
    }

    /** The ids of the trades in the portfolio, sorted the way the list shows them. */
    protected fun tradeIds(account: FullAccount = portfolio): List<Long> = runBlocking {
        repository.loadTransactions(account.id).map { it.id }
    }

    protected fun parts(tradeId: Long): List<Transaction> = repository.loadSplitParts(tradeId)

    protected fun parent(tradeId: Long) = repository.loadTransaction(tradeId).data

    protected fun subAccountsWithRole(role: Int, account: FullAccount = portfolio) =
        repository.loadSubAccounts(account.id).filter { it.portfolioRole == role }

    protected fun assetAccount(account: FullAccount = portfolio, code: String = AAPL) =
        subAccountsWithRole(PORTFOLIO_ASSET, account).single { it.currency == code }

    protected fun cashAccount(account: FullAccount = portfolio) =
        subAccountsWithRole(PORTFOLIO_CASH, account).single()

    protected fun position(account: FullAccount = portfolio, code: String = AAPL) =
        balance(assetAccount(account, code).id)

    protected fun cash(account: FullAccount = portfolio) = balance(cashAccount(account).id)

    /** The balance the way the account list reads it. */
    protected fun balance(accountId: Long): Long = contentResolver.query(
        TransactionProvider.ACCOUNTS_FULL_URI,
        null,
        "$KEY_ROWID = ?",
        arrayOf(accountId.toString()),
        null
    )!!.use {
        check(it.moveToFirst()) { "Account $accountId not found" }
        it.getLong(it.getColumnIndexOrThrow(KEY_CURRENT_BALANCE))
    }

    protected fun storedPrice(
        date: LocalDate = TRADE_DATE,
        base: CurrencyUnit = home,
        commodity: CurrencyUnit = aapl,
        source: ExchangeRateSource? = ExchangeRateSource.User
    ) = repository.loadPrice(base, commodity, date, source)

    /** The flows of the view model hop back to the main thread, so the looper must run. */
    protected fun <T> Flow<T>.awaitMatching(predicate: (T) -> Boolean): T {
        val seen = mutableListOf<T>()
        val job = viewModel.viewModelScope.launch { collect { seen.add(it) } }
        try {
            val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
            while (seen.none(predicate) && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                if (seen.none(predicate)) Thread.sleep(10)
            }
        } finally {
            job.cancel()
        }
        return seen.lastOrNull(predicate) ?: seen.last()
    }

    companion object {
        const val HOME = "EUR"
        const val FOREIGN = "USD"
        const val AAPL = "AAPL"
        val TRADE_DATE: LocalDate = LocalDate.of(2025, 1, 10)
        private const val TIMEOUT_MILLIS = 10_000L
    }
}
