package org.totschnig.myexpenses.next.contracts

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Like the account list of the overview, the list has an edit mode. In it, the user removes
 * contracts that were detected by mistake, restores them and renames contracts.
 *
 * @param currency the currency all amounts of the contracts are in
 */
@Composable
fun NextContractsScreen(
    state: ContractsUiState,
    currency: CurrencyUnit,
    modifier: Modifier = Modifier,
    onConsent: (Boolean) -> Unit = {},
    onDismiss: (Contract) -> Unit = {},
    onRestore: (Contract) -> Unit = {},
    onRename: (Contract, String) -> Unit = { _, _ -> },
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
        val contentModifier = Modifier.weight(1f)
        when (selectedTab) {
            ContractsTab.Contracts -> when (state) {
                ContractsUiState.Loading -> CircularProgressIndicator(
                    contentModifier
                        .fillMaxSize()
                        .wrapContentSize()
                )

                ContractsUiState.AskConsent -> ConsentCard(
                    isDeclined = false,
                    onConsent = onConsent,
                    modifier = contentModifier
                )

                ContractsUiState.Declined -> ConsentCard(
                    isDeclined = true,
                    onConsent = onConsent,
                    modifier = contentModifier
                )

                is ContractsUiState.Ready -> ContractList(
                    state = state,
                    currency = currency,
                    onDismiss = onDismiss,
                    onRestore = onRestore,
                    onRename = onRename,
                    modifier = contentModifier
                )
            }
        }
    }
}

/**
 * Asks whether transactions may be analysed. Once declined, only offers to start the analysis.
 */
@Composable
private fun ConsentCard(
    isDeclined: Boolean,
    onConsent: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    stringResource(R.string.next_contracts_consent_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    stringResource(
                        if (isDeclined) R.string.next_contracts_declined
                        else R.string.next_contracts_consent_text
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(
                    onClick = { onConsent(true) },
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Text(stringResource(R.string.next_contracts_consent_accept))
                }
                if (!isDeclined) {
                    TextButton(onClick = { onConsent(false) }) {
                        Text(stringResource(R.string.next_contracts_consent_decline))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContractList(
    state: ContractsUiState.Ready,
    currency: CurrencyUnit,
    onDismiss: (Contract) -> Unit,
    onRestore: (Contract) -> Unit,
    onRename: (Contract, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = isEditing) { isEditing = false }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }

    if (state.isEmpty) {
        EmptyState(modifier)
        return
    }

    renaming?.let { signature ->
        (state.active + state.ended + state.dismissed).find { it.signature == signature }?.let { contract ->
            RenameDialog(
                contract = contract,
                onConfirm = {
                    onRename(contract, it)
                    renaming = null
                },
                onDismiss = { renaming = null }
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { isEditing = !isEditing }) {
                if (isEditing) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.next_done),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.next_contracts_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // All contracts removed: nothing to show outside of the edit mode
        if (!isEditing && state.active.isEmpty() && state.ended.isEmpty()) {
            EmptyState(Modifier.weight(1f))
            return@Column
        }

        val dismissLabel = stringResource(R.string.next_contracts_dismiss)
        val restoreLabel = stringResource(R.string.next_contracts_restore)
        val renameLabel = stringResource(R.string.next_contracts_rename)

        fun LazyListScope.section(contracts: List<Contract>, isDismissed: Boolean) {
            itemsIndexed(contracts, key = { _, contract -> contract.signature }) { index, contract ->
                ContractItem(
                    contract = contract,
                    currency = currency,
                    isFirst = index == 0,
                    isLast = index == contracts.lastIndex,
                    isExpanded = !isEditing && expanded == contract.signature,
                    onClick = if (isEditing) {
                        { renaming = contract.signature }
                    } else {
                        { expanded = if (expanded == contract.signature) null else contract.signature }
                    },
                    editAction = if (isEditing) {
                        if (isDismissed) EditAction(Icons.Default.Restore, restoreLabel) { onRestore(contract) }
                        else EditAction(Icons.Default.RemoveCircleOutline, dismissLabel) { onDismiss(contract) }
                    } else null,
                    // Tapping the row renames it in edit mode, the action is announced as well
                    accessibilityActions = if (isEditing) listOf(
                        CustomAccessibilityAction(renameLabel) { renaming = contract.signature; true }
                    ) else emptyList(),
                    isFaded = isDismissed || !contract.isActive
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            if (state.active.isNotEmpty()) {
                item(key = "summary") {
                    SummaryCard(
                        monthlyAmount = state.active.sumOf { it.monthlyAmount },
                        yearlyAmount = state.active.sumOf { it.yearlyAmount },
                        count = state.active.size,
                        currency = currency
                    )
                }
            }
            state.active.groupBy { it.interval }.toSortedMap().forEach { (interval, group) ->
                item(key = "header_${interval.name}") {
                    SectionHeader(
                        title = stringResource(interval.labelRes),
                        count = group.size,
                        total = group.sumOf { it.lastAmount },
                        currency = currency
                    )
                }
                section(group, isDismissed = false)
            }
            if (state.ended.isNotEmpty()) {
                item(key = "header_ended") {
                    SectionHeader(
                        title = stringResource(R.string.next_contracts_ended),
                        count = state.ended.size,
                        total = null,
                        currency = currency
                    )
                }
                section(state.ended, isDismissed = false)
            }
            if (isEditing && state.dismissed.isNotEmpty()) {
                item(key = "header_dismissed") {
                    SectionHeader(
                        title = stringResource(R.string.next_contracts_hidden),
                        count = state.dismissed.size,
                        total = null,
                        currency = currency
                    )
                }
                section(state.dismissed, isDismissed = true)
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
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.next_contracts_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun RenameDialog(
    contract: Contract,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(contract.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.next_contracts_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                // An empty name goes back to the detected one
                placeholder = { Text(contract.name) }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
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

private class EditAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * One contract. Contracts of a section look like one card, like the days of the transaction list.
 * A tap shows the debits the contract was detected from. In edit mode, [editAction] replaces the amount.
 */
@Composable
private fun ContractItem(
    contract: Contract,
    currency: CurrencyUnit,
    isFirst: Boolean,
    isLast: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    editAction: EditAction?,
    accessibilityActions: List<CustomAccessibilityAction>,
    isFaded: Boolean,
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
                    .semantics { customActions = accessibilityActions }
                    .padding(start = 16.dp, end = if (editAction != null) 4.dp else 16.dp)
                    .padding(vertical = if (editAction != null) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (isFaded) 0.6f else 1f),
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
                            text = contract.displayName.ifEmpty { "–" },
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
                    if (editAction == null) {
                        PriceChangeIndicator(contract, currency)
                        Text(
                            text = formatter.convAmount(contract.lastAmount, currency),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                if (editAction != null) {
                    IconButton(onClick = editAction.onClick) {
                        Icon(
                            editAction.icon,
                            contentDescription = editAction.label,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    // AppTheme/NextTheme need MyApplication (injector), which is not available in previews
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(content = content)
    }
}

private fun previewState(): ContractsUiState.Ready {
    val today = LocalDate.now()
    fun contract(name: String, interval: ContractInterval, vararg amounts: Long, last: LocalDate = today.minusDays(5)) =
        Contract(
            signature = name,
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
    return buildContractsState(
        listOf(
            contract("Netflix", ContractInterval.MONTHLY, 1299, 1299, 1799),
            contract("Stadtwerke", ContractInterval.MONTHLY, 8500, 8500, 8500),
            contract("Kfz-Versicherung", ContractInterval.QUARTERLY, 12050, 12050, 12050),
            contract("ADAC", ContractInterval.YEARLY, 9400, 9400),
            contract("Fitnessstudio", ContractInterval.MONTHLY, 2990, 2990, 2990, last = today.minusMonths(5)),
            contract("Bäckerei", ContractInterval.WEEKLY, 450, 480, 450, 470),
        ),
        ContractSettings(consent = true, dismissed = setOf("Bäckerei"), names = mapOf("Stadtwerke" to "Strom"))
    )
}

@Preview(name = "Light", showBackground = true, heightDp = 800)
@Preview(name = "Dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NextContractsScreenPreview() {
    PreviewTheme {
        NextContractsScreen(state = previewState(), currency = CurrencyUnit.DebugInstance)
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun ConsentPreview() {
    PreviewTheme {
        NextContractsScreen(state = ContractsUiState.AskConsent, currency = CurrencyUnit.DebugInstance)
    }
}
