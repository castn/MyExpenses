package org.totschnig.myexpenses.next.contracts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.util.convAmount
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Marks a contract with unread news
 */
@Composable
internal fun NewsDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
    )
}

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
            if (isIncome) R.string.next_news_income_after_cancellation else R.string.next_news_payment_after_cancellation,
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
                    this.amount > previous -> R.string.next_news_price_up
                    else -> R.string.next_news_price_down
                },
                formatter.convAmount(previous, currency),
                amount
            )
        }
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
        NewsDot()
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
 * @param onCancelAgain for news of a payment after cancellation, if the contract is still running
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
            val afterCancellation = news.firstOrNull { it.type == ContractNews.Type.PAYMENT_AFTER_CANCELLATION }
            if (afterCancellation != null && onCancelAgain != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onCancelAgain) {
                        Text(
                            stringResource(
                                if (afterCancellation.isIncome) R.string.next_news_income_cancel_again
                                else R.string.next_news_cancel_again
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
