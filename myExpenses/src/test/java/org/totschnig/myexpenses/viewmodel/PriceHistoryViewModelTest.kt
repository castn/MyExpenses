package org.totschnig.myexpenses.viewmodel

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.insertCurrency
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.db2.loadPrice
import org.totschnig.myexpenses.db2.savePrice
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_COMMODITY
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.retrofit.ExchangeRateApi
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.util.ExchangeRateHandler
import org.totschnig.myexpenses.viewmodel.data.Price

/**
 * Pins the logic behind the price history of one commodity: the list with its gaps, what editing
 * stores, and the CSV import and export. Comments starting with "Looks like a bug" mark behaviour
 * that is pinned, not endorsed.
 */
@RunWith(AndroidJUnit4::class)
class PriceHistoryViewModelTest : BaseViewModelTest() {

    @get:Rule
    val folder = TemporaryFolder()

    private val created = mutableListOf<PriceHistoryViewModel>()
    private lateinit var viewModel: PriceHistoryViewModel
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        prefHandler.putString(PrefKey.HOME_CURRENCY, HOME)
        viewModel = newViewModel()
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
    }

    private fun newViewModel(commodity: String = COMMODITY, base: String? = null) =
        PriceHistoryViewModel(
            application,
            SavedStateHandle(
                buildMap {
                    put(KEY_COMMODITY, commodity)
                    base?.let { put(KEY_CURRENCY, it) }
                }
            )
        ).also {
            application.appComponent.inject(it)
            created.add(it)
        }

    private fun price(date: LocalDate, value: String = "1") =
        Price(date, ExchangeRateSource.User, BigDecimal(value))

    private fun <T> LiveData<T>.await(): T {
        var value: T? = null
        val observer = Observer<T> { value = it }
        observeForever(observer)
        val deadline = System.currentTimeMillis() + 10_000
        while (value == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (value == null) Thread.sleep(10)
        }
        removeObserver(observer)
        return checkNotNull(value)
    }

    /** Runs the block while the state flow of prices is subscribed, once it has reached [until]. */
    private fun <T> withPrices(
        until: (Map<LocalDate, Price?>) -> Boolean,
        block: (Map<LocalDate, Price?>) -> T
    ): T {
        val job = viewModel.viewModelScope.launch { viewModel.pricesWithMissingDates.collect {} }
        try {
            val deadline = System.currentTimeMillis() + 10_000
            while (!until(viewModel.pricesWithMissingDates.value) &&
                System.currentTimeMillis() < deadline
            ) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            return block(viewModel.pricesWithMissingDates.value)
        } finally {
            job.cancel()
        }
    }

    private fun <T> Flow<T>.awaitFirst(): T {
        var value: T? = null
        val job = viewModel.viewModelScope.launch { value = first() }
        val deadline = System.currentTimeMillis() + 10_000
        while (value == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (value == null) Thread.sleep(10)
        }
        job.cancel()
        return checkNotNull(value)
    }

    // Filling the gaps

    private fun fill(prices: List<Price>, start: LocalDate? = null, end: LocalDate? = null) =
        with(viewModel) { prices.fillInMissingDates(start, end) }

    @Test
    fun gapsAreFilledNewestFirstDownToTheOldestPrice() {
        val start = LocalDate.of(2025, 1, 10)
        val newest = price(start)
        val oldest = price(start.minusDays(2))
        val result = fill(listOf(newest, oldest), start = start)
        assertThat(result.keys)
            .containsExactly(start, start.minusDays(1), start.minusDays(2))
            .inOrder()
        assertThat(result[start]).isEqualTo(newest)
        assertThat(result[start.minusDays(1)]).isNull()
        assertThat(result[start.minusDays(2)]).isEqualTo(oldest)
    }

    @Test
    fun theEarlierOfEndAndOldestPriceIsTheLastDay() {
        val start = LocalDate.of(2025, 1, 10)
        val prices = listOf(price(start.minusDays(1)))
        assertThat(fill(prices, start, end = start.minusDays(3)).keys.last())
            .isEqualTo(start.minusDays(3))
        assertThat(fill(prices, start, end = start).keys.last()).isEqualTo(start.minusDays(1))
    }

    @Test
    fun withoutAnythingOnlyTheFirstDayIsListed() {
        assertThat(fill(emptyList()).keys).containsExactly(today)
    }

    @Test
    fun onlyPriceAfterTheFirstDayEmptiesTheList() {
        val start = LocalDate.of(2025, 1, 10)
        // Looks like a bug: a single price in the future leaves no day at all, not even today.
        assertThat(fill(listOf(price(start.plusDays(1))), start)).isEmpty()
    }

    @Test
    fun priceAfterTheFirstDayIsNotListed() {
        val start = LocalDate.of(2025, 1, 10)
        val result = fill(listOf(price(start.plusDays(1)), price(start)), start)
        assertThat(result.keys).containsExactly(start)
    }

    // The list

    @Test
    fun pricesAreListedInRealValuesForThePairOnly() {
        repository.savePrice(HOME, COMMODITY, today, ExchangeRateSource.User, 1.5)
        repository.savePrice(HOME, "CHF", today, ExchangeRateSource.User, 9.0)
        repository.savePrice("CHF", COMMODITY, today, ExchangeRateSource.User, 8.0)
        val priceOfToday = withPrices({ it[today] != null }) { it[today] }!!
        assertThat(priceOfToday.source).isEqualTo(ExchangeRateSource.User)
        assertThat(priceOfToday.value.toDouble()).isEqualTo(1.5)
    }

    @Test
    fun valueRespectsTheFractionDigits() {
        viewModel = newViewModel(commodity = "JPY")
        repository.savePrice(HOME, "JPY", today, ExchangeRateSource.User, 0.63)
        val value = withPrices({ it[today] != null }) { it[today] }!!.value
        assertThat(value.toDouble()).isEqualTo(0.0063)
    }

    @Test
    fun listReachesBackToTheOldestTransactionOfADynamicAccount() {
        val dynamic = insertAccount("Dynamic", currency = COMMODITY, dynamic = true)
        val oldest = today.minusDays(3)
        repository.insertTransaction(dynamic, 100, date = oldest.atTime(12, 0))
        repository.savePrice(HOME, COMMODITY, today, ExchangeRateSource.User, 1.5)
        val result = withPrices({ it[today] != null }) { it }
        assertThat(result.keys.last()).isEqualTo(oldest)
    }

    @Test
    fun staticAccountDoesNotExtendTheList() {
        val static = insertAccount("Static", currency = COMMODITY, dynamic = false)
        repository.insertTransaction(static, 100, date = today.minusDays(3).atTime(12, 0))
        repository.savePrice(HOME, COMMODITY, today, ExchangeRateSource.User, 1.5)
        val result = withPrices({ it[today] != null }) { it }
        assertThat(result.keys).containsExactly(today)
    }

    // Base currency

    @Test
    fun baseIsTheHomeCurrencyUnlessGiven() {
        assertThat(viewModel.baseCurrency).isEqualTo(HOME)
        assertThat(newViewModel(base = "CHF").baseCurrency).isEqualTo("CHF")
    }

    // Editing

    @Test
    fun savedPriceIsAUserPriceInMinorUnits() {
        val jpy = newViewModel(commodity = "JPY")
        jpy.savePrice(today, BigDecimal("0.0063")).await()
        val stored = repository.loadPrice(
            currencyContext[HOME],
            currencyContext["JPY"],
            today,
            ExchangeRateSource.User
        )
        assertThat(stored?.toDouble()).isEqualTo(0.0063)
    }

    @Test
    fun savingReportsTheUpdatedEquivalentAmounts() {
        val dynamic = insertAccount("Dynamic", currency = COMMODITY, dynamic = true)
        repository.insertTransaction(dynamic, 100, date = today.atTime(12, 0))
        assertThat(viewModel.savePrice(today, BigDecimal("1.5")).await()).isEqualTo(1)
    }

    @Test
    fun deletingAPriceNeedsItsSource() {
        repository.savePrice(HOME, COMMODITY, today, ExchangeRateSource.User, 1.5)
        val other = Price(today, ExchangeRateSource.Import, BigDecimal.ONE)
        assertThat(viewModel.deletePrice(other).await()).isFalse()
        assertThat(viewModel.deletePrice(price(today)).await()).isTrue()
        assertThat(viewModel.deletePrice(price(today)).await()).isFalse()
    }

    @Test
    fun inverseRateIsRememberedPerCommodity() = runBlocking {
        assertThat(viewModel.inverseRate.first()).isFalse()
        viewModel.persistInverseRate(true)
        assertThat(viewModel.inverseRate.first()).isTrue()
        assertThat(newViewModel(commodity = "CHF").inverseRate.first()).isFalse()
        assertThat(newViewModel().inverseRate.first()).isTrue()
    }

    // Sources

    @Test
    fun sourceDependsOnWhatIsConfiguredAndSupported() = runBlocking {
        prefHandler.putStringSet(
            PrefKey.EXCHANGE_RATE_PROVIDER,
            setOf(ExchangeRateApi.Frankfurter.name, ExchangeRateApi.CoinApi.name)
        )
        val both = newViewModel()
        assertThat(both.relevantSources)
            .containsExactly(ExchangeRateApi.Frankfurter, ExchangeRateApi.CoinApi)
        assertThat(both.effectiveSource).isEqualTo(ExchangeRateApi.Frankfurter)
        both.userSelectedSource = ExchangeRateApi.CoinApi
        assertThat(both.effectiveSource).isEqualTo(ExchangeRateApi.CoinApi)

        repository.insertCurrency("AAPL", "AAPL", "Apple", 2, CommodityType.SECURITY)
        prefHandler.putStringSet(
            PrefKey.EXCHANGE_RATE_PROVIDER,
            setOf(ExchangeRateApi.Frankfurter.name)
        )
        assertThat(newViewModel(commodity = "AAPL").effectiveSource).isNull()
        assertThat(newViewModel().effectiveSource).isEqualTo(ExchangeRateApi.Frankfurter)
    }

    // Time series

    @Test
    fun timeSeriesSkipsKnownDatesOnlyForSourcesThatRequestDayByDay() {
        repository.savePrice(HOME, COMMODITY, today, ExchangeRateSource.User, 1.5)
        repository.savePrice(HOME, COMMODITY, today.minusDays(1), ExchangeRateSource.User, 1.5)
        val handler = mock<ExchangeRateHandler> {
            onBlocking {
                loadTimeSeries(any(), any(), any(), any(), any(), any())
            } doReturn (2 to IOException("Partly failed"))
        }
        viewModel.exchangeRateHandler = handler
        withPrices({ it[today] != null }) {
            runBlocking {
                viewModel.loadTimeSeries(
                    ExchangeRateApi.OpenExchangeRates,
                    today.minusDays(2),
                    today.minusDays(1)
                )
                verify(handler).loadTimeSeries(
                    eq(ExchangeRateApi.OpenExchangeRates),
                    eq(today.minusDays(2)),
                    eq(today.minusDays(1)),
                    eq(setOf(today.minusDays(1))),
                    eq(COMMODITY),
                    eq(HOME)
                )
                viewModel.loadTimeSeries(ExchangeRateApi.Frankfurter, today, today)
                verify(handler).loadTimeSeries(
                    eq(ExchangeRateApi.Frankfurter),
                    eq(today),
                    eq(today),
                    eq(emptySet()),
                    eq(COMMODITY),
                    eq(HOME)
                )
            }
        }
        val expected = application.getString(R.string.batch_download_result, 2) + " Partly failed"
        assertThat(viewModel.batchDownloadResult.awaitFirst()).isEqualTo(expected)
        viewModel.messageShown()
    }

    // Import

    private fun csv(vararg lines: String): Uri =
        Uri.fromFile(File(folder.root, "prices.csv").apply { writeText(lines.joinToString("\n")) })

    private fun import(uri: Uri): Result<String> {
        viewModel.importPricesFromUri(uri)
        return viewModel.importResult.awaitFirst()
    }

    private fun successMessage(count: Int) = application.resources.getQuantityString(
        R.plurals.import_prices_success_message,
        count,
        count
    )

    private fun failureMessage(count: Int) = application.resources.getQuantityString(
        R.plurals.import_prices_failure_message,
        count,
        count
    )

    @Test
    fun importedPricesAreStoredWithTheSourceImport() {
        val result = import(csv("2025-01-01,1.5", " 2025-01-02 , 1.6 ,ignored"))
        assertThat(result.getOrThrow()).isEqualTo(successMessage(2) + "\n")
        val eur = currencyContext[HOME]
        val usd = currencyContext[COMMODITY]
        assertThat(
            repository.loadPrice(eur, usd, LocalDate.of(2025, 1, 1), ExchangeRateSource.Import)
                ?.toDouble()
        ).isEqualTo(1.5)
        assertThat(
            repository.loadPrice(eur, usd, LocalDate.of(2025, 1, 2), ExchangeRateSource.Import)
                ?.toDouble()
        ).isEqualTo(1.6)
    }

    @Test
    fun linesThatFailAreCountedAndTheOthersKept() {
        val result = import(
            csv(
                "Date,Value",
                "2025-01-01",
                ",1.5",
                "2025-01-01,abc",
                "2025-01-01,0",
                "2025-01-01,-1",
                "2025-01-05,1.5"
            )
        )
        assertThat(result.getOrThrow())
            .isEqualTo(successMessage(1) + "\n" + failureMessage(6) + "\n")
    }

    @Test
    fun onlyIsoDatesAreAccepted() {
        val result = import(csv("01.02.2025,1.5", "02/01/2025,1.5"))
        assertThat(result.getOrThrow()).isEqualTo(failureMessage(2) + "\n")
    }

    @Test
    fun emptyFileHasNoData() {
        assertThat(import(csv()).getOrThrow()).isEqualTo(application.getString(R.string.no_data))
    }

    @Test
    fun missingFileIsAFailure() {
        val result = import(Uri.fromFile(File(folder.root, "missing.csv")))
        assertThat(result.isFailure).isTrue()
    }

    // Export

    @Test
    fun exportWritesTheKnownPricesOldestFirst() {
        repository.savePrice(HOME, COMMODITY, today, ExchangeRateSource.User, 1.5)
        repository.savePrice(HOME, COMMODITY, today.minusDays(2), ExchangeRateSource.User, 1.25)
        val result = withPrices({ it[today] != null }) {
            viewModel.export()
            viewModel.exportResult.awaitFirst()
        }
        val (uri, displayName) = result.getOrThrow()
        assertThat(displayName).contains("$COMMODITY-$HOME-")
        assertThat(application.contentResolver.openInputStream(uri)!!.bufferedReader().readLines())
            .containsExactly("${today.minusDays(2)},1.25", "$today,1.5")
            .inOrder()
    }

    @Test
    fun exportWithoutSubscriberWritesNothing() {
        repository.savePrice(HOME, COMMODITY, today, ExchangeRateSource.User, 1.5)
        viewModel.export()
        val (uri, _) = viewModel.exportResult.awaitFirst().getOrThrow()
        // Looks like a bug: the export reads the value of a state flow that only fills while the
        // list is subscribed, it does not query the prices itself.
        assertThat(application.contentResolver.openInputStream(uri)!!.bufferedReader().readLines())
            .isEmpty()
    }

    companion object {
        const val HOME = "EUR"
        const val COMMODITY = "USD"
    }
}
