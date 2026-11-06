package org.totschnig.myexpenses.viewmodel

import android.content.ContentUris
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.db2.loadSubAccounts
import org.totschnig.myexpenses.db2.loadTrade
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.saveCategory
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model2.Category
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_EQUIVALENT_AMOUNT
import org.totschnig.myexpenses.provider.PORTFOLIO_ASSET
import org.totschnig.myexpenses.provider.PORTFOLIO_CASH
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.viewmodel.data.CategoryRef
import org.totschnig.myexpenses.viewmodel.data.CostLeg
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.TradeIntent
import org.totschnig.myexpenses.viewmodel.data.TradeType

/**
 * Pins what saving a trade writes: the legs of the split in the portfolio, the resulting position
 * and cash in the sub accounts, and the price that comes with it.
 */
@RunWith(AndroidJUnit4::class)
class TradeSaveTest : BaseTradeTest() {

    // Buying and selling

    @Test
    fun buyMovesCashIntoTheAssetAccount() {
        save(tradeIntent(fee = 1000))
        val tradeId = tradeIds().single()
        val parent = parent(tradeId)
        assertThat(parent.categoryId).isEqualTo(SPLIT_CATID)
        assertThat(parent.amount).isEqualTo(0)
        val parts = parts(tradeId)
        assertThat(parts.map { it.amount }).containsExactly(-150000L, 151000L, -1000L)
        assertThat(parts.single { it.amount == -150000L }.transferAccountId)
            .isEqualTo(assetAccount().id)
        assertThat(parts.single { it.amount == 151000L }.transferAccountId)
            .isEqualTo(cashAccount().id)
        assertThat(parts.single { it.amount == -1000L }.transferAccountId).isNull()
        assertThat(position()).isEqualTo(1000)
        assertThat(cash()).isEqualTo(-151000)
        assertThat(balance(portfolio.id)).isEqualTo(0)
    }

    @Test
    fun sellMovesTheAssetBackIntoCashMinusTheFee() {
        save(tradeIntent())
        save(
            tradeIntent(
                type = TradeType.AssetTrade.SELL,
                quantity = 400,
                principal = 80000,
                fee = 500
            )
        )
        val sellId = tradeIds().first { id -> parts(id).any { it.amount == 80000L } }
        assertThat(parent(sellId).amount).isEqualTo(0)
        assertThat(parts(sellId).map { it.amount }).containsExactly(80000L, -79500L, -500L)
        assertThat(position()).isEqualTo(600)
        assertThat(cash()).isEqualTo(-150000 + 79500)
    }

    @Test
    fun sellingMoreThanIsHeldGoesNegative() {
        // The limit is only shown in TradeScreen, nothing below it enforces it.
        save(tradeIntent(type = TradeType.AssetTrade.SELL, quantity = 500, principal = 75000))
        assertThat(position()).isEqualTo(-500)
        assertThat(cash()).isEqualTo(75000)
    }

    @Test
    fun cashMayGoNegativeWhenBuying() {
        save(cashMovement(TradeType.CashMovement.DEPOSIT, 1000))
        save(tradeIntent())
        assertThat(cash()).isEqualTo(1000 - 150000)
    }

    // Sub accounts

    @Test
    fun subAccountsAreCreatedWithTheirRoles() {
        save(tradeIntent())
        val asset = assetAccount()
        assertThat(asset.label).isEqualTo("Apple Inc.")
        assertThat(asset.currency).isEqualTo(AAPL)
        assertThat(asset.parentId).isEqualTo(portfolio.id)
        assertThat(asset.dynamicExchangeRates).isTrue()
        assertThat(asset.type?.name).isEqualTo(AccountType.INVESTMENT.name)
        val cash = cashAccount()
        assertThat(cash.currency).isEqualTo(HOME)
        assertThat(cash.parentId).isEqualTo(portfolio.id)
        assertThat(cash.dynamicExchangeRates).isFalse()
        assertThat(cash.type?.name).isEqualTo(AccountType.CASH.name)
    }

    @Test
    fun subAccountsAreReusedAcrossSavesAndOnePerAsset() {
        insertAsset("MSFT")
        save(tradeIntent(), tradeIntent(asset = currencyContext["MSFT"]))
        save(tradeIntent(quantity = 200, principal = 30000))
        assertThat(subAccountsWithRole(PORTFOLIO_ASSET).map { it.currency })
            .containsExactly(AAPL, "MSFT")
        assertThat(subAccountsWithRole(PORTFOLIO_CASH)).hasSize(1)
        assertThat(position()).isEqualTo(1200)
        assertThat(position(code = "MSFT")).isEqualTo(1000)
        assertThat(cash()).isEqualTo(-330000)
    }

    @Test
    fun portfoliosDoNotShareSubAccounts() {
        val other = newPortfolio("Other")
        save(tradeIntent())
        save(tradeIntent(account = other), account = other)
        assertThat(assetAccount().id).isNotEqualTo(assetAccount(other).id)
        assertThat(position()).isEqualTo(1000)
        assertThat(position(other)).isEqualTo(1000)
    }

    // Funding

    @Test
    fun externalFundingTouchesNoCashAccount() {
        save(tradeIntent(fundingSource = FundingSource.EXTERNAL, fee = 1000))
        val parts = parts(tradeIds().single())
        assertThat(parts.map { it.amount }).containsExactly(-150000L, 151000L, -1000L)
        assertThat(parts.single { it.amount == 151000L }.transferAccountId).isNull()
        assertThat(subAccountsWithRole(PORTFOLIO_CASH)).isEmpty()
        assertThat(position()).isEqualTo(1000)
    }

    @Test
    fun accountFundingMovesThePeerAccount() {
        val bank = insertAccount("Bank", currency = HOME)
        save(tradeIntent(fundingSource = FundingSource.ACCOUNT, peerAccountId = bank, fee = 1000))
        assertThat(balance(bank)).isEqualTo(-151000)
        assertThat(subAccountsWithRole(PORTFOLIO_CASH)).isEmpty()
        val fundingPart = parts(tradeIds().single()).single { it.amount == 151000L }
        assertThat(fundingPart.transferAccountId).isEqualTo(bank)
    }

    @Test
    fun linkedTransactionBecomesTheFundingLeg() {
        val bank = insertAccount("Bank", currency = HOME)
        val existing = repository.insertTransaction(bank, -150000).id
        save(
            tradeIntent(
                fundingSource = FundingSource.ACCOUNT,
                peerAccountId = bank,
                linkedTransactionId = existing
            )
        )
        assertThat(balance(bank)).isEqualTo(-150000)
        val fundingPart = parts(tradeIds().single()).single { it.amount == 150000L }
        assertThat(fundingPart.transferAccountId).isEqualTo(bank)
        assertThat(fundingPart.transferPeerId).isEqualTo(existing)
        assertThat(repository.loadTransaction(existing).data.transferAccountId)
            .isEqualTo(portfolio.id)
    }

    @Test
    fun linkedTransactionThatIsGoneLeavesTheFundingLegUnlinked() {
        val bank = insertAccount("Bank", currency = HOME)
        save(
            tradeIntent(
                fundingSource = FundingSource.ACCOUNT,
                peerAccountId = bank,
                linkedTransactionId = 4711
            )
        )
        val fundingPart = parts(tradeIds().single()).single { it.amount == 150000L }
        assertThat(fundingPart.transferAccountId).isNull()
        assertThat(balance(bank)).isEqualTo(0)
    }

    // Deposit and withdrawal

    @Test
    fun depositAddsTheQuantityToCash() {
        save(cashMovement(TradeType.CashMovement.DEPOSIT, 500000, fee = 1500))
        val tradeId = tradeIds().single()
        assertThat(parent(tradeId).amount).isEqualTo(0)
        assertThat(parts(tradeId).map { it.amount }).containsExactly(-500000L, 501500L, -1500L)
        assertThat(cash()).isEqualTo(500000)
        assertThat(subAccountsWithRole(PORTFOLIO_ASSET)).isEmpty()
    }

    @Test
    fun withdrawalTakesTheQuantityFromCash() {
        save(cashMovement(TradeType.CashMovement.WITHDRAW, 200000, fee = 2000))
        val tradeId = tradeIds().single()
        assertThat(parent(tradeId).amount).isEqualTo(0)
        assertThat(parts(tradeId).map { it.amount }).containsExactly(200000L, -198000L, -2000L)
        assertThat(cash()).isEqualTo(-200000)
    }

    @Test
    fun depositFromAccountMovesThePeerAccount() {
        val bank = insertAccount("Bank", currency = HOME)
        save(
            cashMovement(
                TradeType.CashMovement.DEPOSIT,
                500000,
                fundingSource = FundingSource.ACCOUNT,
                peerAccountId = bank
            )
        )
        assertThat(cash()).isEqualTo(500000)
        assertThat(balance(bank)).isEqualTo(-500000)
    }

    @Test
    fun cashMovementCanNotBeFundedFromThePortfolio() {
        assertThrows(IllegalArgumentException::class.java) {
            cashMovement(
                TradeType.CashMovement.DEPOSIT,
                100,
                fundingSource = FundingSource.PORTFOLIO
            )
        }
    }

    @Test
    fun cashMovementStoresAPriceOfTheCurrencyInItself() {
        save(cashMovement(TradeType.CashMovement.DEPOSIT, 100))
        // Looks like a bug: the price of 1 passes the check for a positive price, so that a row
        // with EUR as commodity and as base is stored.
        assertThat(storedPrice(commodity = home)?.toDouble()).isEqualTo(1.0)
    }

    // Costs

    @Test
    fun feeIsTheSumOfTheCostLegs() {
        val intent = tradeIntent().copy(
            additionalCosts = listOf(CostLeg(Money(home, 100)), CostLeg(Money(home, 250)))
        )
        assertThat(intent.fee).isEqualTo(Money(home, 350))
    }

    @Test
    fun feeConstructorDropsAFeeOfZero() {
        val intent = TradeIntent(
            targetAsset = aapl,
            type = TradeType.AssetTrade.BUY,
            date = TRADE_DATE.atStartOfDay(),
            quantity = Money(aapl, 1),
            price = BigDecimal.ONE,
            principal = Money(home, 1),
            peerAccountId = null,
            fee = Money(home, 0)
        )
        assertThat(intent.additionalCosts).isEmpty()
    }

    @Test
    fun costLegOfZeroIsNotSavedButFundingUsesTheRest() {
        save(
            tradeIntent().copy(
                additionalCosts = listOf(CostLeg(Money(home, 0)), CostLeg(Money(home, 300)))
            )
        )
        assertThat(parts(tradeIds().single()).map { it.amount })
            .containsExactly(-150000L, 150300L, -300L)
    }

    @Test
    fun categoriesOfTheLastTradeAreRemembered() {
        val fees = repository.saveCategory(Category(label = "Fees"))!!
        save(
            tradeIntent().copy(
                additionalCosts = listOf(
                    CostLeg(Money(home, 100), CategoryRef(fees, "Fees")),
                    CostLeg(Money(home, 200))
                )
            )
        )
        assertThat(prefHandler.getString(PrefKey.LAST_TRADE_FEE_CATEGORIES)).isEqualTo("$fees")
        val partCategory = parts(tradeIds().single()).single { it.amount == -100L }.categoryId
        assertThat(partCategory).isEqualTo(fees)

        save(tradeIntent())
        assertThat(prefHandler.getString(PrefKey.LAST_TRADE_FEE_CATEGORIES)).isEmpty()
    }

    // Price that comes with a trade

    @Test
    fun tradeStoresThePriceAsUserPriceOfTheDay() {
        save(tradeIntent(price = "150.25"))
        assertThat(storedPrice()?.toDouble()).isEqualTo(150.25)
    }

    @Test
    fun secondTradeOfTheDayReplacesThePrice() {
        save(tradeIntent(price = "150"))
        save(tradeIntent(price = "155"))
        assertThat(storedPrice()?.toDouble()).isEqualTo(155.0)
    }

    @Test
    fun tradeWithoutAPriceStoresNone() {
        save(tradeIntent(price = "0"))
        assertThat(storedPrice()).isNull()
        assertThat(position()).isEqualTo(1000)
    }

    @Test
    fun priceIsStoredAgainstThePortfolioCurrency() {
        val foreign = newPortfolio("Foreign", FOREIGN)
        save(tradeIntent(account = foreign), account = foreign)
        assertThat(storedPrice(base = currencyContext[FOREIGN])?.toDouble()).isEqualTo(150.0)
        assertThat(storedPrice(base = home)).isNull()
    }

    @Test
    fun priceGivesTheAssetLegItsEquivalentAmount() {
        save(tradeIntent(price = "160"))
        assertThat(equivalentAmountOfAssetLeg(portfolio)).isEqualTo(160000)
    }

    @Test
    fun priceAgainstAForeignPortfolioGivesNoEquivalentAmount() {
        val foreign = newPortfolio("Foreign", FOREIGN)
        save(tradeIntent(account = foreign), account = foreign)
        // Only a price against the home currency is used, the leg stays without equivalent amount.
        assertThat(equivalentAmountOfAssetLeg(foreign)).isNull()
    }

    private fun equivalentAmountOfAssetLeg(account: FullAccount): Long? {
        val leg = parts(tradeIds(account).single())
            .single { it.transferAccountId == assetAccount(account).id }
        return contentResolver.query(
            ContentUris.withAppendedId(TransactionProvider.TRANSACTIONS_URI, leg.transferPeerId!!),
            arrayOf(KEY_EQUIVALENT_AMOUNT),
            null,
            null,
            null
        )!!.use {
            it.moveToFirst()
            if (it.isNull(0)) null else it.getLong(0)
        }
    }

    // Saving several

    @Test
    fun saveTradesReportsProgressPerIntent() {
        val progress = mutableListOf<Int>()
        runBlocking {
            viewModel.saveTrades(portfolio, listOf(tradeIntent(), tradeIntent())) {
                progress.add(it)
            }
        }
        assertThat(progress).containsExactly(1, 2).inOrder()
        assertThat(tradeIds()).hasSize(2)
    }

    // Editing

    @Test
    fun editKeepsTheParentAndReplacesTheLegs() {
        save(tradeIntent(fee = 1000, comment = "first"))
        val tradeId = tradeIds().single()
        save(
            tradeIntent(
                quantity = 2000,
                price = "200",
                principal = 400000,
                comment = "second",
                tradeId = tradeId
            )
        )
        assertThat(tradeIds()).containsExactly(tradeId)
        assertThat(parent(tradeId).comment).isEqualTo("second")
        assertThat(parts(tradeId).map { it.amount }).containsExactly(-400000L, 400000L)
        assertThat(position()).isEqualTo(2000)
        assertThat(cash()).isEqualTo(-400000)
        assertThat(subAccountsWithRole(PORTFOLIO_ASSET)).hasSize(1)
        assertThat(subAccountsWithRole(PORTFOLIO_CASH)).hasSize(1)
    }

    @Test
    fun editChangingTheFundingRestoresTheCash() {
        save(tradeIntent())
        val tradeId = tradeIds().single()
        save(tradeIntent(fundingSource = FundingSource.EXTERNAL, tradeId = tradeId))
        assertThat(cash()).isEqualTo(0)
        assertThat(position()).isEqualTo(1000)
    }

    @Test
    fun editChangingTheAssetMovesThePosition() {
        insertAsset("MSFT")
        save(tradeIntent())
        val tradeId = tradeIds().single()
        save(tradeIntent(asset = currencyContext["MSFT"], tradeId = tradeId))
        assertThat(position()).isEqualTo(0)
        assertThat(position(code = "MSFT")).isEqualTo(1000)
    }

    @Test
    fun editChangingTheDateLeavesThePriceOfTheOldDate() {
        save(tradeIntent(price = "150"))
        val tradeId = tradeIds().single()
        val newDate = TRADE_DATE.plusDays(1)
        save(tradeIntent(price = "160", date = newDate.atTime(12, 0), tradeId = tradeId))
        assertThat(storedPrice(newDate)?.toDouble()).isEqualTo(160.0)
        // Looks like a bug: the price that came with the old version of the trade stays behind.
        assertThat(storedPrice(TRADE_DATE)?.toDouble()).isEqualTo(150.0)
    }

    @Test
    fun tradeToEditFollowsLoadAndSave() {
        save(tradeIntent())
        val tradeId = tradeIds().single()
        viewModel.loadTrade(tradeId)
        assertThat(viewModel.tradeToEdit.value).isEqualTo(repository.loadTrade(tradeId))
        viewModel.saveTrade(portfolio, tradeIntent(tradeId = tradeId))
        assertThat(viewModel.tradeToEdit.value).isNull()
    }

    // Transfer between portfolios

    @Test
    fun transferMovesThePositionAndNoCash() {
        val target = newPortfolio("Target")
        save(tradeIntent())
        save(
            tradeIntent(
                type = TradeType.Transfer(false),
                quantity = 400,
                principal = 60000,
                peerAccountId = target.id
            )
        )
        assertThat(position()).isEqualTo(600)
        assertThat(position(target)).isEqualTo(400)
        assertThat(cash()).isEqualTo(-150000)
        assertThat(subAccountsWithRole(PORTFOLIO_CASH, target)).isEmpty()
    }

    @Test
    fun transferWithoutPeerIsDroppedSilently() {
        save(tradeIntent(type = TradeType.Transfer(false)))
        assertThat(tradeIds()).isEmpty()
        assertThat(repository.loadSubAccounts(portfolio.id)).isEmpty()
        assertThat(storedPrice()).isNull()
    }

    @Test
    fun transferDropsItsCosts() {
        val target = newPortfolio("Target")
        save(tradeIntent(type = TradeType.Transfer(false), fee = 1000, peerAccountId = target.id))
        val tradeId = tradeIds().single()
        assertThat(parts(tradeId).map { it.amount }).containsExactly(150000L, -150000L)
        assertThat(parent(tradeId).amount).isEqualTo(0)
    }

    @Test
    fun transferStoresThePriceOnlyForTheCurrentPortfolio() {
        val target = newPortfolio("Target", FOREIGN)
        save(tradeIntent(type = TradeType.Transfer(false), peerAccountId = target.id))
        assertThat(storedPrice()?.toDouble()).isEqualTo(150.0)
        assertThat(storedPrice(base = currencyContext[FOREIGN])).isNull()
    }

    @Test
    fun transferToAPortfolioInAnotherCurrencyDoesNotConvertTheValuation() {
        val target = newPortfolio("Target", FOREIGN)
        save(tradeIntent(type = TradeType.Transfer(false), peerAccountId = target.id))
        // Looks like a bug: 1500 EUR arrive as 1500 USD, no exchange rate is applied.
        val partsInTarget = parts(tradeIds(target).single()).map { it.amount }
        assertThat(partsInTarget).containsExactly(150000L, -150000L)
    }
}
