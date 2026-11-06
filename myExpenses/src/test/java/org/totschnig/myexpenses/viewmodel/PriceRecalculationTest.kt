package org.totschnig.myexpenses.viewmodel

import android.content.ContentUris
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.db2.loadPrice
import org.totschnig.myexpenses.db2.savePrice
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_EQUIVALENT_AMOUNT
import org.totschnig.myexpenses.provider.KEY_EXCHANGE_RATE
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.retrofit.ExchangeRateApi
import org.totschnig.myexpenses.retrofit.ExchangeRateSource

/**
 * Adds to PriceCalculationViewModelTest what a change of the home currency does with sources, with
 * prices that exist already and with more than one price to choose from. Comments starting with
 * "Looks like a bug" mark behaviour that is pinned, not endorsed.
 */
@RunWith(AndroidJUnit4::class)
class PriceRecalculationTest : BaseViewModelTest() {

    private val viewModel by lazy {
        PriceCalculationViewModel(application, SavedStateHandle()).also {
            application.injector.inject(it)
        }
    }

    private fun save(
        base: String,
        commodity: String,
        value: Double,
        source: ExchangeRateSource = ExchangeRateApi.Frankfurter,
        date: LocalDate = DATE
    ) = repository.savePrice(base, commodity, date, source, value)

    private fun recalculate(home: String): Int = runBlocking { viewModel.reCalculatePrices(home) }

    private fun price(
        base: String,
        commodity: String,
        source: ExchangeRateSource? = null,
        date: LocalDate = DATE
    ): BigDecimal? = repository.loadPrice(
        currencyContext[base],
        currencyContext[commodity],
        date,
        source
    )

    // New home currency

    @Test
    fun inverseIsCalculatedAndCounted() {
        save("EUR", "USD", 2.0)
        assertThat(recalculate("USD")).isEqualTo(1)
        assertThat(price("USD", "EUR", ExchangeRateSource.Calculation)?.toDouble()).isEqualTo(0.5)
    }

    @Test
    fun inverseRespectsDifferentFractionDigits() {
        save("EUR", "JPY", 0.63)
        recalculate("JPY")
        val real = price("JPY", "EUR", ExchangeRateSource.Calculation)!!.toDouble()
        // 1 JPY costs 0.0063 EUR, so that 1 EUR costs about 158.73 JPY.
        assertThat(real).isWithin(0.01).of(158.73)
    }

    @Test
    fun priceThroughTheOldHomeCurrencyIsCalculated() {
        save("EUR", "USD", 2.0)
        save("EUR", "CHF", 3.0)
        assertThat(recalculate("USD")).isEqualTo(2)
        assertThat(price("USD", "CHF", ExchangeRateSource.Calculation)?.toDouble()).isEqualTo(1.5)
    }

    @Test
    fun existingPriceIsNeitherReplacedNorCounted() {
        save("EUR", "USD", 2.0)
        save("USD", "EUR", 0.4, source = ExchangeRateSource.User)
        assertThat(recalculate("USD")).isEqualTo(0)
        assertThat(price("USD", "EUR")?.toDouble()).isEqualTo(0.4)
        assertThat(price("USD", "EUR", ExchangeRateSource.Calculation)).isNull()
    }

    @Test
    fun existingPriceServesAsReference() {
        save("USD", "EUR", 0.4)
        save("EUR", "CHF", 5.0)
        assertThat(recalculate("USD")).isEqualTo(1)
        assertThat(price("USD", "CHF", ExchangeRateSource.Calculation)?.toDouble()).isEqualTo(2.0)
    }

    @Test
    fun calculatedPricesAreNotASourceForFurtherCalculation() {
        save("EUR", "USD", 2.0, source = ExchangeRateSource.Calculation)
        assertThat(recalculate("USD")).isEqualTo(0)
        assertThat(price("USD", "EUR")).isNull()
    }

    @Test
    fun allSourcesExceptCalculationAreUsed() {
        save("EUR", "USD", 2.0, source = ExchangeRateSource.User)
        save("EUR", "CHF", 3.0, source = ExchangeRateSource.Import)
        assertThat(recalculate("USD")).isEqualTo(2)
    }

    @Test
    fun pricesOfOtherDaysAreNotUsed() {
        save("EUR", "USD", 2.0)
        save("EUR", "CHF", 3.0, date = DATE.plusDays(1))
        assertThat(recalculate("USD")).isEqualTo(1)
        assertThat(price("USD", "CHF", date = DATE.plusDays(1))).isNull()
    }

    @Test
    fun withoutAConnectionNothingIsCalculated() {
        save("EUR", "USD", 2.0)
        assertThat(recalculate("CHF")).isEqualTo(0)
    }

    @Test
    fun secondRunFindsNothingToDo() {
        save("EUR", "USD", 2.0)
        assertThat(recalculate("USD")).isEqualTo(1)
        assertThat(recalculate("USD")).isEqualTo(0)
    }

    @Test
    fun recalculationDoesNotTouchEquivalentAmounts() {
        val accountId = insertAccount("Account", currency = "EUR", dynamic = true)
        val transaction = repository.insertTransaction(accountId, 100, date = DATE.atTime(12, 0)).id
        prefHandler.putString(PrefKey.HOME_CURRENCY, "USD")
        save("USD", "CHF", 3.0)
        save("CHF", "EUR", 2.0)
        recalculate("USD")
        assertThat(equivalentAmount(transaction)).isNull()
    }

    // Equivalent amounts from several prices

    private fun recalculateAmounts(home: String) = runBlocking {
        prefHandler.putString(PrefKey.HOME_CURRENCY, home)
        viewModel.reCalculateEquivalentAmounts(home)
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

    private fun accountRate(accountId: Long): Double? = contentResolver.query(
        ContentUris.withAppendedId(TransactionProvider.ACCOUNTS_URI, accountId),
        arrayOf(KEY_EXCHANGE_RATE),
        null,
        null,
        null
    )!!.use {
        it.moveToFirst()
        if (it.isNull(0)) null else it.getDouble(0)
    }

    @Test
    fun transactionTakesTheNewestPriceUpToItsDay() {
        val accountId = insertAccount("Account", currency = "EUR", dynamic = true)
        val transaction = repository.insertTransaction(accountId, 100, date = DATE.atTime(12, 0)).id
        save("USD", "EUR", 1.1, date = DATE.minusDays(5))
        save("USD", "EUR", 1.5, date = DATE.minusDays(1))
        save("USD", "EUR", 1.9, date = DATE.plusDays(1))
        recalculateAmounts("USD")
        assertThat(equivalentAmount(transaction)).isEqualTo(150)
    }

    @Test
    fun transactionPrefersTheUserPriceOfADay() {
        val accountId = insertAccount("Account", currency = "EUR", dynamic = true)
        val transaction = repository.insertTransaction(accountId, 100, date = DATE.atTime(12, 0)).id
        save("USD", "EUR", 1.1, source = ExchangeRateApi.Frankfurter)
        save("USD", "EUR", 1.5, source = ExchangeRateSource.User)
        recalculateAmounts("USD")
        assertThat(equivalentAmount(transaction)).isEqualTo(150)
    }

    @Test
    fun accountRateTakesTheOldestPriceUpToItsFirstTransaction() {
        val accountId = insertAccount("Account", currency = "EUR", dynamic = true)
        repository.insertTransaction(accountId, 100, date = DATE.atTime(12, 0))
        save("USD", "EUR", 1.1, date = DATE.minusDays(5))
        save("USD", "EUR", 1.5, date = DATE.minusDays(1))
        recalculateAmounts("USD")
        // Looks like a bug: the query for the account rate has no sort order, unlike the one for
        // transactions, so that it takes the first row instead of the nearest date.
        assertThat(accountRate(accountId)).isEqualTo(1.1)
    }

    companion object {
        val DATE: LocalDate = LocalDate.of(2025, 1, 10)
    }
}
