package org.totschnig.myexpenses.next.contracts

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.util.convAmount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.totschnig.myexpenses.compose.Icon as CategoryIcon

private enum class ContractsTab(@param:StringRes val labelRes: Int) {
    Contracts(R.string.next_tab_contracts)
}

private val CardShape = RoundedCornerShape(16.dp)

/**
 * Contracts detected from recurring debits, grouped by how often they are debited.
 *
 * @param contracts null while loading
 * @param currency the currency all amounts of [contracts] are in
 */
@Composable
fun NextContractsScreen(
    contracts: List<Contract>?,
    currency: CurrencyUnit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(ContractsTab.Contracts) }
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            ContractsTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    text = { Text(stringResource(tab.labelRes)) }
                )
            }
        }
        when (selectedTab) {
            ContractsTab.Contracts -> ContractList(contracts, currency, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ContractList(
    contracts: List<Contract>?,
    currency: CurrencyUnit,
    modifier: Modifier = Modifier,
) {
    when {
        contracts == null -> CircularProgressIndicator(
            modifier
                .fillMaxSize()
                .wrapContentSize()
        )

        contracts.isEmpty() -> EmptyState(modifier)

        else -> {
            val (active, ended) = contracts.partition { it.isActive }
            val byInterval = active.groupBy { it.interval }.toSortedMap()
            var expanded by rememberSaveable { mutableStateOf<String?>(null) }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item(key = "summary") {
                    SummaryCard(
                        monthlyAmount = active.sumOf { it.monthlyAmount },
                        yearlyAmount = active.sumOf { it.yearlyAmount },
                        count = active.size,
                        currency = currency
                    )
                }
                byInterval.forEach { (interval, group) ->
                    item(key = "header_${interval.name}") {
                        SectionHeader(
                            title = stringResource(interval.labelRes),
                            count = group.size,
                            total = group.sumOf { it.lastAmount },
                            currency = currency
                        )
                    }
                    itemsIndexed(group, key = { _, contract -> contract.key }) { index, contract ->
                        ContractItem(
                            contract = contract,
                            currency = currency,
                            isFirst = index == 0,
                            isLast = index == group.lastIndex,
                            isExpanded = expanded == contract.key,
                            onClick = { expanded = if (expanded == contract.key) null else contract.key }
                        )
                    }
                }
                if (ended.isNotEmpty()) {
                    item(key = "header_ended") {
                        SectionHeader(
                            title = stringResource(R.string.next_contracts_ended),
                            count = ended.size,
                            total = null,
                            currency = currency
                        )
                    }
                    val sortedEnded = ended.sortedByDescending { it.lastDate }
                    itemsIndexed(sortedEnded, key = { _, contract -> contract.key }) { index, contract ->
                        ContractItem(
                            contract = contract,
                            currency = currency,
                            isFirst = index == 0,
                            isLast = index == sortedEnded.lastIndex,
                            isExpanded = expanded == contract.key,
                            onClick = { expanded = if (expanded == contract.key) null else contract.key },
                            modifier = Modifier.alpha(0.6f)
                        )
                    }
                }
            }
        }
    }
}

@get:StringRes
private val ContractInterval.labelRes: Int
    get() = when (this) {
        ContractInterval.WEEKLY -> R.string.next_interval_weekly
        ContractInterval.BIWEEKLY -> R.string.next_interval_biweekly
        ContractInterval.MONTHLY -> R.string.next_interval_monthly
        ContractInterval.BIMONTHLY -> R.string.next_interval_bimonthly
        ContractInterval.QUARTERLY -> R.string.next_interval_quarterly
        ContractInterval.HALF_YEARLY -> R.string.next_interval_half_yearly
        ContractInterval.YEARLY -> R.string.next_interval_yearly
    }

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.next_contracts_empty),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.next_contracts_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SummaryCard(
    monthlyAmount: Long,
    yearlyAmount: Long,
    count: Int,
    currency: CurrencyUnit,
) {
    val formatter = LocalCurrencyFormatter.current
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.next_contracts_per_month),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                formatter.convAmount(monthlyAmount, currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(
                    R.string.next_contracts_per_year,
                    formatter.convAmount(yearlyAmount, currency)
                ) + " · " + pluralStringResource(R.plurals.next_contracts_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    total: Long?,
    currency: CurrencyUnit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$title ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (total != null) {
            Text(
                LocalCurrencyFormatter.current.convAmount(total, currency),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One contract. Contracts of a section look like one card, like the days of the transaction list.
 * A tap shows the debits the contract was detected from.
 */
@Composable
private fun ContractItem(
    contract: Contract,
    currency: CurrencyUnit,
    isFirst: Boolean,
    isLast: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = if (isFirst) 16.dp else 0.dp,
            topEnd = if (isFirst) 16.dp else 0.dp,
            bottomStart = if (isLast) 16.dp else 0.dp,
            bottomEnd = if (isLast) 16.dp else 0.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(Modifier.animateContentSize()) {
            if (!isFirst) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = contract.categoryIcon
                    if (icon != null) {
                        CategoryIcon(icon, size = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = contract.name.ifEmpty { "–" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (contract.isActive)
                            stringResource(R.string.next_contracts_next, dateFormatter.format(contract.nextExpectedDate))
                        else
                            stringResource(R.string.next_contracts_last, dateFormatter.format(contract.lastDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PriceChangeIndicator(contract, currency)
                Text(
                    text = formatter.convAmount(contract.lastAmount, currency),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (isExpanded) {
                ContractDetails(contract, currency, dateFormatter)
            }
        }
    }
}

/**
 * Arrow if the last debit differs from the one before
 */
@Composable
private fun PriceChangeIndicator(contract: Contract, currency: CurrencyUnit) {
    val previous = contract.previousAmount ?: return
    if (previous == contract.lastAmount) return
    val increased = contract.lastAmount > previous
    Icon(
        if (increased) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
        contentDescription = stringResource(
            R.string.next_contracts_previously,
            LocalCurrencyFormatter.current.convAmount(previous, currency)
        ),
        tint = if (increased) LocalColors.current.expense else LocalColors.current.income,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun ContractDetails(
    contract: Contract,
    currency: CurrencyUnit,
    dateFormatter: DateTimeFormatter,
) {
    val formatter = LocalCurrencyFormatter.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 72.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
    ) {
        contract.categoryPath?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        contract.transactions.asReversed().forEach { transaction ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    dateFormatter.format(transaction.date),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                transaction.accountLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
                Text(
                    formatter.convAmount(-transaction.amount, currency),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 800)
@Preview(name = "Dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NextContractsScreenPreview() {
    val today = LocalDate.now()
    fun contract(name: String, interval: ContractInterval, vararg amounts: Long, last: LocalDate = today.minusDays(5)) =
        Contract(
            key = name,
            name = name,
            interval = interval,
            transactions = amounts.mapIndexed { i, amount ->
                ContractTransaction(
                    id = i.toLong(),
                    date = last.minus(interval.step.multipliedBy(amounts.size - 1 - i)),
                    amount = -amount,
                    accountId = 1,
                    accountLabel = "Girokonto"
                )
            },
            nextExpectedDate = last.plus(interval.step),
            isActive = last.isAfter(today.minusDays(interval.maxDays.toLong()))
        )
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            NextContractsScreen(
                contracts = listOf(
                    contract("Netflix", ContractInterval.MONTHLY, 1299, 1299, 1799),
                    contract("Stadtwerke", ContractInterval.MONTHLY, 8500, 8500, 8500),
                    contract("Kfz-Versicherung", ContractInterval.QUARTERLY, 12050, 12050, 12050),
                    contract("ADAC", ContractInterval.YEARLY, 9400, 9400),
                    contract("Fitnessstudio", ContractInterval.MONTHLY, 2990, 2990, 2990, last = today.minusMonths(5)),
                ),
                currency = CurrencyUnit.DebugInstance
            )
        }
    }
}
