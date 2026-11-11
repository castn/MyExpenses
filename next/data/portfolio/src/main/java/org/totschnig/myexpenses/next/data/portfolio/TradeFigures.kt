package org.totschnig.myexpenses.next.data.portfolio

import java.math.BigDecimal
import java.math.RoundingMode
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/**
 * What can be moved out of the portfolio
 *
 * @param amount in minor units of [currency]
 */
data class Available(val amount: Long, val currency: CurrencyInfo)

/**
 * What the form shows of a draft, in minor units of the portfolio's currency
 *
 * @param principal quantity times price, null while the quantity cannot be read or is too large
 * @param total what is paid or received with the costs, null like [principal]
 * @param isRoundingNecessary the principal has more decimals than the currency, so that the
 * rounding mode matters
 * @param available what can be sold or withdrawn, null for a trade into the portfolio
 * @param isOverLimit more is sold or withdrawn than [available]
 */
data class TradeFigures(
    val principal: Long?,
    val costs: Long,
    val total: Long?,
    val isRoundingNecessary: Boolean,
    val available: Available?,
    val isOverLimit: Boolean
)

/** The asset of this draft among [portfolio]'s holdings and [assets] */
fun TradeDraft.asset(portfolio: Portfolio, assets: List<Asset>): Asset? =
    portfolio.holdings.find { it.asset.code == assetCode }?.asset
        ?: assets.find { it.code == assetCode }

/**
 * The figures of this draft. While a trade is changed, what it moved out counts as available
 * again, what it moved in no longer.
 */
fun TradeDraft.figures(
    portfolio: Portfolio,
    asset: Asset?,
    original: Trade?,
    rounding: RoundingMode
): TradeFigures {
    val digits = portfolio.currency.fractionDigits
    val quantity = parseDecimalText(quantityText)
    val principalRaw = principalRaw(quantity)
    val costs = costsMinor(digits, rounding)
    val principal = principalRaw?.toMinor(digits, rounding)
    val total = principal?.let { if (kind.isIncoming) it + costs else it - costs }
    val available = if (kind.isIncoming) null else available(portfolio, asset, original)
    val isOverLimit = available != null && quantity != null &&
        quantity > BigDecimal.valueOf(available.amount, available.currency.fractionDigits)
    return TradeFigures(
        principal = principal,
        costs = costs,
        total = total,
        isRoundingNecessary = principalRaw != null &&
            principalRaw.movePointRight(digits).stripTrailingZeros().scale() > 0,
        available = available,
        isOverLimit = isOverLimit
    )
}

internal fun TradeDraft.principalRaw(quantity: BigDecimal?): BigDecimal? = quantity?.let {
    if (kind.isCashMovement) it else it.multiply(parseDecimalText(priceText) ?: BigDecimal.ZERO)
}

private fun TradeDraft.costsMinor(digits: Int, rounding: RoundingMode): Long =
    if (kind.isTransfer) {
        0L
    } else {
        costs.sumOf { cost ->
            parseDecimalText(cost.amountText)?.takeIf { it.signum() > 0 }?.toMinor(digits, rounding)
                ?: 0L
        }
    }

private fun TradeDraft.available(
    portfolio: Portfolio,
    asset: Asset?,
    original: Trade?
): Available? {
    val currency = when {
        kind.isCashMovement -> portfolio.currency
        else -> asset?.quantityFormat ?: return null
    }
    val balance = if (kind.isCashMovement) {
        portfolio.cashBalance
    } else {
        portfolio.holdings.find { it.asset.code == asset?.code }?.balance ?: 0L
    }
    val counted = original?.takeIf {
        it.kind.isCashMovement == kind.isCashMovement &&
            (kind.isCashMovement || it.asset.code == asset?.code)
    }?.let { if (it.kind.isIncoming) -it.quantity else it.quantity } ?: 0L
    return Available(balance + counted, currency)
}

internal fun BigDecimal.toMinor(digits: Int, rounding: RoundingMode): Long? = runCatching {
    setScale(digits, rounding).movePointRight(digits).longValueExact()
}.getOrNull()
