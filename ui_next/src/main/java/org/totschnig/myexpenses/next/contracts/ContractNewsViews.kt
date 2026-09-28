package org.totschnig.myexpenses.next.contracts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.Dot
import org.totschnig.myexpenses.designsystem.GroupPosition
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.Shapes
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.util.convAmount

/**
 * What happened, in one sentence
 */
@Composable
internal fun ContractNews.text(currency: CurrencyUnit): String {
    val formatter = LocalCurrencyFormatter.current
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val amount = formatter.convAmount(amount, currency)
    return when (type) {
        ContractNews.Type.PAYMENT_AFTER_CANCELLATION -> stringResource(
            when {
                isIncome -> R.string.next_news_income_after_cancellation
                isReserve -> R.string.next_news_reserve_after_cancellation
                else -> R.string.next_news_payment_after_cancellation
            },
            amount,
            dateFormatter.format(date),
            cancelledOn?.let { dateFormatter.format(it) } ?: "–"
        )

        ContractNews.Type.PRICE_CHANGE -> {
            val previous = previousAmount ?: 0
            stringResource(
                when {
                    isIncome && this.amount > previous -> R.string.next_news_income_up
                    isIncome -> R.string.next_news_income_down
                    isReserve && this.amount > previous -> R.string.next_news_reserve_up
                    isReserve -> R.string.next_news_reserve_down
                    this.amount > previous -> R.string.next_news_price_up
                    else -> R.string.next_news_price_down
                },
                formatter.convAmount(previous, currency),
                amount
            )
        }

        ContractNews.Type.NEW_CONTRACT -> stringResource(
            when {
                isIncome -> R.string.next_news_new_income
                isReserve -> R.string.next_news_new_reserve
                else -> R.string.next_news_new_contract
            },
            amount,
            interval?.let { stringResource(it.labelRes) } ?: "–"
        )

        ContractNews.Type.PAYMENT_MISSING -> stringResource(
            when {
                isIncome -> R.string.next_news_income_missing
                isReserve -> R.string.next_news_reserve_missing
                else -> R.string.next_news_payment_missing
            },
            dateFormatter.format(date),
            amount
        )

        ContractNews.Type.CONTRACT_STOPPED -> stringResource(
            when {
                isIncome -> R.string.next_news_income_stopped
                isReserve -> R.string.next_news_reserve_stopped
                else -> R.string.next_news_contract_stopped
            },
            dateFormatter.format(date)
        )
    }
}

/**
 * Unread news at the top of the list of contracts
 *
 * @param onOpen opens the contract of a news, which marks it as read
 */
@Composable
internal fun NewsSection(
    news: List<ContractNews>,
    currency: CurrencyUnit,
    onOpen: (ContractNews) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${stringResource(R.string.next_news)} (${news.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onMarkAllRead) {
            Text(stringResource(R.string.next_news_mark_all_read))
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            news.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                NewsRow(item, currency, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun NewsRow(news: ContractNews, currency: CurrencyUnit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot()
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                news.contractName.ifEmpty { "–" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                news.text(currency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * News of a contract at the top of its details
 *
 * @param onCancelAgain for news suggesting that the contract ended, if it is not cancelled
 */
@Composable
internal fun NewsBanner(
    news: List<ContractNews>,
    currency: CurrencyUnit,
    onCancelAgain: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.next_news),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            news.forEach {
                Text(
                    it.text(currency),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // News that suggest the contract has ended, the user confirms with one tap
            val cancelling = news.firstOrNull {
                it.type == ContractNews.Type.PAYMENT_AFTER_CANCELLATION || it.type == ContractNews.Type.CONTRACT_STOPPED
            }
            if (cancelling != null && onCancelAgain != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onCancelAgain) {
                        Text(
                            stringResource(
                                if (cancelling.type == ContractNews.Type.CONTRACT_STOPPED) when {
                                    cancelling.isIncome -> R.string.next_income_cancel
                                    cancelling.isReserve -> R.string.next_reserve_cancel
                                    else -> R.string.next_contracts_cancel
                                } else when {
                                    cancelling.isIncome -> R.string.next_news_income_cancel_again
                                    cancelling.isReserve -> R.string.next_news_reserve_cancel_again
                                    else -> R.string.next_news_cancel_again
                                }
                            )
                        )
                    }
                }
            } else {
                Box(Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

/**
 * All news of the list, newest first, also those read already
 *
 * @param onOpen opens the contract of a news, which marks it as read
 */
@Composable
internal fun NewsHistoryScreen(
    news: List<ContractNews>,
    currency: CurrencyUnit,
    onBack: () -> Unit,
    onOpen: (ContractNews) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.next_back))
            }
            Text(
                stringResource(R.string.next_news_history),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        if (news.isEmpty()) {
            Text(
                stringResource(R.string.next_news_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(32.dp)
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            itemsIndexed(news, key = { _, item -> item.id }) { index, item ->
                Surface(
                    shape = Shapes.groupItem(GroupPosition.of(index, news.size)),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(item) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Keeps the texts aligned, whether read or not
                            Box(Modifier.size(8.dp)) { if (!item.isRead) Dot() }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        item.contractName.ifEmpty { "–" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        dateFormatter.format(item.date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                Text(
                                    item.text(currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * All news of a contract, in its details
 */
@Composable
internal fun NewsHistorySection(news: List<ContractNews>, currency: CurrencyUnit) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    SectionTitle(stringResource(R.string.next_news_history))
    DetailCard {
        news.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    dateFormatter.format(item.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(item.text(currency), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

