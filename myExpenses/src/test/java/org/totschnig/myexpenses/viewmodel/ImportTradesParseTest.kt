package org.totschnig.myexpenses.viewmodel

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.totschnig.myexpenses.compose.transactions.ImportTradesParseResult
import org.totschnig.myexpenses.export.qif.QifDateFormat
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.TradeType

/**
 * Pins how a file of trades turns into trade intents, including the use of QifUtils for the dates.
 * The parser is a private function in the file of the dialog, so that the tests reach it by
 * reflection. When it moves, the lookup below is the only thing to change.
 */
@RunWith(AndroidJUnit4::class)
class ImportTradesParseTest : BaseTradeTest() {

    @get:Rule
    val folder = TemporaryFolder()

    private suspend fun invokeParser(
        uri: Uri,
        assets: List<CurrencyUnit>,
        fundingSource: FundingSource,
        fundingAccountId: Long?,
        dateFormat: QifDateFormat,
        roundingMode: RoundingMode
    ): ImportTradesParseResult = suspendCoroutineUninterceptedOrReturn { continuation ->
        Class.forName("org.totschnig.myexpenses.compose.transactions.ImportTradesDialogKt")
            .getDeclaredMethod(
                "parseCsv",
                Context::class.java,
                Uri::class.java,
                FullAccount::class.java,
                List::class.java,
                FundingSource::class.java,
                java.lang.Long::class.java,
                QifDateFormat::class.java,
                RoundingMode::class.java,
                Continuation::class.java
            )
            .apply { isAccessible = true }
            .invoke(
                null,
                application,
                uri,
                portfolio,
                assets,
                fundingSource,
                fundingAccountId,
                dateFormat,
                roundingMode,
                continuation
            )
    }

    private fun parse(
        vararg lines: String,
        dateFormat: QifDateFormat = QifDateFormat.YMD,
        assets: List<CurrencyUnit> = listOf(aapl),
        fundingSource: FundingSource = FundingSource.PORTFOLIO,
        fundingAccountId: Long? = null,
        roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): ImportTradesParseResult {
        val file = File(folder.root, "trades.csv").apply { writeText(lines.joinToString("\n")) }
        return runBlocking {
            invokeParser(
                Uri.fromFile(file),
                assets,
                fundingSource,
                fundingAccountId,
                dateFormat,
                roundingMode
            )
        }
    }

    // Amounts

    @Test
    fun quantityAndPriceGiveThePrincipal() {
        val result = parse(
            "Date,Type,Asset,Quantity,Price,Fee",
            "2025-01-10,BUY,AAPL,10,150.5,1.5"
        )
        val intent = result.intents.single()
        assertThat(intent.type).isEqualTo(TradeType.AssetTrade.BUY)
        assertThat(intent.targetAsset).isEqualTo(aapl)
        assertThat(intent.quantity).isEqualTo(Money(aapl, 1000))
        assertThat(intent.price).isEqualTo(BigDecimal("150.5"))
        assertThat(intent.principal).isEqualTo(Money(home, 150500))
        assertThat(intent.fee).isEqualTo(Money(home, 150))
        assertThat(result.parseFailures).isEqualTo(0)
        assertThat(result.missingAssets).isEmpty()
    }

    @Test
    fun quantityAndTotalGiveThePriceWithTenDecimals() {
        val intent = parse(
            "Date,Type,Asset,Quantity,Total",
            "2025-01-10,SELL,AAPL,3,100"
        ).intents.single()
        assertThat(intent.type).isEqualTo(TradeType.AssetTrade.SELL)
        assertThat(intent.price).isEqualTo(BigDecimal("33.3333333333"))
        assertThat(intent.principal).isEqualTo(Money(home, 10000))
        assertThat(intent.quantity).isEqualTo(Money(aapl, 300))
    }

    @Test
    fun priceAndTotalGiveTheQuantity() {
        val intent = parse(
            "Date,Type,Asset,Price,Total",
            "2025-01-10,BUY,AAPL,50,125"
        ).intents.single()
        assertThat(intent.quantity).isEqualTo(Money(aapl, 250))
        assertThat(intent.price).isEqualTo(BigDecimal("50"))
        assertThat(intent.principal).isEqualTo(Money(home, 12500))
    }

    @Test
    fun priceWinsOverAGivenTotal() {
        val intent = parse(
            "Date,Type,Asset,Quantity,Price,Total",
            "2025-01-10,BUY,AAPL,10,5,999"
        ).intents.single()
        assertThat(intent.principal).isEqualTo(Money(home, 5000))
    }

    @Test
    fun amountsAreRoundedWithTheGivenMode() {
        val lines = arrayOf("Date,Type,Asset,Quantity,Price", "2025-01-10,BUY,AAPL,1.005,1.005")
        val up = parse(*lines, roundingMode = RoundingMode.UP).intents.single()
        assertThat(up.quantity).isEqualTo(Money(aapl, 101))
        assertThat(up.principal).isEqualTo(Money(home, 102))
        val down = parse(*lines, roundingMode = RoundingMode.DOWN).intents.single()
        assertThat(down.quantity).isEqualTo(Money(aapl, 100))
        assertThat(down.principal).isEqualTo(Money(home, 101))
    }

    @Test
    fun zeroQuantityWithPriceIsAccepted() {
        val intent = parse(
            "Date,Type,Asset,Quantity,Price",
            "2025-01-10,BUY,AAPL,0,5"
        ).intents.single()
        assertThat(intent.quantity.amountMinor).isEqualTo(0)
    }

    @Test
    fun negativeNumbersAreTakenAsTheyAre() {
        val intent = parse(
            "Date,Type,Asset,Quantity,Price",
            "2025-01-10,SELL,AAPL,-10,5"
        ).intents.single()
        // Looks like a bug: a broker export with negative sell quantities turns into a negative
        // leg, which adds to the position.
        assertThat(intent.quantity.amountMinor).isEqualTo(-1000)
        assertThat(intent.principal.amountMinor).isEqualTo(-5000)
    }

    @Test
    fun missingAmountsAndNumbersThatDoNotParseAreFailures() {
        val result = parse(
            "Date,Type,Asset,Quantity,Price,Total",
            "2025-01-10,BUY,AAPL,10,,",
            "2025-01-10,BUY,AAPL,,5,",
            "2025-01-10,BUY,AAPL,0,,5",
            "2025-01-10,BUY,AAPL,,0,5",
            "2025-01-10,BUY,AAPL,\"1,5\",5,",
            "2025-01-10,BUY,AAPL,10,5,"
        )
        assertThat(result.parseFailures).isEqualTo(5)
        assertThat(result.intents).hasSize(1)
    }

    @Test
    fun feeThatDoesNotParseCountsAsNoFee() {
        val intent = parse(
            "Date,Type,Asset,Quantity,Price,Fee",
            "2025-01-10,BUY,AAPL,10,5,free"
        ).intents.single()
        assertThat(intent.additionalCosts).isEmpty()
    }

    // Columns

    @Test
    fun columnsHaveAliasesAndIgnoreTheCase() {
        val intent = parse(
            "DATE,type,ASSET,Qty,Unit Price,Commission",
            "2025-01-10,b,AAPL,10,5,1"
        ).intents.single()
        assertThat(intent.type).isEqualTo(TradeType.AssetTrade.BUY)
        assertThat(intent.quantity).isEqualTo(Money(aapl, 1000))
        assertThat(intent.price).isEqualTo(BigDecimal("5"))
        assertThat(intent.fee).isEqualTo(Money(home, 100))
        val withValue = parse(
            "Date,Type,Asset,Qty,Value",
            "2025-01-10,s,AAPL,10,50"
        ).intents.single()
        assertThat(withValue.type).isEqualTo(TradeType.AssetTrade.SELL)
        assertThat(withValue.principal).isEqualTo(Money(home, 5000))
        val withPrincipal = parse(
            "Date,Type,Asset,Qty,Principal",
            "2025-01-10,BUY,AAPL,10,60"
        ).intents.single()
        assertThat(withPrincipal.principal).isEqualTo(Money(home, 6000))
    }

    @Test
    fun missingColumnSkipsEveryRowWithoutAWord() {
        val result = parse(
            "Date,Asset,Quantity,Price",
            "2025-01-10,AAPL,10,5"
        )
        // Looks like a bug: the user is told there is no data rather than which column is missing.
        assertThat(result.intents).isEmpty()
        assertThat(result.parseFailures).isEqualTo(0)
        assertThat(result.fatalError).isNull()
    }

    @Test
    fun otherTypesAreFailures() {
        val result = parse(
            "Date,Type,Asset,Quantity,Price",
            "2025-01-10,DIVIDEND,AAPL,10,5",
            "2025-01-10,sell,AAPL,10,5"
        )
        assertThat(result.parseFailures).isEqualTo(1)
        assertThat(result.intents.single().type).isEqualTo(TradeType.AssetTrade.SELL)
    }

    @Test
    fun unknownAssetsAreCountedByCode() {
        val result = parse(
            "Date,Type,Asset,Quantity,Price",
            "2025-01-10,BUY,MSFT,10,5",
            "2025-01-10,BUY,MSFT,10,5",
            "2025-01-10,BUY,aapl,10,5"
        )
        assertThat(result.missingAssets).containsExactly("MSFT", 2, "aapl", 1)
        assertThat(result.intents).isEmpty()
        assertThat(result.parseFailures).isEqualTo(0)
    }

    @Test
    fun unbalancedQuoteEndsTheParseWithAFatalError() {
        val result = parse(
            "Date,Type,Asset,Quantity,Price",
            "2025-01-10,BUY,AAPL,10,5",
            "2025-01-10,BUY,\"AAPL\"x,10,5"
        )
        assertThat(result.intents).hasSize(1)
        assertThat(result.fatalError).isNotNull()
        assertThat(result.fatalErrorLine).isEqualTo(3)
    }

    @Test
    fun missingFileIsAFatalError() {
        val result = runBlocking {
            invokeParser(
                Uri.fromFile(File(folder.root, "missing.csv")),
                listOf(aapl),
                FundingSource.PORTFOLIO,
                null,
                QifDateFormat.YMD,
                RoundingMode.HALF_UP
            )
        }
        assertThat(result.intents).isEmpty()
        assertThat(result.fatalError).isNotNull()
    }

    @Test
    fun onlyAHeaderGivesNothing() {
        val result = parse("Date,Type,Asset,Quantity,Price")
        assertThat(result.intents).isEmpty()
        assertThat(result.parseFailures).isEqualTo(0)
        assertThat(result.fatalError).isNull()
    }

    // Dates

    private fun dateOf(value: String, format: QifDateFormat) = parse(
        "Date,Type,Asset,Quantity,Price",
        "$value,BUY,AAPL,10,5",
        dateFormat = format
    ).intents.single().date

    @Test
    fun dateFollowsTheChosenFormat() {
        val expected = LocalDate.of(2025, 2, 3)
        assertThat(dateOf("02/03/2025", QifDateFormat.US).toLocalDate()).isEqualTo(expected)
        assertThat(dateOf("03/02/2025", QifDateFormat.EU).toLocalDate()).isEqualTo(expected)
        assertThat(dateOf("2025/02/03", QifDateFormat.YMD).toLocalDate()).isEqualTo(expected)
        assertThat(dateOf("03.02.25", QifDateFormat.EU).toLocalDate()).isEqualTo(expected)
        assertThat(dateOf("20250203", QifDateFormat.US).toLocalDate()).isEqualTo(expected)
    }

    @Test
    fun timeOfDayIsKept() {
        val date = dateOf("2025-02-03T14:30:15", QifDateFormat.YMD)
        assertThat(date.toLocalDate()).isEqualTo(LocalDate.of(2025, 2, 3))
        assertThat(date.hour).isEqualTo(14)
        assertThat(date.minute).isEqualTo(30)
        assertThat(date.second).isEqualTo(15)
        assertThat(dateOf("2025-02-03", QifDateFormat.YMD).hour).isEqualTo(0)
    }

    @Test
    fun dateThatDoesNotParseBecomesToday() {
        // Looks like a bug: QifUtils falls back to now, so that the trade lands on the day of the
        // import instead of being reported as a failure.
        assertThat(dateOf("not a date", QifDateFormat.YMD).toLocalDate()).isEqualTo(LocalDate.now())
    }

    @Test
    fun dateInTheWrongFormatRollsOver() {
        // Looks like a bug: the calendar is lenient, month 13 of 2025 is January 2026.
        assertThat(dateOf("13/01/2025", QifDateFormat.US).toLocalDate())
            .isEqualTo(LocalDate.of(2026, 1, 1))
    }

    // Funding and on to the database

    @Test
    fun fundingIsPassedOn() {
        val bank = insertAccount("Bank", currency = HOME)
        val intent = parse(
            "Date,Type,Asset,Quantity,Price",
            "2025-01-10,BUY,AAPL,10,5",
            fundingSource = FundingSource.ACCOUNT,
            fundingAccountId = bank
        ).intents.single()
        assertThat(intent.fundingSource).isEqualTo(FundingSource.ACCOUNT)
        assertThat(intent.peerAccountId).isEqualTo(bank)
    }

    @Test
    fun parsedTradesCanBeSaved() {
        val result = parse(
            "Date,Type,Asset,Quantity,Price,Fee",
            "2025-01-10,BUY,AAPL,10,150,1",
            "2025-01-11,SELL,AAPL,4,160,1"
        )
        save(*result.intents.toTypedArray())
        assertThat(position()).isEqualTo(600)
        assertThat(cash()).isEqualTo(-150100 + 63900)
        assertThat(storedPrice(LocalDate.of(2025, 1, 10))?.toDouble()).isEqualTo(150.0)
        assertThat(storedPrice(LocalDate.of(2025, 1, 11))?.toDouble()).isEqualTo(160.0)
    }

    @Test
    fun laterPriceOfTheSameDayInAFileWins() {
        val result = parse(
            "Date,Type,Asset,Quantity,Price",
            "2025-01-10,BUY,AAPL,10,150",
            "2025-01-10,BUY,AAPL,10,155"
        )
        save(*result.intents.toTypedArray())
        assertThat(storedPrice(LocalDate.of(2025, 1, 10))?.toDouble()).isEqualTo(155.0)
    }
}
