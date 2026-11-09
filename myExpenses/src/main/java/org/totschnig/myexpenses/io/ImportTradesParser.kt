package org.totschnig.myexpenses.io

import android.content.Context
import android.net.Uri
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.epochMillis2LocalDateTime
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FundingSource
import org.totschnig.myexpenses.viewmodel.data.TradeIntent
import org.totschnig.myexpenses.viewmodel.data.TradeType

data class ImportTradesParseResult(
    val intents: List<TradeIntent>,
    val parseFailures: Int,
    val missingAssets: Map<String, Int>,
    val fatalError: String? = null,
    val fatalErrorLine: Long = 0
)

object ImportTradesParser {
    private const val PRICE_SCALE = 10

    /**
     * Reads a CSV file of trades with a header row (Date, Type, Asset and quantity, price or total)
     * into trade intents for [portfolio]. Rows with unknown assets or values are counted, not
     * imported.
     */
    suspend fun parse(
        context: Context,
        uri: Uri,
        portfolio: FullAccount,
        assets: List<CurrencyUnit>,
        fundingSource: FundingSource,
        fundingAccountId: Long?,
        dateFormat: ImportDateFormat,
        roundingMode: RoundingMode
    ): ImportTradesParseResult = withContext(Dispatchers.IO) {
        val intents = mutableListOf<TradeIntent>()
        val assetMap = assets.associateBy { it.code }
        var parseFailures = 0
        val missingAssets = mutableMapOf<String, Int>()
        var fatalError: String? = null
        var fatalErrorLine: Long = 0

        fun money(unit: CurrencyUnit, amount: BigDecimal) =
            Money.buildWithMajor(unit, amount, roundingMode).getOrThrow()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val format = CSVFormat.DEFAULT.builder()
                    .setIgnoreSurroundingSpaces(true)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .get()
                val parser = CSVParser.parse(reader, format)

                val iterator = parser.iterator()
                while (true) {
                    val record = try {
                        if (!iterator.hasNext()) break
                        iterator.next()
                    } catch (e: Exception) {
                        fatalError = e.message
                        fatalErrorLine = parser.currentLineNumber
                        break
                    }
                    try {
                        val dateStr = record.getSafe("Date") ?: continue
                        val typeStr = record.getSafe("Type") ?: continue
                        val assetCode = record.getSafe("Asset") ?: continue

                        val asset = assetMap[assetCode]
                        if (asset == null) {
                            missingAssets[assetCode] = (missingAssets[assetCode] ?: 0) + 1
                            continue
                        }

                        val date = ImportUtils.parseDate(dateStr, dateFormat)
                        val type = when (typeStr.uppercase()) {
                            "BUY", "B" -> TradeType.AssetTrade.BUY

                            "SELL", "S" -> TradeType.AssetTrade.SELL

                            else -> {
                                parseFailures++
                                continue
                            }
                        }

                        val quantity = record.getSafe("Quantity", "Qty")?.toBigDecimalOrNull()
                        val price = record.getSafe("Price", "Unit Price")?.toBigDecimalOrNull()
                        val total =
                            record.getSafe("Total", "Principal", "Value")?.toBigDecimalOrNull()
                        val fee = record.getSafe("Fee", "Commission")?.toBigDecimalOrNull()
                            ?: BigDecimal.ZERO

                        val finalQuantity: Money
                        val finalPrice: BigDecimal
                        val finalPrincipal: Money

                        when {
                            quantity != null && price != null -> {
                                finalQuantity = money(asset, quantity)
                                finalPrice = price
                                finalPrincipal =
                                    money(portfolio.currencyUnit, quantity.multiply(price))
                            }

                            quantity != null && total != null && quantity.signum() != 0 -> {
                                finalQuantity = money(asset, quantity)
                                finalPrice = total.divide(quantity, PRICE_SCALE, roundingMode)
                                finalPrincipal = money(portfolio.currencyUnit, total)
                            }

                            price != null && total != null && price.signum() != 0 -> {
                                finalQuantity =
                                    money(asset, total.divide(price, PRICE_SCALE, roundingMode))
                                finalPrice = price
                                finalPrincipal = money(portfolio.currencyUnit, total)
                            }

                            else -> {
                                parseFailures++
                                continue
                            }
                        }

                        intents.add(
                            TradeIntent(
                                targetAsset = asset,
                                type = type,
                                date = epochMillis2LocalDateTime(date.time),
                                quantity = finalQuantity,
                                price = finalPrice,
                                principal = finalPrincipal,
                                fundingSource = fundingSource,
                                peerAccountId = fundingAccountId,
                                fee = money(portfolio.currencyUnit, fee)
                            )
                        )
                    } catch (_: Exception) {
                        parseFailures++
                    }
                }
            }
        } catch (e: Exception) {
            if (fatalError == null) {
                fatalError = e.message
            }
            CrashHandler.report(e)
        }
        ImportTradesParseResult(intents, parseFailures, missingAssets, fatalError, fatalErrorLine)
    }

    private fun CSVRecord.getSafe(vararg names: String): String? {
        for (name in names) {
            if (isMapped(name)) return get(name)
            // Also check case-insensitive
            val mappedName = parser.headerNames.find { it.equals(name, ignoreCase = true) }
            if (mappedName != null) return get(mappedName)
        }
        return null
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = try {
        BigDecimal(this)
    } catch (_: Exception) {
        null
    }
}
