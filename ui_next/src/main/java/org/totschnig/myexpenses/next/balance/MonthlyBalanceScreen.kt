package org.totschnig.myexpenses.next.balance

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.absoluteValue
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.designsystem.Dot
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.util.convAmount

/**
 * Breakdown of the balance of the current period: income, contracts, savings, other expenses
 * and what is left. Each row leads to where the amount comes from.
 *
 * @param onOpenSavings null, if there is nothing to show
 * @param onOpenOther null, if there is nothing to show
 * @param salaryName names of the salaries the period is based on, null for the calendar month
 * @param onChangeSalary lets the user choose the salary
 * @param hasIncomeNews unread news about regular incomes, shown as dot on the income
 */
@Composable
fun MonthlyBalanceScreen(
    balance: MonthlyBalance,
    today: LocalDate,
    currency: CurrencyUnit,
    onBack: () -> Unit,
    onOpenIncome: () -> Unit,
    onOpenContracts: () -> Unit,
    onOpenSavings: (() -> Unit)?,
    onOpenOther: (() -> Unit)?,
    salaryName: String?,
    onChangeSalary: () -> Unit,
    modifier: Modifier = Modifier,
    hasIncomeNews: Boolean = false,
) {
    val formatter = LocalCurrencyFormatter.current
    val colors = LocalColors.current
    fun signed(amount: Long) = when {
        amount > 0 -> "+ "
        amount < 0 -> "− "
        else -> ""
    } + formatter.convAmount(amount.absoluteValue, currency)

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
            Column(Modifier.padding(start = 4.dp, end = 16.dp)) {
                Text(
                    stringResource(R.string.next_balance_details),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    balance.period.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // What the period is based on, tapping it changes the salary
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            onClickLabel = stringResource(R.string.next_salary),
                            onClick = onChangeSalary
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        salaryName?.let { stringResource(R.string.next_balance_salary, it) }
                            ?: stringResource(R.string.next_balance_calendar_month),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    BalanceRow(
                        stringResource(R.string.next_balance_income),
                        signed(balance.income),
                        colors.income,
                        hasNews = hasIncomeNews,
                        hint = if (balance.incomeUpcoming != 0L) stringResource(
                            R.string.next_balance_income_upcoming,
                            formatter.convAmount(balance.incomeUpcoming, currency)
                        ) else null,
                        onClick = onOpenIncome
                    )
                    BalanceRow(
                        stringResource(R.string.next_tab_contracts),
                        signed(balance.contracts),
                        colors.expense,
                        hint = if (balance.contractsUpcoming != 0L) stringResource(
                            R.string.next_balance_contracts_upcoming,
                            formatter.convAmount(-balance.contractsUpcoming, currency)
                        ) else null,
                        onClick = onOpenContracts
                    )
                    BalanceRow(
                        stringResource(R.string.next_balance_savings),
                        signed(balance.reserves),
                        // Money put aside is no loss
                        MaterialTheme.colorScheme.onSurface,
                        hint = if (balance.savingsUpcoming != 0L) stringResource(
                            R.string.next_balance_contracts_upcoming,
                            formatter.convAmount(-balance.savingsUpcoming, currency)
                        ) else null,
                        onClick = onOpenSavings
                    )
                    BalanceRow(
                        stringResource(R.string.next_balance_other),
                        signed(balance.other),
                        colors.expense,
                        onClick = onOpenOther
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, thickness = 2.dp)
                    AvailableRow(balance, today, currency)
                }
            }
        }
    }
}

@Composable
internal fun BalancePeriod.label(): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    return "${formatter.format(start)} – ${formatter.format(end.minusDays(1))}"
}

@Composable
private fun BalanceRow(
    label: String,
    amount: String,
    color: Color,
    onClick: (() -> Unit)?,
    hint: String? = null,
    hasNews: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (hasNews) Dot(Modifier.padding(start = 6.dp))
            }
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            amount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        // Keeps the amounts aligned, also for rows without target
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (onClick != null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
}

@Composable
private fun AvailableRow(balance: MonthlyBalance, today: LocalDate, currency: CurrencyUnit) {
    val formatter = LocalCurrencyFormatter.current
    val colors = LocalColors.current
    val color = if (balance.available < 0) colors.expense else colors.income
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            stringResource(R.string.next_balance_available_total),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatter.convAmount(balance.available, currency),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (balance.available > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.15f),
                    contentColor = color,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.next_balance_per_day,
                            formatter.convAmount(balance.available / balance.period.daysLeft(today), currency)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 600)
@Preview(name = "Dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthlyBalanceScreenPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        MonthlyBalanceScreen(
            balance = previewBalance(),
            today = LocalDate.now(),
            currency = CurrencyUnit.DebugInstance,
            onBack = {},
            onOpenIncome = {},
            onOpenContracts = {},
            onOpenSavings = {},
            onOpenOther = {},
            salaryName = "Arbeitgeber GmbH",
            onChangeSalary = {}
        )
    }
}
