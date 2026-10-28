package org.totschnig.myexpenses.next.feature.budgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat

/**
 * Part of the details of a transaction of the current period: the pot it is paid from and what
 * is left in it. Nothing for a transaction in no pot.
 */
@Composable
fun PotSection(viewModel: BudgetViewModel, currency: CurrencyInfo, transactionId: Long) {
    val status by remember(transactionId) { viewModel.potOf(transactionId) }.collectAsStateWithLifecycle(null)
    val pot = status ?: return
    val amount = rememberAmountFormat(currency)
    SectionTitle(stringResource(R.string.next_budget_title))
    DetailCard {
        PotRow(pot, amount, isFirst = true, onClick = null)
        if (transactionId in pot.oneOffIds) {
            HintRow(stringResource(R.string.next_budget_one_off_hint))
        }
    }
}
