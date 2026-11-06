package org.totschnig.myexpenses.db2

import android.content.ContentUris
import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.model.CurrencyEnum
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.isOnboardingCompleted
import org.totschnig.myexpenses.provider.KEY_COMMODITY
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_VALUE
import org.totschnig.myexpenses.provider.PORTFOLIO_ASSET
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.retrofit.ExchangeRateSource

/**
 * Pins the types of commodities and what changing or deleting a currency does to prices and to the
 * sub accounts of portfolios. Comments starting with "Looks like a bug" mark behaviour that is
 * pinned, not endorsed.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryCommodityTest : BaseTestWithRepository() {

    private var assetId = 0L

    @Before
    fun setUp() {
        prefHandler.putString(PrefKey.HOME_CURRENCY, EUR)
        prefHandler.isOnboardingCompleted = true
        assetId = runBlocking {
            ContentUris.parseId(
                repository.insertCurrency(ASSET, ASSET, "Apple", 2, CommodityType.SECURITY)!!
            )
        }
    }

    private fun savePrice(value: Double, commodity: String = ASSET, base: String = EUR) =
        repository.savePrice(base, commodity, DATE, ExchangeRateSource.User, value)

    private fun rawPrices(
        selection: String? = null,
        vararg args: String
    ): List<Triple<String, String, Double>> = contentResolver.query(
        TransactionProvider.PRICES_URI,
        arrayOf(KEY_CURRENCY, KEY_COMMODITY, KEY_VALUE),
        selection,
        args.takeIf { it.isNotEmpty() }?.let { arrayOf(*it) },
        null
    )!!.useAndMapToList { Triple(it.getString(0), it.getString(1), it.getDouble(2)) }

    private fun assetAccount(currency: String = ASSET, label: String = "Asset account"): Long =
        repository.createAccount(
            Account(
                label = label,
                currency = currency,
                type = repository.findAccountType(AccountType.INVESTMENT.name)!!,
                portfolioRole = PORTFOLIO_ASSET
            )
        ).id

    private fun update(
        code: String = ASSET,
        label: String = "Apple",
        fractionDigits: Int = 2,
        oldCode: String? = null,
        oldFractionDigits: Int? = null,
        type: CommodityType? = null
    ) = runBlocking {
        repository.updateCurrency(
            assetId,
            code,
            code,
            label,
            fractionDigits,
            oldCode,
            type,
            oldFractionDigits
        )
    }

    // Types

    @Test
    fun preciousMetalsAreSeededAsCommodities() {
        CurrencyEnum.PRECIOUS_METALS.forEach {
            assertThat(currencyContext[it].commodityType).isEqualTo(CommodityType.COMMODITY)
        }
        assertThat(CurrencyEnum.PRECIOUS_METALS).containsExactly("XAU", "XAG", "XPT", "XPD")
    }

    @Test
    fun metalsHaveEightDigitsAndOtherCurrenciesTheirOwn() {
        assertThat(currencyContext["XAU"].fractionDigits).isEqualTo(8)
        assertThat(currencyContext[EUR].fractionDigits).isEqualTo(2)
        assertThat(currencyContext[EUR].commodityType).isEqualTo(CommodityType.FIAT)
    }

    @Test
    fun priceOfAMetalKeepsItsRealValue() {
        repository.savePrice(
            currencyContext[EUR],
            currencyContext["XAU"],
            DATE,
            ExchangeRateSource.User,
            "2500.50".toBigDecimal()
        )
        assertThat(rawPrices().single().third).isWithin(1E-12).of(0.0025005)
        val real = repository.loadPrice(
            currencyContext[EUR],
            currencyContext["XAU"],
            DATE,
            ExchangeRateSource.User
        )
        assertThat(real?.toDouble()).isEqualTo(2500.5)
    }

    @Test
    fun customAssetKeepsItsType() {
        val asset = currencyContext[ASSET]
        assertThat(asset.commodityType).isEqualTo(CommodityType.SECURITY)
        assertThat(asset.description).isEqualTo("Apple")
        assertThat(asset.fractionDigits).isEqualTo(2)
    }

    @Test
    fun codeCanOnlyBeInsertedOnce() {
        val second = runBlocking {
            repository.insertCurrency(ASSET, ASSET, "Again", 2, CommodityType.SECURITY)
        }
        assertThat(second).isNull()
    }

    // Changing

    @Test
    fun changingTheDigitsKeepsTheRealPrice() {
        savePrice(150.0)
        update(fractionDigits = 4, oldFractionDigits = 2)
        assertThat(rawPrices().single().third).isEqualTo(1.5)
    }

    @Test
    fun changingTheDigitsScalesThePosition() {
        val accountId = assetAccount()
        val transaction = repository.insertTransaction(accountId, 1000).id
        val (updatedCurrencies, updatedAccounts) = update(fractionDigits = 4, oldFractionDigits = 2)
        assertThat(updatedCurrencies).isEqualTo(1)
        assertThat(updatedAccounts).isEqualTo(1)
        assertThat(repository.loadTransaction(transaction).data.amount).isEqualTo(100000)
    }

    @Test
    fun changingTheDigitsOfABaseScalesItsPricesTheOtherWay() {
        savePrice(150.0, base = ASSET, commodity = EUR)
        update(fractionDigits = 4, oldFractionDigits = 2)
        assertThat(rawPrices().single().third).isEqualTo(15000.0)
    }

    @Test
    fun renamingTheLabelRenamesTheAssetAccounts() {
        val asset = assetAccount()
        val other = insertAccount("Unrelated", currency = EUR)
        update(label = "Apple Inc.")
        assertThat(repository.loadAccount(asset)?.label).isEqualTo("Apple Inc.")
        assertThat(repository.loadAccount(other)?.label).isEqualTo("Unrelated")
    }

    @Test
    fun renamingTheCodeMovesPricesAndAccounts() {
        savePrice(150.0)
        val accountId = assetAccount()
        update(code = "APPL", oldCode = ASSET)
        assertThat(rawPrices().single().second).isEqualTo("APPL")
        assertThat(repository.loadAccount(accountId)?.currency).isEqualTo("APPL")
    }

    @Test
    fun renamingTheCodeOfABaseWithPricesFails() {
        savePrice(150.0, base = ASSET, commodity = EUR)
        // Looks like a bug: prices of the commodity follow a new code, those of the base hold a
        // foreign key without ON UPDATE, which makes the update fail with an exception.
        assertThrows(SQLiteConstraintException::class.java) {
            update(code = "APPL", oldCode = ASSET)
        }
        assertThat(rawPrices().single().first).isEqualTo(ASSET)
    }

    @Test
    fun changingTheType() {
        update(type = CommodityType.COMMODITY)
        currencyContext.invalidate(ASSET)
        assertThat(currencyContext[ASSET].commodityType).isEqualTo(CommodityType.COMMODITY)
    }

    // Deleting

    @Test
    fun knownCurrencyCanNotBeDeleted() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.deleteCurrency("XAU") }
        }
    }

    @Test
    fun assetInUseByAnAccountIsNotDeleted() = runBlocking {
        assetAccount()
        assertThat(repository.isCurrencyUsed(ASSET)).isTrue()
        assertThat(repository.deleteCurrency(ASSET)).isEqualTo(0)
    }

    @Test
    fun unusedAssetIsDeleted() = runBlocking {
        assertThat(repository.isCurrencyUsed(ASSET)).isFalse()
        assertThat(repository.deleteCurrency(ASSET)).isEqualTo(1)
    }

    @Test
    fun assetWithPricesCountsAsUsed() = runBlocking {
        savePrice(150.0)
        assertThat(repository.isCurrencyUsed(ASSET)).isTrue()
    }

    @Test
    fun deletingAnAssetLeavesItsPricesBehind(): Unit = runBlocking {
        savePrice(150.0)
        assertThat(repository.deleteCurrency(ASSET)).isEqualTo(1)
        // Looks like a bug: only the base has a foreign key, prices of the commodity stay behind.
        assertThat(rawPrices().map { it.second }).containsExactly(ASSET)
    }

    @Test
    fun deletingABaseDeletesItsPrices() = runBlocking {
        savePrice(150.0, base = ASSET, commodity = EUR)
        assertThat(repository.deleteCurrency(ASSET)).isEqualTo(1)
        assertThat(rawPrices()).isEmpty()
    }

    companion object {
        const val EUR = "EUR"
        const val ASSET = "AAPL"
        val DATE: LocalDate = LocalDate.of(2025, 1, 10)
    }
}
