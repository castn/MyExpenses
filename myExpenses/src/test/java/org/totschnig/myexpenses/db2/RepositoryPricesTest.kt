package org.totschnig.myexpenses.db2

import androidx.core.os.BundleCompat
import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_COMMODITY
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_EQUIVALENT_AMOUNT
import org.totschnig.myexpenses.provider.KEY_MAX_VALUE
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_SOURCE
import org.totschnig.myexpenses.provider.KEY_VALUE
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.retrofit.ExchangeRateApi
import org.totschnig.myexpenses.retrofit.ExchangeRateSource

/**
 * Pins how prices are stored and found: a price relates minor units of a commodity to minor units
 * of the base currency, there is at most one per source and day, and a user price wins over a
 * downloaded one, which wins over a calculated one. Comments starting with "Looks like a bug" mark
 * behaviour that is pinned, not endorsed.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryPricesTest : BaseTestWithRepository() {

    private lateinit var eur: CurrencyUnit
    private lateinit var usd: CurrencyUnit
    private lateinit var jpy: CurrencyUnit

    @Before
    fun setUp() {
        prefHandler.putString(PrefKey.HOME_CURRENCY, EUR)
        eur = currencyContext[EUR]
        usd = currencyContext[USD]
        jpy = currencyContext["JPY"]
    }

    private fun save(
        value: Double,
        date: LocalDate = DATE,
        source: ExchangeRateSource = ExchangeRateSource.User,
        base: String = EUR,
        commodity: String = USD
    ) = repository.savePrice(base, commodity, date, source, value)

    private fun rawValues(
        base: String = EUR,
        commodity: String = USD
    ): List<Triple<String, String, Double>> = contentResolver.query(
        TransactionProvider.PRICES_URI,
        arrayOf(KEY_DATE, KEY_SOURCE, KEY_VALUE),
        "$KEY_CURRENCY = ? AND $KEY_COMMODITY = ?",
        arrayOf(base, commodity),
        "$KEY_DATE DESC"
    )!!.useAndMapToList { Triple(it.getString(0), it.getString(1), it.getDouble(2)) }

    private fun latest(date: String? = null): List<Triple<String, String, Double>> =
        contentResolver.query(
            TransactionProvider.LATEST_PRICES_URI.let { uri ->
                date?.let { uri.buildUpon().appendQueryParameter(KEY_DATE, it).build() } ?: uri
            },
            null,
            null,
            null,
            null
        )!!.useAndMapToList {
            Triple(
                it.getString(it.getColumnIndexOrThrow(KEY_COMMODITY)),
                it.getString(it.getColumnIndexOrThrow(KEY_DATE)),
                it.getDouble(it.getColumnIndexOrThrow(KEY_VALUE))
            )
        }

    private fun equivalentAmount(transactionId: Long): Long? = contentResolver.query(
        TransactionProvider.TRANSACTIONS_URI,
        arrayOf(KEY_EQUIVALENT_AMOUNT),
        "$KEY_ROWID = ?",
        arrayOf(transactionId.toString()),
        null
    )!!.use {
        it.moveToFirst()
        if (it.isNull(0)) null else it.getLong(0)
    }

    // Storing

    @Test
    fun priceInMajorUnitsIsStoredInMinorUnits() {
        repository.savePrice(eur, jpy, DATE, ExchangeRateSource.User, BigDecimal("0.0063"))
        assertThat(rawValues(commodity = "JPY").single().third).isEqualTo(0.63)
        assertThat(repository.loadPrice(eur, jpy, DATE, null)?.toDouble()).isEqualTo(0.0063)
    }

    @Test
    fun priceAgainstACurrencyWithMoreDigitsIsScaledTheOtherWay() {
        repository.savePrice(jpy, eur, DATE, ExchangeRateSource.User, BigDecimal("158.73"))
        assertThat(rawValues(base = "JPY", commodity = EUR).single().third).isEqualTo(1.5873)
        assertThat(repository.loadPrice(jpy, eur, DATE, null)?.toDouble()).isEqualTo(158.73)
    }

    @Test
    fun priceMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { save(0.0) }
        assertThrows(IllegalArgumentException::class.java) { save(-1.0) }
        assertThat(rawValues()).isEmpty()
    }

    @Test
    fun sameDayAndSourceReplaces() {
        save(1.5)
        save(1.6)
        assertThat(rawValues().single().third).isEqualTo(1.6)
    }

    // Looking up by date

    @Test
    fun loadPriceFindsOnlyTheExactDate() {
        save(1.5)
        assertThat(repository.loadPrice(eur, usd, DATE, null)?.toDouble()).isEqualTo(1.5)
        assertThat(repository.loadPrice(eur, usd, DATE.plusDays(1), null)).isNull()
        assertThat(repository.loadPrice(eur, usd, DATE.minusDays(1), null)).isNull()
    }

    @Test
    fun loadPriceIsNotInverted() {
        save(1.5)
        assertThat(repository.loadPrice(usd, eur, DATE, null)).isNull()
    }

    @Test
    fun loadPriceWithSourceReadsOnlyThatSource() {
        save(1.5, source = ExchangeRateApi.Frankfurter)
        val frankfurter = repository.loadPrice(eur, usd, DATE, ExchangeRateApi.Frankfurter)
        assertThat(frankfurter?.toDouble()).isEqualTo(1.5)
        assertThat(repository.loadPrice(eur, usd, DATE, ExchangeRateSource.User)).isNull()
    }

    @Test
    fun latestPriceFallsBackToTheNearestEarlierDate() {
        save(1.1, DATE.minusDays(5))
        save(1.2, DATE)
        save(1.3, DATE.plusDays(5))
        assertThat(latest(DATE.plusDays(1).toString()))
            .containsExactly(Triple(USD, DATE.toString(), 1.2))
        assertThat(latest(DATE.minusDays(1).toString()))
            .containsExactly(Triple(USD, DATE.minusDays(5).toString(), 1.1))
        assertThat(latest(DATE.minusDays(6).toString())).isEmpty()
    }

    @Test
    fun latestPriceIgnoresTheFutureByDefault() {
        val today = LocalDate.now()
        save(1.2, today)
        save(1.3, today.plusDays(1))
        assertThat(latest()).containsExactly(Triple(USD, today.toString(), 1.2))
        assertThat(latest("now")).containsExactly(Triple(USD, today.toString(), 1.2))
    }

    @Test
    fun latestPriceIsPerPair() {
        save(1.2, DATE)
        save(1.5, DATE.minusDays(1), commodity = "CHF")
        assertThat(latest(DATE.toString())).containsExactly(
            Triple(USD, DATE.toString(), 1.2),
            Triple("CHF", DATE.minusDays(1).toString(), 1.5)
        )
    }

    // Which source wins

    @Test
    fun userWinsOverImportOverDownloadOverCalculation() {
        save(4.0, source = ExchangeRateSource.Calculation)
        assertThat(repository.loadPrice(eur, usd, DATE, null)?.toDouble()).isEqualTo(4.0)
        save(3.0, source = ExchangeRateApi.Frankfurter)
        assertThat(repository.loadPrice(eur, usd, DATE, null)?.toDouble()).isEqualTo(3.0)
        save(2.0, source = ExchangeRateSource.Import)
        assertThat(repository.loadPrice(eur, usd, DATE, null)?.toDouble()).isEqualTo(2.0)
        save(1.0, source = ExchangeRateSource.User)
        assertThat(repository.loadPrice(eur, usd, DATE, null)?.toDouble()).isEqualTo(1.0)
    }

    @Test
    fun amongDownloadsTheSourceNameDecides() {
        save(1.0, source = ExchangeRateApi.CoinApi)
        save(2.0, source = ExchangeRateApi.OpenExchangeRates)
        save(3.0, source = ExchangeRateApi.Frankfurter)
        assertThat(repository.loadPrice(eur, usd, DATE, null)?.toDouble()).isEqualTo(2.0)
    }

    @Test
    fun listAndLatestShowOnlyTheWinnerOfADay() {
        save(3.0, source = ExchangeRateApi.Frankfurter)
        save(1.0, source = ExchangeRateSource.User)
        assertThat(rawValues()).containsExactly(
            Triple(DATE.toString(), ExchangeRateSource.User.name, 1.0)
        )
        assertThat(latest(DATE.toString())).containsExactly(Triple(USD, DATE.toString(), 1.0))
    }

    // Deleting

    @Test
    fun deletingOneSourceBringsTheNextOneBack() {
        save(3.0, source = ExchangeRateApi.Frankfurter)
        save(1.0, source = ExchangeRateSource.User)
        assertThat(repository.deletePrice(DATE, ExchangeRateSource.User, EUR, USD)).isEqualTo(1)
        assertThat(rawValues().single().third).isEqualTo(3.0)
    }

    @Test
    fun deletingNeedsTheExactKey() {
        save(1.0)
        assertThat(repository.deletePrice(DATE.plusDays(1), ExchangeRateSource.User, EUR, USD))
            .isEqualTo(0)
        assertThat(repository.deletePrice(DATE, ExchangeRateSource.Import, EUR, USD)).isEqualTo(0)
        assertThat(repository.deletePrice(DATE, ExchangeRateSource.User, USD, EUR)).isEqualTo(0)
        assertThat(rawValues()).hasSize(1)
    }

    // Equivalent amounts

    private fun dynamicTransaction(
        date: LocalDate = DATE,
        amount: Long = 100,
        dynamic: Boolean = true,
        currency: String = USD
    ): Long {
        val accountId = insertAccount("Account", currency = currency, dynamic = dynamic)
        return repository.insertTransaction(accountId, amount, date = date.atTime(12, 0)).id
    }

    @Test
    fun savingUpdatesEquivalentAmountsOfThatDay() {
        val sameDay = dynamicTransaction()
        val otherDay = dynamicTransaction(date = DATE.plusDays(1))
        assertThat(save(1.5)).isEqualTo(1)
        assertThat(equivalentAmount(sameDay)).isEqualTo(150)
        // A price of a day also applies to the days after, but these are not updated.
        assertThat(equivalentAmount(otherDay)).isNull()
    }

    @Test
    fun savingLeavesStaticAccountsAlone() {
        val static = dynamicTransaction(dynamic = false)
        assertThat(save(1.5)).isEqualTo(0)
        assertThat(equivalentAmount(static)).isNull()
    }

    @Test
    fun updatingEquivalentAmountsCanBeSwitchedOff() {
        prefHandler.putBoolean(PrefKey.EQUIVALENT_AMOUNTS_AUTOMATIC_UPDATE, false)
        val transaction = dynamicTransaction()
        assertThat(save(1.5)).isEqualTo(0)
        assertThat(equivalentAmount(transaction)).isNull()
    }

    @Test
    fun priceAgainstAnotherBaseUpdatesNothing() {
        val transaction = dynamicTransaction()
        assertThat(save(1.5, base = "CHF")).isEqualTo(0)
        assertThat(equivalentAmount(transaction)).isNull()
    }

    @Test
    fun deletingAPriceKeepsTheEquivalentAmounts() {
        val transaction = dynamicTransaction()
        save(1.5)
        repository.deletePrice(DATE, ExchangeRateSource.User, EUR, USD)
        // Looks like a bug: the amount stays calculated from a price that no longer exists.
        assertThat(equivalentAmount(transaction)).isEqualTo(150)
    }

    @Test
    fun lowerRankedPriceStillOverwritesWithTheWinner() {
        val transaction = dynamicTransaction()
        save(1.5, source = ExchangeRateSource.User)
        save(2.0, source = ExchangeRateApi.Frankfurter)
        assertThat(equivalentAmount(transaction)).isEqualTo(150)
    }

    // Query for one pair

    @Test
    fun queryForAPairReportsTheOldestTransactionOfDynamicAccounts() {
        dynamicTransaction(date = DATE.minusDays(3))
        dynamicTransaction(date = DATE.minusDays(10), dynamic = false)
        contentResolver.query(
            TransactionProvider.PRICES_URI.buildUpon()
                .appendQueryParameter(KEY_COMMODITY, USD)
                .appendQueryParameter(KEY_CURRENCY, EUR)
                .build(),
            arrayOf(KEY_DATE),
            null,
            null,
            null
        )!!.use {
            assertThat(
                BundleCompat.getSerializable(it.extras, KEY_MAX_VALUE, LocalDate::class.java)
            ).isEqualTo(DATE.minusDays(3))
        }
    }

    companion object {
        const val EUR = "EUR"
        const val USD = "USD"
        val DATE: LocalDate = LocalDate.of(2025, 1, 10)
    }
}
