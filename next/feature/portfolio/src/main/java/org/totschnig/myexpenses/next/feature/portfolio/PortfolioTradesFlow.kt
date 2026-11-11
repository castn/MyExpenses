package org.totschnig.myexpenses.next.feature.portfolio

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import java.time.format.FormatStyle
import org.totschnig.myexpenses.designsystem.ActionItemRow
import org.totschnig.myexpenses.designsystem.ActionSheet
import org.totschnig.myexpenses.designsystem.EmptyState
import org.totschnig.myexpenses.designsystem.GroupItem
import org.totschnig.myexpenses.designsystem.GroupPosition
import org.totschnig.myexpenses.designsystem.LoadingBlock
import org.totschnig.myexpenses.designsystem.SheetAction
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.core.ui.rememberNumberFormat
import org.totschnig.myexpenses.next.data.portfolio.Trade
import org.totschnig.myexpenses.next.data.portfolio.cashAmount

/**
 * The trades of portfolio [portfolioId] on its page, the latest first. A tap changes a trade, its
 * actions change or delete it; a closed portfolio only shows them.
 *
 * @param onEdit with the id of the trade
 * @param onDelete with the id of the trade, asked for by the app
 */
@Composable
fun PortfolioTradesFlow(
    viewModel: PortfolioTradesViewModel,
    portfolioId: Long,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val content by viewModel.trades(portfolioId).collectAsStateWithLifecycle()
    PortfolioTradeList(content, onEdit, onDelete, modifier, contentPadding)
}

@Composable
private fun PortfolioTradeList(
    content: PortfolioTrades?,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues
) {
    var actionsOf by rememberSaveable { mutableStateOf<Long?>(null) }
    when {
        content == null -> LoadingBlock(modifier.padding(Spacing.screen).fillMaxWidth())

        content.trades.isEmpty() -> EmptyState(
            title = stringResource(R.string.next_trades_empty),
            hint = if (content.portfolio.isSealed) {
                null
            } else {
                stringResource(
                    R.string.next_trades_empty_hint
                )
            },
            modifier = modifier
        )

        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            itemsIndexed(content.trades, key = { _, trade -> trade.id }) { index, trade ->
                GroupItem(
                    GroupPosition.of(index, content.trades.size),
                    Modifier.padding(horizontal = Spacing.screen)
                ) {
                    TradeRow(
                        trade,
                        content,
                        onClick = { if (!content.portfolio.isSealed) onEdit(trade.id) },
                        onActions = { actionsOf = trade.id }
                    )
                }
            }
        }
    }
    val trade = content?.trades?.find { it.id == actionsOf }
    if (trade != null && !content.portfolio.isSealed) {
        ActionSheet(
            actions = listOf(
                SheetAction(stringResource(R.string.next_trade_edit)) { onEdit(trade.id) },
                SheetAction(stringResource(R.string.next_trade_delete)) { onDelete(trade.id) }
            ),
            onDismiss = { actionsOf = null },
            title = "${stringResource(trade.kind.label)} ${trade.asset.label}"
        )
    }
}

/** Kind and asset, quantity times price and the day, the principal; the costs where there are */
@Composable
private fun TradeRow(
    trade: Trade,
    content: PortfolioTrades,
    onClick: () -> Unit,
    onActions: () -> Unit
) {
    val currency = content.portfolio.currency
    val formatAmount = rememberAmountFormat(currency)
    val formatNumber = rememberNumberFormat()
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)
    val details = buildList {
        if (trade.kind.movesAsset) {
            add(
                stringResource(
                    R.string.next_trade_quantity_price,
                    formatNumber(BigDecimal.valueOf(trade.quantity, trade.asset.fractionDigits)),
                    "${formatNumber(trade.price)} ${currency.symbol}"
                )
            )
        }
        trade.peer?.let { add(it.label) }
        add(dateFormatter.format(trade.date))
        val costs = stringResource(R.string.next_trade_costs)
        if (trade.totalCosts != 0L) add("$costs ${formatAmount(trade.totalCosts)}")
        trade.comment.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    ActionItemRow(
        title = if (trade.kind.movesAsset) {
            "${stringResource(trade.kind.label)} ${trade.asset.label}"
        } else {
            stringResource(trade.kind.label)
        },
        value = formatAmount(trade.cashAmount),
        actionsLabel = stringResource(R.string.next_trade_actions),
        onClick = onClick,
        onActions = onActions,
        supportingText = details.joinToString(" · "),
        isFirst = true,
        onClickLabel = stringResource(R.string.next_trade_edit)
    )
}
