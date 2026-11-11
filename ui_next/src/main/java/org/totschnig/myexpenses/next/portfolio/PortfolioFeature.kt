package org.totschnig.myexpenses.next.portfolio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.text.DecimalFormatSymbols
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.feature.portfolio.PortfolioTradesFlow
import org.totschnig.myexpenses.next.feature.portfolio.PortfolioTradesViewModel
import org.totschnig.myexpenses.next.feature.portfolio.TradeEditViewModel
import org.totschnig.myexpenses.next.viewModelsOf

/**
 * Portfolios: the trades on the page of a portfolio and their form, [TradeEditorActivity]
 */
class PortfolioFeature(activity: ComponentActivity) {
    private val store by lazy {
        val injector = activity.injector
        RepositoryTradeStore(
            activity.applicationContext,
            injector.repository(),
            injector.preferencesDataStore()
        )
    }

    val trades by activity.viewModelsOf { PortfolioTradesViewModel(store) }

    val editor by activity.viewModelsOf {
        TradeEditViewModel(store, it, DecimalFormatSymbols.getInstance().decimalSeparator)
    }

    /**
     * The trades of portfolio [portfolioId] on its page
     *
     * @param onEdit with the id of the trade
     * @param onDelete with the id of the trade, to be confirmed
     */
    @Composable
    fun TradesPage(
        portfolioId: Long,
        onEdit: (Long) -> Unit,
        onDelete: (Long) -> Unit,
        modifier: Modifier = Modifier,
        contentPadding: PaddingValues = PaddingValues()
    ) {
        PortfolioTradesFlow(trades, portfolioId, onEdit, onDelete, modifier, contentPadding)
    }
}
