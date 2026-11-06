package org.totschnig.myexpenses.util

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.db2.insertCurrency
import org.totschnig.myexpenses.db2.loadPrice
import org.totschnig.myexpenses.db2.savePrice
import org.totschnig.myexpenses.model.CommodityType
import org.totschnig.myexpenses.preference.PrefHandler.Companion.AUTOMATIC_EXCHANGE_RATE_DOWNLOAD_PREF_KEY_PREFIX
import org.totschnig.myexpenses.preference.PrefHandler.Companion.SERVICE_DEACTIVATED
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.retrofit.ExchangeRateApi
import org.totschnig.myexpenses.retrofit.ExchangeRateService
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.retrofit.MissingApiKeyException

/**
 * Pins when a rate comes from the stored prices and when from the network, and what is stored after
 * a download. The network itself is replaced.
 */
@RunWith(RobolectricTestRunner::class)
class ExchangeRateHandlerTest : BaseTestWithRepository() {

    private val today = LocalDate.now()
    private val past = today.minusDays(5)
    private val eur by lazy { currencyContext["EUR"] }
    private val usd by lazy { currencyContext["USD"] }

    private fun handler(service: ExchangeRateService = mock()) =
        ExchangeRateHandler(service, repository, prefHandler, currencyContext)

    private fun serviceReturning(date: LocalDate, rate: Double) = mock<ExchangeRateService> {
        onBlocking { getRate(any(), anyOrNull(), any(), any(), any()) } doReturn (date to rate)
    }

    @Before
    fun setUp() {
        prefHandler.putString(PrefKey.HOME_CURRENCY, "EUR")
    }

    private fun save(
        date: LocalDate,
        value: Double,
        source: ExchangeRateSource = ExchangeRateSource.User
    ) = repository.savePrice("EUR", "USD", date, source, value)

    // Stored or from the network

    @Test
    fun storedPriceIsUsedWithoutGivenSource() = runBlocking {
        val service = mock<ExchangeRateService>()
        save(today, 1.5)
        assertThat(handler(service).loadExchangeRate(usd, eur, today).toDouble()).isEqualTo(1.5)
        verifyNoInteractions(service)
    }

    @Test
    fun missingPriceIsDownloadedFromTheBestSourceAndStoredUnderTheDateOfTheAnswer() = runBlocking {
        val answered = past.minusDays(1)
        val service = serviceReturning(answered, 1.25)
        val rate = handler(service).loadExchangeRate(usd, eur, past)
        assertThat(rate.toDouble()).isEqualTo(1.25)
        verify(service).getRate(
            eq(ExchangeRateApi.Frankfurter),
            isNull(),
            eq(past),
            eq("USD"),
            eq("EUR")
        )
        val stored = repository.loadPrice(eur, usd, answered, ExchangeRateApi.Frankfurter)
        assertThat(stored?.toDouble()).isEqualTo(1.25)
        assertThat(repository.loadPrice(eur, usd, past, null)).isNull()
    }

    @Test
    fun priceOfAnotherDayIsNotAFallback() = runBlocking {
        save(past.minusDays(1), 9.0)
        val service = serviceReturning(past, 1.25)
        assertThat(handler(service).loadExchangeRate(usd, eur, past).toDouble()).isEqualTo(1.25)
    }

    @Test
    fun givenSourceIgnoresPricesOfOtherSources() = runBlocking {
        save(past, 9.0, ExchangeRateSource.User)
        val service = serviceReturning(past, 1.25)
        val rate = handler(service).loadExchangeRate(usd, eur, past, ExchangeRateApi.Frankfurter)
        assertThat(rate.toDouble()).isEqualTo(1.25)
    }

    @Test
    fun storedPriceOfALimitedSourceIsUsedEvenForToday() = runBlocking {
        val service = mock<ExchangeRateService>()
        save(today, 1.5, ExchangeRateApi.Frankfurter)
        val rate = handler(service).loadExchangeRate(usd, eur, today, ExchangeRateApi.Frankfurter)
        assertThat(rate.toDouble()).isEqualTo(1.5)
        verifyNoInteractions(service)
    }

    @Test
    fun unlimitedSourceAsksAgainForTodayButNotForThePast() = runBlocking {
        prefHandler.putString(PrefKey.COIN_API_API_KEY, "key")
        save(today, 1.5, ExchangeRateApi.CoinApi)
        save(past, 1.5, ExchangeRateApi.CoinApi)
        val service = serviceReturning(today, 1.75)
        val forToday = handler(service).loadExchangeRate(usd, eur, today, ExchangeRateApi.CoinApi)
        assertThat(forToday.toDouble()).isEqualTo(1.75)
        verify(service).getRate(
            eq(ExchangeRateApi.CoinApi),
            eq("key"),
            eq(today),
            eq("USD"),
            eq("EUR")
        )
        val forPast = handler(mock()).loadExchangeRate(usd, eur, past, ExchangeRateApi.CoinApi)
        assertThat(forPast.toDouble()).isEqualTo(1.5)
    }

    @Test
    fun sourceWithoutKeyFails() {
        assertThrows(MissingApiKeyException::class.java) {
            runBlocking {
                handler().loadExchangeRate(usd, eur, past, ExchangeRateApi.CoinApi)
            }
        }
    }

    @Test
    fun commodityThatNoSourceSupportsFails() {
        runBlocking {
            repository.insertCurrency("AAPL", "AAPL", "Apple", 2, CommodityType.SECURITY)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking {
                handler().loadExchangeRate(currencyContext["AAPL"], eur, past)
            }
        }
    }

    // Which sources

    private fun configure(vararg sources: ExchangeRateApi) {
        prefHandler.putStringSet(PrefKey.EXCHANGE_RATE_PROVIDER, sources.map { it.name }.toSet())
    }

    @Test
    fun frankfurterIsTheDefault() {
        assertThat(handler().relevantSources("USD")).containsExactly(ExchangeRateApi.Frankfurter)
    }

    @Test
    fun emptyConfigurationMeansNoSource() {
        configure()
        assertThat(handler().relevantSources("USD")).isEmpty()
    }

    @Test
    fun configuredSourcesThatSupportThePairAreRelevant() {
        configure(ExchangeRateApi.Frankfurter, ExchangeRateApi.CoinApi)
        assertThat(handler().relevantSources("USD"))
            .containsExactly(ExchangeRateApi.Frankfurter, ExchangeRateApi.CoinApi)
        assertThat(handler().relevantSources("XAU")).containsExactly(ExchangeRateApi.CoinApi)
    }

    @Test
    fun supportIsCheckedAgainstTheHomeCurrencyNotTheBaseOfThePortfolio() {
        configure(ExchangeRateApi.Frankfurter)
        prefHandler.putString(PrefKey.HOME_CURRENCY, "VND")
        // Looks like a bug: the check does not know the base, a USD portfolio with a home currency
        // that the source does not know gets no source.
        assertThat(handler().relevantSources("USD")).isEmpty()
    }

    private fun pin(commodity: String, value: String) {
        prefHandler.putString("$AUTOMATIC_EXCHANGE_RATE_DOWNLOAD_PREF_KEY_PREFIX$commodity", value)
    }

    @Test
    fun sourceChosenForAutomaticDownloadIsTheOnlyOne() {
        configure(ExchangeRateApi.Frankfurter, ExchangeRateApi.CoinApi)
        pin("USD", ExchangeRateApi.CoinApi.name)
        assertThat(handler().relevantSources("USD")).containsExactly(ExchangeRateApi.CoinApi)
    }

    @Test
    fun chosenSourceThatIsNotConfiguredOrDeactivatedIsIgnored() {
        configure(ExchangeRateApi.Frankfurter)
        pin("USD", ExchangeRateApi.CoinApi.name)
        assertThat(handler().relevantSources("USD")).containsExactly(ExchangeRateApi.Frankfurter)
        pin("USD", SERVICE_DEACTIVATED)
        assertThat(handler().relevantSources("USD")).containsExactly(ExchangeRateApi.Frankfurter)
    }

    // Time series

    @Test
    fun timeSeriesStoresWhatArrivedAndPassesOnTheFailure() = runBlocking {
        val failure = IOException("Partly failed")
        val service = mock<ExchangeRateService> {
            onBlocking {
                getTimeSeries(any(), anyOrNull(), any(), any(), any(), any(), any())
            } doReturn (listOf(past to 1.5, past.plusDays(1) to 1.6) to failure)
        }
        val skipped = setOf(past.plusDays(2))
        val (count, exception) = handler(service).loadTimeSeries(
            ExchangeRateApi.Frankfurter,
            past,
            past.plusDays(2),
            skipped,
            "USD",
            "EUR"
        )
        assertThat(count).isEqualTo(2)
        assertThat(exception).isEqualTo(failure)
        verify(service).getTimeSeries(
            source = eq(ExchangeRateApi.Frankfurter),
            apiKey = isNull(),
            start = eq(past),
            end = eq(past.plusDays(2)),
            base = eq("USD"),
            symbol = eq("EUR"),
            except = eq(skipped)
        )
        val stored = repository.loadPrice(eur, usd, past.plusDays(1), ExchangeRateApi.Frankfurter)
        assertThat(stored?.toDouble()).isEqualTo(1.6)
    }
}
