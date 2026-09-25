package org.totschnig.myexpenses.next.contracts

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.absoluteValue
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.util.convAmount
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
    /** Categories the contract can be put into */
    areas: List<ContractArea> = BuiltInArea.entries,
    onSetArea: (AreaChoice) -> Unit = {},
    onCreateArea: (String) -> CustomArea = { CustomArea(it, it) },
    onRenameArea: (CustomArea, String) -> Unit = { _, _ -> },
    onDeleteArea: (CustomArea) -> Unit = {},
    /** Blank to go back to the detected name */
    onRename: (String) -> Unit = {},
    /** Opens the debits the contract was detected from */
    onShowTransactions: () -> Unit = {},
    /** For incomes: whether it is the salary */
    isSalary: Boolean = false,
    onSalaryClick: () -> Unit = {},
    /** For a suggestion: the user confirms it, null for a confirmed contract */
    onConfirm: (() -> Unit)? = null,
    /** For a suggestion: the user declares it as no contract */
    onReject: (() -> Unit)? = null,
    /** Contracts that can be joined into this one, see [ContractRuleSettings] */
    mergeCandidates: List<Contract> = emptyList(),
    onSetInterval: (ContractInterval) -> Unit = {},
    onRemovePayee: (Long) -> Unit = {},
    onRemoveTargetAccount: (Long) -> Unit = {},
    onSetAmountRange: (LongRange?) -> Unit = {},
    onMerge: (Contract) -> Unit = {},
    /** For a confirmed contract: the user cancelled it (or the income was discontinued) */
    onCancel: () -> Unit = {},
    onRevokeCancellation: () -> Unit = {},
    onSetReserve: (Boolean) -> Unit = {},
    /** For a confirmed contract: the user declares it as no contract */
    onDismiss: () -> Unit = {},
    /** Unread news of this contract, shown at the top */
    news: List<ContractNews> = emptyList(),
    /** All news of this contract, newest first */
    history: List<ContractNews> = emptyList(),
) {
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    if (showRenameDialog) {
        NameDialog(
            title = stringResource(R.string.next_contracts_rename),
            initialName = contract.displayName,
            // An empty name goes back to the detected one
            placeholder = contract.name,
            allowBlank = true,
            onConfirm = {
                onRename(it)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
    var showAreaDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateAreaDialog by rememberSaveable { mutableStateOf(false) }
    /** Key of the custom category being edited */
    var editedAreaKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Back to the choice after editing, so that the user sees the result
    areas.filterIsInstance<CustomArea>().find { it.key == editedAreaKey }?.let { area ->
        EditAreaDialog(
            area = area,
            onRename = {
                onRenameArea(area, it)
                editedAreaKey = null
                showAreaDialog = true
            },
            onDelete = {
                onDeleteArea(area)
                editedAreaKey = null
                showAreaDialog = true
            },
            onDismiss = {
                editedAreaKey = null
                showAreaDialog = true
            }
        )
    }
    if (showAreaDialog) {
        AreaDialog(
            contract = contract,
            areas = areas,
            onSelect = {
                onSetArea(it)
                showAreaDialog = false
            },
            onCreate = {
                showAreaDialog = false
                showCreateAreaDialog = true
            },
            onEdit = {
                showAreaDialog = false
                editedAreaKey = it.key
            },
            onDismiss = { showAreaDialog = false }
        )
    }
    if (showCreateAreaDialog) {
        NameDialog(
            title = stringResource(R.string.next_contracts_new_area),
            initialName = "",
            onConfirm = {
                onSetArea(AreaChoice.Fixed(onCreateArea(it)))
                showCreateAreaDialog = false
            },
            onDismiss = { showCreateAreaDialog = false }
        )
    }
    val formatter = LocalCurrencyFormatter.current
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    fun debit(amount: Long) = (if (contract.isIncome) "+ " else "− ") + formatter.convAmount(amount, currency)

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
            if (news.isNotEmpty()) {
                NewsBanner(news, currency, onCancelAgain = onCancel.takeIf { !contract.isCancelled })
            }
            if (onConfirm != null && onReject != null) {
                SuggestionQuestion(contract.isIncome, onConfirm, onReject)
            }
            DetailCard {
                Header(contract, onRename = { showRenameDialog = true })
                val chipModifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                if (contract.isIncome) LabelChip(
                    text = if (isSalary) stringResource(R.string.next_salary) else null,
                    placeholder = stringResource(R.string.next_salary_set),
                    onClick = onSalaryClick,
                    modifier = chipModifier
                ) else LabelChip(
                    text = contract.area?.label(),
                    placeholder = stringResource(R.string.next_contracts_area),
                    onClick = { showAreaDialog = true },
                    modifier = chipModifier
                )
                InfoRow(stringResource(contract.interval.labelRes), debit(contract.lastAmount))
                contract.previousAmount?.takeIf { it != contract.lastAmount }?.let {
                    InfoRow(stringResource(R.string.next_contracts_previous_amount), debit(it))
                }
                InfoRow(
                    stringResource(R.string.next_contracts_total),
                    debit(contract.transactions.sumOf { it.amount.absoluteValue })
                )
                contract.cancelledOn?.let {
                    InfoRow(
                        stringResource(
                            when {
                                contract.isIncome -> R.string.next_income_cancelled_row
                                contract.isReserve -> R.string.next_reserve_cancelled_row
                                else -> R.string.next_contracts_cancelled_row
                            }
                        ),
                        dateFormatter.format(it)
                    )
                }
                if (contract.isActive) {
                    InfoRow(
                        stringResource(if (contract.isIncome) R.string.next_income_next else R.string.next_contracts_next_debit),
                        dateFormatter.format(contract.nextExpectedDate)
                    )
                } else {
                    InfoRow(
                        stringResource(R.string.next_contracts_last_debit),
                        dateFormatter.format(contract.lastDate)
                    )
                }
                // Payments older than the analysed period cannot be shown
                if (contract.hasPayments) LinkRow(
                    pluralStringResource(
                        R.plurals.next_contracts_based_on,
                        contract.transactions.size,
                        contract.transactions.size
                    ),
                    onClick = onShowTransactions
                )
            }

            SectionTitle(stringResource(R.string.next_contracts_details))
            DetailCard {
                InfoRow(
                    stringResource(if (contract.isIncome) R.string.next_income_account else R.string.next_contracts_account),
                    contract.transactions.mapNotNull { it.accountLabel }.distinct().joinToString()
                        .ifEmpty { "–" },
                    isFirst = true
                )
                contract.categoryPath?.let {
                    InfoRow(stringResource(R.string.next_contracts_transaction_category), it)
                }
                contract.lastTransaction.comment?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(stringResource(R.string.next_contracts_purpose), it)
                }
            }
            contract.rule?.let { rule ->
                ContractRuleSettings(
                    contract = contract,
                    rule = rule,
                    currency = currency,
                    mergeCandidates = mergeCandidates,
                    onSetInterval = onSetInterval,
                    onRemovePayee = onRemovePayee,
                    onRemoveTargetAccount = onRemoveTargetAccount,
                    onSetAmountRange = onSetAmountRange,
                    onMerge = onMerge,
                    onSetReserve = onSetReserve
                )
                DetailCard(Modifier.padding(top = 16.dp)) {
                    if (contract.isCancelled) LinkRow(
                        stringResource(
                            when {
                                contract.isIncome -> R.string.next_income_uncancel
                                contract.isReserve -> R.string.next_reserve_uncancel
                                else -> R.string.next_contracts_uncancel
                            }
                        ),
                        onClick = onRevokeCancellation,
                        isFirst = true
                    ) else LinkRow(
                        stringResource(
                            when {
                                contract.isIncome -> R.string.next_income_cancel
                                contract.isReserve -> R.string.next_reserve_cancel
                                else -> R.string.next_contracts_cancel
                            }
                        ),
                        onClick = onCancel,
                        isFirst = true
                    )
                    LinkRow(
                        stringResource(if (contract.isIncome) R.string.next_income_dismiss else R.string.next_contracts_dismiss),
                        onClick = onDismiss
                    )
                }
            }
            if (history.isNotEmpty()) {
                NewsHistorySection(history, currency)
            }
        }
    }
}

/**
 * Asks whether a suggestion is a contract (or regular income)
 */
@Composable
private fun SuggestionQuestion(isIncome: Boolean, onConfirm: () -> Unit, onReject: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text(
                stringResource(if (isIncome) R.string.next_income_question else R.string.next_contracts_question),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(if (isIncome) R.string.next_income_question_hint else R.string.next_contracts_question_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onReject) {
                    Text(stringResource(R.string.next_no))
                }
                Button(onClick = onConfirm, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.next_yes))
                }
            }
        }
    }
}

@Composable
internal fun DetailCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun Header(contract: Contract, onRename: () -> Unit) {
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
        // Tapping the name renames the contract, the pencil shows that this is possible
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = stringResource(R.string.next_contracts_rename), onClick = onRename)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                contract.displayName.ifEmpty { "–" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
            )
        }
    }
}

/**
 * A property of the contract as small box, e.g. its category. Without [text], an outlined box
 * with a plus invites to set it. Tapping it lets the user choose.
 */
@Composable
private fun LabelChip(
    text: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (text != null) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (text != null) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (text == null) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (text == null) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp)
                )
            }
            Text(text ?: placeholder, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Label on the left, value on the right, separated from the row above by a divider
 */
@Composable
internal fun InfoRow(
    label: String,
    value: String,
    isFirst: Boolean = false,
) {
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

/**
 * Lets the user choose the area of [contract], or go back to the one suggested by its category
 */
@Composable
private fun AreaDialog(
    contract: Contract,
    areas: List<ContractArea>,
    onSelect: (AreaChoice) -> Unit,
    onCreate: () -> Unit,
    onEdit: (CustomArea) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = listOf(AreaChoice.Automatic, AreaChoice.Fixed(null)) + areas.map { AreaChoice.Fixed(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.next_contracts_area)) },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                choices.forEach { choice ->
                    val selected = choice == contract.areaChoice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(choice) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            when (choice) {
                                AreaChoice.Automatic -> stringResource(
                                    R.string.next_contracts_area_automatic,
                                    contract.suggestedArea.label()
                                )

                                is AreaChoice.Fixed -> choice.area.label()
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        )
                        val custom = (choice as? AreaChoice.Fixed)?.area as? CustomArea
                        if (custom != null) {
                            IconButton(onClick = { onEdit(custom) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.next_contracts_edit_area),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreate)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.next_contracts_new_area),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

/**
 * Row that leads to another page, like "show all" on the overview
 */
@Composable
internal fun LinkRow(text: String, onClick: () -> Unit, isFirst: Boolean = false) {
    if (!isFirst) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary
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
