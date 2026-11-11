package org.totschnig.myexpenses.next.feature.portfolio

import androidx.annotation.StringRes
import java.math.RoundingMode
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.portfolio.AssetKind
import org.totschnig.myexpenses.next.data.portfolio.TradeKind

/** The name of [kind] in the list of trades and as a choice of the form, a transfer in either direction alike */
@get:StringRes
internal val TradeKind.label
    get() = when (this) {
        TradeKind.BUY -> R.string.next_trade_buy
        TradeKind.SELL -> R.string.next_trade_sell
        TradeKind.DEPOSIT -> R.string.next_trade_deposit
        TradeKind.WITHDRAW -> R.string.next_trade_withdraw
        TradeKind.TRANSFER_IN, TradeKind.TRANSFER_OUT -> R.string.next_trade_transfer
    }

@get:StringRes
internal val RoundingMode.label
    get() = when (this) {
        RoundingMode.UP -> R.string.next_trade_rounding_up
        RoundingMode.DOWN -> R.string.next_trade_rounding_down
        RoundingMode.HALF_DOWN -> R.string.next_trade_rounding_half_down
        RoundingMode.HALF_EVEN -> R.string.next_trade_rounding_half_even
        else -> R.string.next_trade_rounding_half_up
    }

@get:StringRes
internal val AssetKind.label
    get() = when (this) {
        AssetKind.SECURITY -> R.string.next_asset_kind_security
        AssetKind.CRYPTO -> R.string.next_asset_kind_crypto
        AssetKind.COMMODITY -> R.string.next_asset_kind_commodity
    }

/** The direction of a transfer, seen from the portfolio */
@get:StringRes
internal val TradeKind.directionLabel
    get() = when (this) {
        TradeKind.TRANSFER_IN -> R.string.next_trade_transfer_in
        else -> R.string.next_trade_transfer_out
    }
