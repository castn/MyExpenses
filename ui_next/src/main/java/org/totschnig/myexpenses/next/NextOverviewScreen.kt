package org.totschnig.myexpenses.next

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.text.NumberFormat
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.compose.LocalColors
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * UI models of the overview screen. They are deliberately independent of the data layer;
 * mapping from the view models happens in the caller.
 */
data class OverviewBudget(
    val id: Long,
    val title: String,
    /** Spent share of the budget, 1f = fully spent, values above 1f = exceeded. */
    val progress: Float,
)

data class OverviewAccount(
    val id: Long,
    val label: String,
    /** Already formatted balance, e.g. "3.205,99 €". */
    val balance: String,
    val isNegative: Boolean,
    val color: Color,
    val bankId: Long? = null,
)

data class OverviewSection(
    val title: String,
    val accounts: List<OverviewAccount>,
)

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun NextOverviewScreen(
    budgets: List<OverviewBudget>,
    sections: List<OverviewSection>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onBudgetClick: (OverviewBudget) -> Unit = {},
    onShowAllBudgets: () -> Unit = {},
    onAddBudget: () -> Unit = {},
    onAccountClick: (OverviewAccount) -> Unit = {},
    onAddAccount: () -> Unit = {},
    /** Called with the ids of all shown accounts in their new order, after the user moved one */
    onReorderAccounts: (List<Long>) -> Unit = {},
    bankIcon: (@Composable (Modifier, Long) -> Unit)? = null,
) {
    var isReordering by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = isReordering) { isReordering = false }

    // Local copy, so that moves are shown immediately. It is replaced by fresh data,
    // which after persisting a move comes back in the same order.
    var orderedSections by remember(sections) { mutableStateOf(sections) }

    // Accounts can only be moved within their section, since the section follows from their type
    fun move(fromId: Long, toId: Long): Boolean {
        val sectionIndex = orderedSections.indexOfFirst { section -> section.accounts.any { it.id == fromId } }
        val section = orderedSections.getOrNull(sectionIndex) ?: return false
        val fromIndex = section.accounts.indexOfFirst { it.id == fromId }
        val toIndex = section.accounts.indexOfFirst { it.id == toId }
        if (toIndex == -1) return false
        orderedSections = orderedSections.toMutableList().apply {
            set(sectionIndex, section.copy(accounts = section.accounts.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }))
        }
        return true
    }

    fun persistOrder() {
        onReorderAccounts(orderedSections.flatMap { section -> section.accounts.map { it.id } })
    }

    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
        val toId = to.key as? Long ?: return@rememberReorderableLazyListState
        if (move(fromId, toId)) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (orderedSections.sumOf { it.accounts.size } > 1) {
                IconButton(onClick = { isReordering = !isReordering }) {
                    if (isReordering) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.next_done),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.next_reorder_accounts),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding
        ) {
            item(key = "budgets") {
                BudgetCard(
                    budgets = budgets,
                    onBudgetClick = onBudgetClick,
                    onShowAll = onShowAllBudgets,
                    onAdd = onAddBudget,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (orderedSections.isEmpty()) {
                item(key = "no_accounts") {
                    NoAccountsCard(
                        onAddAccount = onAddAccount,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)
                    )
                }
            }
            orderedSections.forEachIndexed { sectionIndex, section ->
                item(key = "header_${section.title}") {
                    // Accounts are added rarely, so instead of a dedicated row the add action
                    // only takes up the trailing end of the first section header
                    SectionHeader(
                        title = section.title,
                        onAdd = onAddAccount.takeIf { sectionIndex == 0 && !isReordering }
                    )
                }
                itemsIndexed(section.accounts, key = { _, account -> account.id }) { index, account ->
                    ReorderableItem(reorderableState, key = account.id, enabled = isReordering) { isDragging ->
                        val moveUp = stringResource(org.totschnig.myexpenses.R.string.action_move_up)
                        val moveDown = stringResource(org.totschnig.myexpenses.R.string.action_move_down)
                        AccountItem(
                            account = account,
                            isFirst = index == 0,
                            isLast = index == section.accounts.lastIndex,
                            isDragging = isDragging,
                            bankIcon = bankIcon,
                            onClick = { onAccountClick(account) }.takeIf { !isReordering },
                            dragHandle = if (isReordering) {
                                {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .draggableHandle(
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                },
                                                onDragStopped = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                    persistOrder()
                                                }
                                            )
                                            .padding(12.dp)
                                    )
                                }
                            } else null,
                            // Dragging is not accessible, so screen reader users move accounts step by step
                            accessibilityActions = if (isReordering) buildList {
                                section.accounts.getOrNull(index - 1)?.let { previous ->
                                    add(CustomAccessibilityAction(moveUp) {
                                        move(account.id, previous.id).also { if (it) persistOrder() }
                                    })
                                }
                                section.accounts.getOrNull(index + 1)?.let { next ->
                                    add(CustomAccessibilityAction(moveDown) {
                                        move(account.id, next.id).also { if (it) persistOrder() }
                                    })
                                }
                            } else emptyList(),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}

@Composable
private fun BudgetCard(
    budgets: List<OverviewBudget>,
    onBudgetClick: (OverviewBudget) -> Unit,
    onShowAll: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.next_budgets),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onShowAll)
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.next_show_all),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(budgets, key = { it.id }) { budget ->
                    BudgetRing(budget, onClick = { onBudgetClick(budget) })
                }
                item(key = "add") {
                    BudgetTile(label = null, onClick = onAdd) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.next_add_budget),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

private val BudgetTileWidth = 64.dp
private val BudgetRingSize = 56.dp

/**
 * A circle of [BudgetRingSize] with an optional short [label] below, shared by the budget rings and the add button
 */
@Composable
private fun BudgetTile(
    label: String?,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    circle: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(BudgetTileWidth)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(BudgetRingSize)
                .clip(CircleShape)
        ) {
            circle()
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun BudgetRing(
    budget: OverviewBudget,
    onClick: () -> Unit,
) {
    val colors = LocalColors.current
    val ringColor = when {
        budget.progress > 1f -> colors.expense
        budget.progress > 0.75f -> Color(0xFFFF9800)
        else -> colors.income
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val percentFormat = remember { NumberFormat.getPercentInstance() }
    BudgetTile(
        label = percentFormat.format(budget.progress),
        labelColor = if (budget.progress > 1f) colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(
                    ringColor, -90f, 360f * budget.progress.coerceIn(0f, 1f), false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Icon(
                Icons.Default.Savings,
                contentDescription = budget.title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onAdd: (() -> Unit)?,
) {
    Row(
        // Vertical padding is reduced by the extra height of the 48dp touch target,
        // so the header is as high as without the button
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.next_add_account),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoAccountsCard(
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(R.string.next_no_accounts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.next_no_accounts_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Button(
                onClick = onAddAccount,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.next_add_account))
            }
        }
    }
}

@Composable
private fun AccountItem(
    account: OverviewAccount,
    isFirst: Boolean,
    isLast: Boolean,
    isDragging: Boolean,
    bankIcon: (@Composable (Modifier, Long) -> Unit)?,
    onClick: (() -> Unit)?,
    dragHandle: (@Composable () -> Unit)?,
    accessibilityActions: List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
) {
    // Each account is a separate list item so that it can be dragged. Together they look like
    // one card: only the outer corners are rounded, and dividers separate the rows.
    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { customActions = accessibilityActions },
        shape = if (isDragging) CardShape else RoundedCornerShape(
            topStart = if (isFirst) 16.dp else 0.dp,
            topEnd = if (isFirst) 16.dp else 0.dp,
            bottomStart = if (isLast) 16.dp else 0.dp,
            bottomEnd = if (isLast) 16.dp else 0.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = elevation
    ) {
        Column {
            if (!isFirst && !isDragging) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
            }
            AccountRow(account, bankIcon, onClick, dragHandle)
        }
    }
}

@Composable
private fun AccountRow(
    account: OverviewAccount,
    bankIcon: (@Composable (Modifier, Long) -> Unit)?,
    onClick: (() -> Unit)?,
    dragHandle: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // The drag handle brings its own padding for a large enough touch target
            .padding(start = 16.dp, end = if (dragHandle != null) 0.dp else 8.dp)
            .padding(vertical = if (dragHandle != null) 10.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconModifier = Modifier.size(32.dp)
        if (account.bankId != null && bankIcon != null) {
            bankIcon(iconModifier, account.bankId)
        } else {
            Box(
                modifier = iconModifier
                    .clip(CircleShape)
                    .background(account.color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = account.label.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = account.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = account.balance,
            style = MaterialTheme.typography.bodyLarge,
            color = if (account.isNegative) MaterialTheme.colorScheme.onSurface else LocalColors.current.income,
            modifier = Modifier.padding(start = 8.dp)
        )
        if (dragHandle != null) {
            dragHandle()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val previewBudgets = listOf(
    OverviewBudget(1, "Lebensmittel", 0.8f),
    OverviewBudget(2, "Freizeit", 0.3f),
    OverviewBudget(3, "Urlaub", 0.55f),
    OverviewBudget(4, "Haushalt", 1.1f),
)

private val previewSections = listOf(
    OverviewSection(
        "Tägliche Konten", listOf(
            OverviewAccount(1, "Girokonto", "3.205,99 €", false, Color(0xFFE53935)),
            OverviewAccount(2, "Kreditkarte", "-256,36 €", true, Color(0xFF1E88E5)),
            OverviewAccount(3, "PayPal", "32,11 €", false, Color(0xFF283593)),
        )
    ),
    OverviewSection(
        "Investments", listOf(
            OverviewAccount(4, "Direkt Depot", "12.187,22 €", false, Color(0xFFFB8C00)),
        )
    ),
)

@Preview(name = "Light", heightDp = 640)
@Preview(name = "Dark", heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NextOverviewScreenPreview() {
    // AppTheme/NextTheme need MyApplication (injector), which is not available in previews
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        NextOverviewScreen(budgets = previewBudgets, sections = previewSections)
    }
}

@Preview(name = "No accounts", heightDp = 640)
@Composable
private fun NextOverviewScreenEmptyPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        NextOverviewScreen(budgets = previewBudgets, sections = emptyList())
    }
}
