package org.totschnig.myexpenses.next.portfolio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity
import org.totschnig.myexpenses.compose.transactions.Action
import org.totschnig.myexpenses.next.NextFeatures
import org.totschnig.myexpenses.next.NextTheme
import org.totschnig.myexpenses.next.data.portfolio.TradeKind
import org.totschnig.myexpenses.next.feature.portfolio.TradeEditorFlow
import org.totschnig.myexpenses.next.feature.portfolio.TradeStart
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_ROWID

/**
 * The screen of a trade, see [TradeEditorFlow]: a new one from the plus button of a portfolio, and
 * changing one from the page of the portfolio or the details of its transaction. An activity of its
 * own, like the form of a transaction, so that it is opened alike from everywhere.
 */
class TradeEditorActivity : ProtectedFragmentActivity() {

    private val portfolio by lazy { NextFeatures(this).portfolio }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tradeId = intent.getLongExtra(KEY_ROWID, 0L)
        val start = if (tradeId > 0L) {
            TradeStart.Edit(tradeId)
        } else {
            TradeStart.New(
                portfolioId = intent.getLongExtra(KEY_ACCOUNTID, 0L),
                kind = intent.getStringExtra(EXTRA_KIND).let { name ->
                    TradeKind.entries.firstOrNull { it.name == name } ?: TradeKind.BUY
                }
            )
        }
        setContent {
            NextTheme {
                TradeEditorFlow(portfolio.editor, start, onClose = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_KIND = "tradeKind"

        /** A new trade in portfolio [portfolioId] for [action] of its plus button */
        fun newTrade(context: Context, portfolioId: Long, action: Action): Intent =
            Intent(context, TradeEditorActivity::class.java)
                .putExtra(KEY_ACCOUNTID, portfolioId)
                .putExtra(
                    EXTRA_KIND,
                    when (action) {
                        Action.Sell -> TradeKind.SELL
                        Action.Deposit -> TradeKind.DEPOSIT
                        Action.Withdraw -> TradeKind.WITHDRAW
                        Action.AssetTransfer -> TradeKind.TRANSFER_OUT
                        else -> TradeKind.BUY
                    }.name
                )

        /** Changing the trade of transaction [transactionId] */
        fun edit(context: Context, transactionId: Long): Intent =
            Intent(context, TradeEditorActivity::class.java).putExtra(KEY_ROWID, transactionId)
    }
}
