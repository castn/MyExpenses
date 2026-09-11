package org.totschnig.myexpenses.next.balance

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.util.convAmount
import java.time.LocalDate

private val CardShape = RoundedCornerShape(16.dp)

/**
 * Card of the overview: income and expenses of the current salary cycle and what is left.
 * Before transactions may be analysed, it asks for consent. Shows nothing when declined or loading.
 */
@Composable
fun MonthlyBalanceCard(
    state: BalanceUiState,
    currency: CurrencyUnit,
    onOpen: () -> Unit,
    onConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        BalanceUiState.Loading, BalanceUiState.Declined -> {}
        BalanceUiState.AskConsent -> ConsentCard(onConsent, modifier)
        is BalanceUiState.Ready -> BalanceCard(state.balance, state.today, currency, onOpen, modifier)
    }
}

@Composable
private fun CardTitle() {
    Text(
        stringResource(R.string.next_balance_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ConsentCard(onConsent: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(Modifier.padding(16.dp)) {
            CardTitle()
            Text(
                stringResource(R.string.next_balance_consent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(onClick = onConsent, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.next_contracts_consent_accept))
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: MonthlyBalance,
    today: LocalDate,
    currency: CurrencyUnit,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val formatter = LocalCurrencyFormatter.current
    val colors = LocalColors.current
    val max = maxOf(balance.income, balance.expenses, 1)
    val daysLeft = balance.period.daysLeft(today).toInt()
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(Modifier.padding(16.dp)) {
            CardTitle()
            AmountBar(
                label = stringResource(R.string.next_balance_income),
                amount = formatter.convAmount(balance.income, currency),
                fraction = balance.income.toFloat() / max,
                color = colors.income,
                modifier = Modifier.padding(top = 12.dp)
            )
            AmountBar(
                label = stringResource(R.string.next_balance_expenses),
                amount = formatter.convAmount(balance.expenses, currency),
                fraction = balance.expenses.toFloat() / max,
                color = colors.expense,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    formatter.convAmount(balance.available, currency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (balance.available < 0) colors.expense else MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.next_balance_available),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
            Text(
                pluralStringResource(
                    if (balance.period.isSalaryCycle) R.plurals.next_balance_until_salary
                    else R.plurals.next_balance_until_month_end,
                    daysLeft, daysLeft
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tinted bar whose width shows the amount in relation to the larger one of income and expenses
 */
@Composable
private fun AmountBar(
    label: String,
    amount: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(vertical = 14.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

internal fun previewBalance() = MonthlyBalance(
    period = BalancePeriod(LocalDate.now().minusDays(22), LocalDate.now().plusDays(8), true),
    incomeBooked = 153684,
    incomeUpcoming = 0,
    contractsBooked = -70000,
    contractsUpcoming = -12526,
    savings = -35000,
    other = -10643,
    incomeTransactionIds = emptyList(),
    savingsTransactionIds = listOf(1),
    otherTransactionIds = listOf(2)
)

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthlyBalanceCardPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            MonthlyBalanceCard(
                state = BalanceUiState.Ready(previewBalance(), LocalDate.now()),
                currency = CurrencyUnit.DebugInstance,
                onOpen = {},
                onConsent = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
