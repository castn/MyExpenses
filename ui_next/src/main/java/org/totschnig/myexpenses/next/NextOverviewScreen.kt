package org.totschnig.myexpenses.next

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.compose.LocalColors

/**
 * UI models of the overview screen. They are deliberately independent of the data layer;
 * mapping from the view models happens in the caller.
 */
data class OverviewBudget(
    val id: Long,
    val title: String,
    /** Spent share of the budget, 1f = fully spent, values above 1f = exceeded. */
    val progress: Float,
    val icon: ImageVector = Icons.Default.Savings,
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
    bankIcon: (@Composable (Modifier, Long) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentPadding = contentPadding
    ) {
        item(key = "budgets") {
            BudgetCard(
                budgets = budgets,
                onBudgetClick = onBudgetClick,
                onShowAll = onShowAllBudgets,
                onAdd = onAddBudget,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
        }
        if (sections.isEmpty()) {
            item(key = "no_accounts") {
                NoAccountsCard(
                    onAddAccount = onAddAccount,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)
                )
            }
        }
        sections.forEachIndexed { index, section ->
            item(key = "header_${section.title}") {
                // Accounts are added rarely, so instead of a dedicated row the add action
                // only takes up the trailing end of the first section header
                SectionHeader(
                    title = section.title,
                    onAdd = onAddAccount.takeIf { index == 0 }
                )
            }
            item(key = "section_${section.title}") {
                AccountCard(
                    accounts = section.accounts,
                    onAccountClick = onAccountClick,
                    bankIcon = bankIcon,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        item { Spacer(Modifier.padding(bottom = 16.dp)) }
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
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = onAdd),
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
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
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
            budget.icon,
            contentDescription = budget.title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun AccountCard(
    accounts: List<OverviewAccount>,
    onAccountClick: (OverviewAccount) -> Unit,
    bankIcon: (@Composable (Modifier, Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column {
            accounts.forEachIndexed { index, account ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                }
                AccountRow(account, bankIcon, onClick = { onAccountClick(account) })
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: OverviewAccount,
    bankIcon: (@Composable (Modifier, Long) -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 18.dp),
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
