package org.totschnig.myexpenses.next.data.portfolio

import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/**
 * A cost of a trade as typed
 *
 * @param amountText in the currency of the portfolio, empty while not typed
 */
data class CostDraft(val amountText: String, val category: CostCategory?) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * What is typed in the form of a trade, kept as typed and [Serializable] for the saved state
 *
 * @param id of the trade changed, [NEW] for a new one
 * @param assetCode the asset bought, sold or moved, not used by a cash movement
 * @param quantityText of the asset, the amount of a cash movement
 * @param priceText of one unit of the asset in the currency of the portfolio
 * @param peerAccountId the account of [Funding.ACCOUNT] or the other portfolio of a transfer
 * @param linkedTransactionId a transaction of the funding account taken as the other side
 */
data class TradeDraft(
    val id: Long,
    val portfolioId: Long,
    val kind: TradeKind,
    val date: LocalDateTime,
    val assetCode: String?,
    val quantityText: String,
    val priceText: String,
    val costs: List<CostDraft>,
    val funding: Funding,
    val peerAccountId: Long?,
    val comment: String,
    val linkedTransactionId: Long?
) : Serializable {
    val isNew get() = id == NEW

    companion object {
        const val NEW = 0L
        private const val serialVersionUID = 1L
    }
}

/**
 * The draft of a new trade of [kind] in [portfolio]: of the first asset held, with its latest
 * price, and a cost for each of [feeCategories], the categories of the costs of the trade saved last
 */
fun newTradeDraft(
    portfolio: Portfolio,
    kind: TradeKind,
    now: LocalDateTime,
    feeCategories: List<CostCategory>,
    decimalSeparator: Char
): TradeDraft {
    val holding = portfolio.holdings.firstOrNull()
    return TradeDraft(
        id = TradeDraft.NEW,
        portfolioId = portfolio.id,
        kind = kind,
        date = now,
        assetCode = holding?.asset?.code,
        quantityText = "",
        priceText = holding?.price?.takeIf { kind.movesAsset }?.let {
            formatDecimalText(it, decimalSeparator)
        }.orEmpty(),
        costs = if (kind.isTransfer) emptyList() else feeCategories.map { CostDraft("", it) },
        funding = Funding.EXTERNAL,
        peerAccountId = null,
        comment = "",
        linkedTransactionId = null
    )
}

/**
 * The draft for changing [this]. The peer of a trade is its funding account when it is one of
 * [fundingAccounts], otherwise the cash of the portfolio.
 */
fun Trade.toDraft(
    fundingAccounts: List<AccountChoice>,
    portfolioCurrency: CurrencyInfo,
    decimalSeparator: Char
) = TradeDraft(
    id = id,
    portfolioId = portfolioId,
    kind = kind,
    date = date,
    assetCode = asset.code,
    quantityText = formatMinorText(
        quantity,
        if (kind.isCashMovement) portfolioCurrency.fractionDigits else asset.fractionDigits,
        decimalSeparator
    ),
    priceText = if (kind.isCashMovement) "" else formatDecimalText(price, decimalSeparator),
    costs = costs.map {
        CostDraft(
            formatMinorText(it.amount, portfolioCurrency.fractionDigits, decimalSeparator),
            it.category
        )
    },
    funding = when {
        kind.isTransfer -> Funding.EXTERNAL
        peer == null -> Funding.EXTERNAL
        fundingAccounts.any { it.id == peer.id } -> Funding.ACCOUNT
        else -> Funding.PORTFOLIO
    },
    peerAccountId = peer?.id,
    comment = comment,
    linkedTransactionId = null
)

/**
 * The draft after "save and new": kind, day, asset and funding stay for the next trade, the costs
 * start again with [feeCategories]
 */
fun TradeDraft.next(feeCategories: List<CostCategory>) = copy(
    id = TradeDraft.NEW,
    quantityText = "",
    priceText = "",
    costs = if (kind.isTransfer) emptyList() else feeCategories.map { CostDraft("", it) },
    comment = "",
    linkedTransactionId = null
)

/**
 * Whether the draft can be saved: its quantity can be read, an asset is chosen where it moves one
 * and the other portfolio of a transfer
 */
val TradeDraft.canSave: Boolean
    get() = parseDecimalText(quantityText) != null &&
        (!kind.movesAsset || assetCode != null) &&
        (!kind.isTransfer || peerAccountId != null)

/**
 * The values of this draft, null if it cannot be saved or a number is too large
 *
 * @param asset of [TradeDraft.assetCode], see [asset]
 */
fun TradeDraft.toValues(portfolio: Portfolio, asset: Asset?, rounding: RoundingMode): TradeValues? {
    val traded = if (kind.movesAsset) asset else portfolio.currency.asCash
    if (!canSave || traded == null) return null
    val quantity = parseDecimalText(quantityText) ?: return null
    val costs = costLegs(portfolio.currency.fractionDigits, rounding) ?: return null
    val quantityMinor = quantity.toMinor(traded.fractionDigits, rounding)
    val principal = principalRaw(quantity)?.toMinor(portfolio.currency.fractionDigits, rounding)
    val price = when {
        kind.movesAsset -> parseDecimalText(priceText) ?: BigDecimal.ZERO
        else -> BigDecimal.ONE
    }
    return if (quantityMinor == null || principal == null) {
        null
    } else {
        TradeValues(
            kind = kind,
            assetCode = traded.code,
            date = date,
            quantity = quantityMinor,
            price = price,
            principal = principal,
            funding = funding,
            peerAccountId = peerAccountId,
            costs = costs,
            comment = comment.trim(),
            linkedTransactionId = linkedTransactionId.takeIf { funding == Funding.ACCOUNT },
            tradeId = id.takeIf { it != TradeDraft.NEW }
        )
    }
}

/** The cash of a portfolio as the asset of a deposit or withdrawal */
private val CurrencyInfo.asCash get() = Asset(code, code, fractionDigits, AssetKind.SECURITY)

/** The costs typed with an amount, none for a transfer; null if an amount is too large */
private fun TradeDraft.costLegs(digits: Int, rounding: RoundingMode): List<CostLeg>? {
    if (kind.isTransfer) return emptyList()
    val amounts = costs.mapNotNull { cost ->
        val typed = parseDecimalText(cost.amountText)?.takeIf { it.signum() > 0 }
        typed?.let { cost to it.toMinor(digits, rounding) }
    }
    if (amounts.any { it.second == null }) return null
    return amounts.mapNotNull { (cost, amount) -> amount?.let { CostLeg(it, cost.category) } }
}
