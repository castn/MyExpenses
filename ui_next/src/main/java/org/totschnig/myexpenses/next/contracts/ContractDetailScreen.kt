package org.totschnig.myexpenses.next.contracts

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.util.convAmount
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.totschnig.myexpenses.compose.Icon as CategoryIcon

/**
 * Details of one contract: amounts, next debit, the debits it was detected from and
 * further information taken from the last debit.
 */
@Composable
fun ContractDetailScreen(
    contract: Contract,
    currency: CurrencyUnit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    fun debit(amount: Long) = "− " + formatter.convAmount(amount, currency)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.next_back)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            DetailCard {
                Header(contract)
                InfoRow(stringResource(contract.interval.labelRes), debit(contract.lastAmount))
                contract.previousAmount?.takeIf { it != contract.lastAmount }?.let {
                    InfoRow(stringResource(R.string.next_contracts_previous_amount), debit(it))
                }
                InfoRow(
                    stringResource(R.string.next_contracts_total),
                    debit(contract.transactions.sumOf { -it.amount })
                )
                if (contract.isActive) {
                    InfoRow(
                        stringResource(R.string.next_contracts_next_debit),
                        dateFormatter.format(contract.nextExpectedDate)
                    )
                } else {
                    InfoRow(
                        stringResource(R.string.next_contracts_last_debit),
                        dateFormatter.format(contract.lastDate)
                    )
                }
            }

            SectionTitle(
                pluralStringResource(
                    R.plurals.next_contracts_based_on,
                    contract.transactions.size,
                    contract.transactions.size
                ),
                color = MaterialTheme.colorScheme.tertiary
            )
            DetailCard {
                contract.transactions.asReversed().forEachIndexed { index, transaction ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    }
                    TransactionRow(
                        date = dateFormatter.format(transaction.date),
                        account = transaction.accountLabel,
                        amount = debit(-transaction.amount)
                    )
                }
            }

            SectionTitle(stringResource(R.string.next_contracts_details))
            DetailCard {
                InfoRow(
                    stringResource(R.string.next_contracts_account),
                    contract.transactions.mapNotNull { it.accountLabel }.distinct().joinToString()
                        .ifEmpty { "–" },
                    isFirst = true
                )
                contract.lastTransaction.comment?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(stringResource(R.string.next_contracts_purpose), it)
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionTitle(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun Header(contract: Contract) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            val icon = contract.categoryIcon
            if (icon != null) {
                CategoryIcon(icon, size = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                contract.displayName.ifEmpty { "–" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            contract.categoryPath?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Label on the left, value on the right, separated from the row above by a divider
 */
@Composable
private fun InfoRow(label: String, value: String, isFirst: Boolean = false) {
    if (!isFirst) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
    }
}

@Composable
private fun TransactionRow(date: String, account: String?, amount: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, style = MaterialTheme.typography.bodyLarge)
            account?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            amount,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 900)
@Preview(name = "Dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContractDetailScreenPreview() {
    val last = LocalDate.now().minusDays(5)
    val amounts = listOf(3499L, 3499, 3499, 3999, 3999, 3999, 3999)
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        ContractDetailScreen(
            contract = Contract(
                signature = "p1|MONTHLY",
                name = "Vodafone",
                interval = ContractInterval.MONTHLY,
                transactions = amounts.mapIndexed { i, amount ->
                    ContractTransaction(
                        id = i.toLong(),
                        date = last.minus(Period.ofMonths(amounts.size - 1 - i)),
                        amount = -amount,
                        accountId = 1,
                        accountLabel = "Girokonto",
                        comment = "Kundennr. 0889797",
                        categoryPath = "Wohnen > Internet & Telefon"
                    )
                },
                nextExpectedDate = last.plusMonths(1),
                isActive = true
            ),
            currency = CurrencyUnit.DebugInstance,
            onBack = {}
        )
    }
}
