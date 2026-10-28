package org.totschnig.myexpenses.next.feature.tips

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.totschnig.myexpenses.designsystem.theme.LocalSemanticColors
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.designsystem.ExpandableCard
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.designsystem.Dot
import org.totschnig.myexpenses.designsystem.EmptyState
import org.totschnig.myexpenses.designsystem.LoadingBlock
import org.totschnig.myexpenses.designsystem.NavigationRow
import org.totschnig.myexpenses.designsystem.OverviewCard
import org.totschnig.myexpenses.designsystem.PageTopBar
import org.totschnig.myexpenses.designsystem.TopBarAction
import org.totschnig.myexpenses.designsystem.PromptCard
import org.totschnig.myexpenses.designsystem.RowDivider
import org.totschnig.myexpenses.designsystem.SectionHeader
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.data.contracts.labelRes
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.core.ui.receipts.labelRes
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.data.tips.BankFeeGroup
import org.totschnig.myexpenses.next.data.tips.BankFeeKind
import org.totschnig.myexpenses.next.data.tips.DepositNotReturned
import org.totschnig.myexpenses.next.data.tips.DrugstoreInSupermarkets
import org.totschnig.myexpenses.next.data.tips.EatingOut
import org.totschnig.myexpenses.next.data.tips.ItemGroupRising
import org.totschnig.myexpenses.next.data.tips.Outliers
import org.totschnig.myexpenses.next.data.tips.PotsOverspent
import org.totschnig.myexpenses.next.data.tips.PriceIncrease
import org.totschnig.myexpenses.next.data.tips.SavingTip
import org.totschnig.myexpenses.next.data.tips.SavingTipsOverview
import org.totschnig.myexpenses.next.data.tips.SmallPurchases
import org.totschnig.myexpenses.next.data.tips.SpendingGroup
import org.totschnig.myexpenses.next.data.tips.Supermarkets

/**
 * What a tip can open
 *
 * @param onOpenContract opens a contract a tip is about, by its signature
 * @param onShowTransactions shows a group of [SavingTip.transactionGroups] by its key, with title and subtitle
 */
class TipNavigation(
    val onOpenContract: (String) -> Unit = {},
    val onShowTransactions: (key: String, title: String, subtitle: String) -> Unit = { _, _, _ -> },
)

/**
 * Content of the tab "Spartipps": the tips, highest amount first, and those the user dismissed.
 * Opening the tab marks the tips as seen, they keep their dot until the tab is left.
 */
@Composable
fun SavingTipsScreen(
    state: AnalysisState<SavingTipsOverview>,
    currency: CurrencyInfo,
    modifier: Modifier = Modifier,
    onConsent: () -> Unit = {},
    /** Opens the settings of the analysis, none if null */
    onSettings: (() -> Unit)? = null,
    onDismiss: (SavingTip) -> Unit = {},
    onRestore: (SavingTip) -> Unit = {},
    onMarkSeen: (List<SavingTip>) -> Unit = {},
    navigation: TipNavigation = TipNavigation(),
    /** Below the tips, e.g. the tips that still lack data */
    footer: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(onBack = null, title = stringResource(R.string.next_tab_saving_tips), actions = {
            onSettings?.let {
                TopBarAction(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.next_settings_open),
                    onClick = it
                )
            }
        })
        val contentModifier = Modifier.weight(1f)
        when (state) {
            AnalysisState.Loading -> Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            AnalysisState.AskConsent, AnalysisState.Declined -> PromptCard(
                icon = Icons.Default.Search,
                title = stringResource(R.string.next_tips_consent_title),
                text = stringResource(R.string.next_tips_consent_text),
                confirmLabel = stringResource(R.string.next_contracts_consent_accept),
                onConfirm = onConsent,
                modifier = contentModifier
            )

            is AnalysisState.Ready -> {
                val overview = state.value
                // Dots stay until the tab is left, although the tips are marked as seen right away
                var newIds by remember { mutableStateOf(overview.newIds) }
                LaunchedEffect(overview.newIds) {
                    if (overview.newIds.isNotEmpty()) {
                        newIds = newIds + overview.newIds
                        onMarkSeen(overview.tips.filter { it.id in overview.newIds })
                    }
                }
                TipList(overview, newIds, currency, onDismiss, onRestore, navigation, footer, contentModifier)
            }
        }
    }
}

@Composable
private fun TipList(
    state: SavingTipsOverview,
    newIds: Set<String>,
    currency: CurrencyInfo,
    onDismiss: (SavingTip) -> Unit,
    onRestore: (SavingTip) -> Unit,
    navigation: TipNavigation,
    footer: @Composable () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.tips.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.next_tips_empty),
                    hint = stringResource(R.string.next_tips_empty_hint),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
        items(state.tips, key = { it.id }) { tip ->
            TipCard(tip, currency, isNew = tip.id in newIds, navigation = navigation) {
                TextButton(onClick = { onDismiss(tip) }) {
                    Text(stringResource(R.string.next_tips_dismiss))
                }
            }
        }
        if (state.dismissed.isNotEmpty()) {
            item(key = "dismissed") {
                SectionHeader(title = stringResource(R.string.next_contracts_hidden), count = state.dismissed.size)
            }
            items(state.dismissed, key = { "dismissed_${it.id}" }) { tip ->
                TipCard(tip, currency, isNew = false, isFaded = true, navigation = navigation) {
                    TextButton(onClick = { onRestore(tip) }) {
                        Text(stringResource(R.string.next_tips_restore))
                    }
                }
            }
        }
        item(key = "footer") { footer() }
    }
}

@Composable
private fun TipCard(
    tip: SavingTip,
    currency: CurrencyInfo,
    isNew: Boolean,
    navigation: TipNavigation,
    isFaded: Boolean = false,
    action: @Composable () -> Unit,
) {
    val text = tip.text(currency)
    // Only title, amount and message at first, the advice and what the tip is about on a tap
    var expanded by rememberSaveable(tip.id) { mutableStateOf(false) }
    ExpandableCard(expanded, { expanded = it }, summary = {
        Column(Modifier.alpha(if (isFaded) 0.6f else 1f)) {
            TipHeader(text, isNew)
            Text(text.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }) {
        text.hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.content, end = Spacing.content, bottom = 8.dp)
            )
        }
        when (tip) {
            is SavingTip.PriceIncreases -> Column(Modifier.padding(top = 8.dp)) {
                tip.increases.forEach { PriceIncreaseRow(it, currency, navigation.onOpenContract) }
            }

            is SavingTip.BankFees -> Column(Modifier.padding(top = 8.dp)) {
                tip.groups.forEach { BankFeeRow(it, currency, navigation) }
            }

            is SmallPurchases -> Column(Modifier.padding(top = 8.dp)) {
                tip.payees.forEach {
                    SpendingRow(it, currency, navigation, pluralStringResource(R.plurals.next_tips_purchases, it.count, it.count))
                }
            }

            is EatingOut -> Column(Modifier.padding(top = 8.dp)) {
                val title = EatingOut.DAYS.toInt().let { pluralStringResource(R.plurals.next_tips_last_days, it, it) }
                NavigationRow(
                    title = title,
                    supportingText = pluralStringResource(R.plurals.next_tips_fee_count, tip.transactions.size, tip.transactions.size),
                    onClick = { navigation.onShowTransactions(EatingOut.ID, text.title, title) },
                    value = LocalAmountFormatter.current.format(tip.recent, currency),
                    valueColor = LocalSemanticColors.current.expense
                )
            }

            is Supermarkets -> Column(Modifier.padding(top = 8.dp)) {
                val formatter = LocalAmountFormatter.current
                tip.chains.forEach {
                    SpendingRow(
                        it, currency, navigation,
                        stringResource(
                            R.string.next_tips_purchases_average,
                            pluralStringResource(R.plurals.next_tips_purchases, it.count, it.count),
                            formatter.format(it.average, currency)
                        )
                    )
                }
            }

            is Outliers -> Column(Modifier.padding(top = 8.dp)) {
                val formatter = LocalAmountFormatter.current
                tip.categories.forEach {
                    SpendingRow(
                        it, currency, navigation,
                        stringResource(R.string.next_tips_usually, formatter.format(tip.averages.getValue(it.key), currency)),
                        subtitle = rememberMonthFormatter().format(tip.month)
                    )
                }
            }

            is SavingTip.DuplicateContracts -> Column(Modifier.padding(top = 8.dp)) {
                tip.contracts.forEach { ContractRow(it, currency, navigation.onOpenContract) }
            }

            is SavingTip.LongRunningContracts -> Column(Modifier.padding(top = 8.dp)) {
                val monthFormatter = rememberMonthFormatter()
                tip.contracts.forEach {
                    ContractRow(
                        it.contract, currency, navigation.onOpenContract,
                        supportingText = stringResource(R.string.next_tips_since, monthFormatter.format(it.since))
                    )
                }
            }

            is SavingTip.FrequentPayments -> Column(Modifier.padding(top = 8.dp)) {
                tip.contracts.forEach { ContractRow(it, currency, navigation.onOpenContract) }
            }

            is ItemGroupRising -> ScannedPurchasesRow(
                tip.id, tip.transactions.size, text.title,
                ItemGroupRising.RECENT_MONTHS.toInt().let { pluralStringResource(R.plurals.next_tips_last_months, it, it) }, navigation
            )

            is DepositNotReturned -> ScannedPurchasesRow(
                tip.id, tip.transactions.size, text.title, stringResource(R.string.next_tips_last_year), navigation
            )

            is DrugstoreInSupermarkets -> ScannedPurchasesRow(
                tip.id, tip.transactions.size, text.title, stringResource(R.string.next_tips_last_year), navigation
            )

            is PotsOverspent -> Column(Modifier.padding(top = 8.dp)) {
                val amount = rememberAmountFormat(currency)
                tip.pots.forEachIndexed { index, pot ->
                    InfoRow(
                        pot.name,
                        stringResource(R.string.next_tips_pot_over, pot.overspent, PotsOverspent.PERIODS, amount(pot.averageOver)),
                        isFirst = index == 0
                    )
                }
            }
        }
        RowDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            action()
        }
    }
}

@Composable
private fun TipHeader(text: TipText, isNew: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isNew) Dot(Modifier.padding(end = Spacing.small))
        Text(
            text.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text.amount,
            style = MaterialTheme.typography.titleSmall,
            color = LocalSemanticColors.current.expense,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun PriceIncreaseRow(increase: PriceIncrease, currency: CurrencyInfo, onOpenContract: (String) -> Unit) {
    val formatter = LocalAmountFormatter.current
    val dateFormatter = rememberDateFormatter()
    NavigationRow(
        title = increase.contract.displayName,
        supportingText = stringResource(
            R.string.next_tips_price_increase,
            formatter.format(increase.before, currency),
            formatter.format(increase.after, currency),
            dateFormatter.format(increase.since)
        ),
        onClick = { onOpenContract(increase.contract.signature) }
    )
}

@Composable
private fun ContractRow(
    contract: Contract,
    currency: CurrencyInfo,
    onOpenContract: (String) -> Unit,
    supportingText: String = stringResource(contract.interval.labelRes),
) {
    NavigationRow(
        title = contract.displayName,
        supportingText = supportingText,
        onClick = { onOpenContract(contract.signature) },
        value = LocalAmountFormatter.current.format(contract.lastAmount, currency)
    )
}

@Composable
private fun BankFeeRow(group: BankFeeGroup, currency: CurrencyInfo, navigation: TipNavigation) {
    val title = group.kind.label()
    val subtitle = stringResource(R.string.next_tips_last_year)
    NavigationRow(
        title = title,
        supportingText = pluralStringResource(R.plurals.next_tips_fee_count, group.transactions.size, group.transactions.size),
        onClick = { navigation.onShowTransactions(SavingTip.BankFees.keyOf(group.kind), title, subtitle) },
        value = LocalAmountFormatter.current.format(group.amount, currency),
        valueColor = LocalSemanticColors.current.expense
    )
}

/**
 * @param subtitle of the page showing the transactions
 */
@Composable
private fun SpendingRow(
    group: SpendingGroup,
    currency: CurrencyInfo,
    navigation: TipNavigation,
    supportingText: String,
    subtitle: String = stringResource(R.string.next_tips_last_year),
) {
    NavigationRow(
        title = group.label,
        supportingText = supportingText,
        onClick = { navigation.onShowTransactions(group.key, group.label, subtitle) },
        value = LocalAmountFormatter.current.format(group.amount, currency),
        valueColor = LocalSemanticColors.current.expense
    )
}

/** Leads to the purchases whose receipts a tip is based on, see [SavingTip.transactionGroups] */
@Composable
private fun ScannedPurchasesRow(key: String, count: Int, title: String, subtitle: String, navigation: TipNavigation) {
    Column(Modifier.padding(top = 8.dp)) {
        NavigationRow(
            title = stringResource(R.string.next_tips_scanned_purchases),
            supportingText = pluralStringResource(R.plurals.next_tips_purchases, count, count),
            onClick = { navigation.onShowTransactions(key, title, subtitle) }
        )
    }
}

/**
 * Card of the overview with the tip of the highest amount, nothing if there is none. Its outline
 * while loading keeps the cards below from jumping, as long as there is a tip.
 */
@Composable
fun SavingTipCard(
    state: AnalysisState<SavingTipsOverview>,
    currency: CurrencyInfo,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == AnalysisState.Loading) {
        OverviewCard(stringResource(R.string.next_tips_card_title), modifier) {
            LoadingBlock(Modifier.fillMaxWidth(0.6f), height = 20.dp)
            LoadingBlock(Modifier.fillMaxWidth().padding(top = 8.dp))
            LoadingBlock(Modifier.fillMaxWidth(0.8f).padding(top = 4.dp))
        }
        return
    }
    val overview = state.valueOrNull ?: return
    val tip = overview.tips.firstOrNull() ?: return
    val text = tip.text(currency)
    OverviewCard(stringResource(R.string.next_tips_card_title), modifier, onClick = onOpen) {
        TipHeader(text, isNew = tip.id in overview.newIds)
        Text(text.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun previewTips(): SavingTipsOverview {
    val today = LocalDate.of(2026, 9, 29)
    fun contract(name: String, amount: Long) = Contract(
        signature = "p:$name",
        name = name,
        interval = ContractInterval.MONTHLY,
        transactions = listOf(ContractTransaction(1, today.minusDays(3), -amount, 1)),
        nextExpectedDate = today.plusDays(27),
        isActive = true,
        isConfirmed = true
    )
    return SavingTipsOverview(
        tips = listOf(
            SavingTip.PriceIncreases(
                listOf(
                    PriceIncrease(contract("Netflix", 1799), 1399, 1799, today.minusMonths(2), 1),
                    PriceIncrease(contract("Stadtwerke", 9800), 9200, 9800, today.minusMonths(5), 2)
                )
            )
        ),
        dismissed = listOf(
            SavingTip.BankFees(
                listOf(
                    BankFeeGroup(BankFeeKind.ACCOUNT, List(12) { ContractTransaction(10L + it, today.minusMonths(it.toLong()), -299, 1) }),
                    BankFeeGroup(BankFeeKind.FOREIGN, listOf(ContractTransaction(30, today.minusMonths(4), -175, 1)))
                )
            )
        ),
        newIds = setOf(SavingTip.PriceIncreases.ID)
    )
}

@Preview(name = "Light", showBackground = true, heightDp = 600)
@Preview(name = "Dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SavingTipsScreenPreview() {
    NextPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            SavingTipsScreen(AnalysisState.Ready(previewTips()), CurrencyInfo.Preview)
        }
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun SavingTipsEmptyPreview() {
    NextPreviewTheme {
        Surface {
            SavingTipsScreen(AnalysisState.Ready(SavingTipsOverview(emptyList())), CurrencyInfo.Preview)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SavingTipCardPreview() {
    NextPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            SavingTipCard(AnalysisState.Ready(previewTips()), CurrencyInfo.Preview, onOpen = {}, modifier = Modifier.padding(16.dp))
        }
    }
}
