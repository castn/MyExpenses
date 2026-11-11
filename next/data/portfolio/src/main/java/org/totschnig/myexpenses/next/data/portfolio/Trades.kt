package org.totschnig.myexpenses.next.data.portfolio

import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/**
 * What a trade does in a portfolio
 *
 * @param isIncoming the asset or the cash comes into the portfolio
 */
enum class TradeKind(val isIncoming: Boolean) {
    BUY(true),
    SELL(false),
    DEPOSIT(true),
    WITHDRAW(false),

    /** An asset moved into the portfolio from another portfolio */
    TRANSFER_IN(true),

    /** An asset moved from the portfolio into another portfolio */
    TRANSFER_OUT(false);

    val isCashMovement get() = this == DEPOSIT || this == WITHDRAW

    val isTransfer get() = this == TRANSFER_IN || this == TRANSFER_OUT

    /** Buys, sells or moves an asset, the others move cash */
    val movesAsset get() = !isCashMovement
}

/** Where the money of a trade comes from or goes to */
enum class Funding {
    /** The cash account of the portfolio */
    PORTFOLIO,

    /** Not tracked in the app, no balance changes besides the portfolio's */
    EXTERNAL,

    /** An account of the app in the currency of the portfolio */
    ACCOUNT
}

/** What kind of asset is traded */
enum class AssetKind { SECURITY, CRYPTO, COMMODITY }

/**
 * A security or crypto currency that can be traded
 *
 * @param fractionDigits of its quantities, e.g. 0 for whole shares
 */
data class Asset(
    val code: String,
    val label: String,
    val fractionDigits: Int,
    val kind: AssetKind
) {
    /** To format quantities, without a symbol */
    val quantityFormat get() = CurrencyInfo(code, "", fractionDigits)
}

/**
 * An asset held in a portfolio, in an account of its own
 *
 * @param balance quantity in minor units of the asset
 * @param price of one unit in the currency of the portfolio, the latest one known, null if none
 */
data class Holding(val accountId: Long, val asset: Asset, val balance: Long, val price: BigDecimal?)

/**
 * A portfolio with its cash and its holdings
 *
 * @param cashBalance in minor units of [currency]
 * @param rounding how the principal of its trades is rounded to its currency
 */
data class Portfolio(
    val id: Long,
    val label: String,
    val currency: CurrencyInfo,
    val isSealed: Boolean,
    val cashBalance: Long,
    val holdings: List<Holding>,
    val rounding: RoundingMode = RoundingMode.HALF_UP
)

/** An account a trade can be funded from or moved to */
data class AccountChoice(val id: Long, val label: String)

/**
 * The accounts a trade of a portfolio can use
 *
 * @param funding open accounts in its currency, without portfolios
 * @param otherPortfolios to move an asset to or from
 */
data class TradeAccounts(val funding: List<AccountChoice>, val otherPortfolios: List<AccountChoice>)

/** A category a cost of a trade is booked on */
data class CostCategory(val id: Long, val path: String) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * A cost of a trade, e.g. a fee or a tax
 *
 * @param amount in minor units of the portfolio's currency, positive
 * @param category null to book it without a category
 */
data class CostLeg(val amount: Long, val category: CostCategory?)

/**
 * A trade as it is saved
 *
 * @param quantity in minor units of the asset, of the portfolio's currency for a cash movement
 * @param price of one unit of the asset, 1 for a cash movement
 * @param principal quantity times price in minor units of the portfolio's currency
 * @param peerAccountId the account of [Funding.ACCOUNT] or the other portfolio of a transfer
 * @param linkedTransactionId a transaction of the funding account that is the other side of the
 * trade, e.g. from the bank import, instead of creating one
 * @param tradeId the trade changed, null for a new one
 */
data class TradeValues(
    val kind: TradeKind,
    val assetCode: String,
    val date: LocalDateTime,
    val quantity: Long,
    val price: BigDecimal,
    val principal: Long,
    val funding: Funding,
    val peerAccountId: Long?,
    val costs: List<CostLeg>,
    val comment: String,
    val linkedTransactionId: Long?,
    val tradeId: Long?
)

/**
 * A trade of a portfolio as it is shown and changed
 *
 * @param id of the transaction of the trade in the portfolio
 * @param quantity in minor units of [asset]
 * @param principal in minor units of the portfolio's currency
 * @param peer the funding account or the other portfolio, see [TradeValues.peerAccountId]
 */
data class Trade(
    val id: Long,
    val portfolioId: Long,
    val kind: TradeKind,
    val date: LocalDateTime,
    val asset: Asset,
    val quantity: Long,
    val price: BigDecimal,
    val principal: Long,
    val costs: List<CostLeg>,
    val peer: AccountChoice?,
    val comment: String
) {
    /** All costs in minor units */
    val totalCosts get() = costs.sumOf { it.amount }
}

/**
 * A transaction of the funding account that may be the other side of a trade
 *
 * @param amount in minor units, negative for an expense
 */
data class MatchingTransaction(
    val id: Long,
    val label: String?,
    val amount: Long,
    val day: LocalDate
)

/**
 * What [this] does to the money of the portfolio in minor units: negative for a buy or a
 * withdrawal; the value moved for a transfer, negative out of the portfolio
 */
val Trade.cashAmount: Long
    get() = when (kind) {
        TradeKind.SELL, TradeKind.DEPOSIT, TradeKind.TRANSFER_IN -> principal
        TradeKind.BUY, TradeKind.WITHDRAW, TradeKind.TRANSFER_OUT -> -principal
    }
