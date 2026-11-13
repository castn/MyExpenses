package org.totschnig.myexpenses.next.feature.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.rememberMonthFormatter
import org.totschnig.myexpenses.next.core.ui.shownOrLeave
import org.totschnig.myexpenses.next.core.ui.transactions.FixedTransactionsScreen

/**
 * The debits without category of the payee of [key], to decide on its category. Once they all
 * have one, e.g. given in their details, the page leaves.
 */
@Composable
internal fun SetupTransactionsPage(
    viewModel: SetupViewModel,
    transactions: SetupTransactions,
    key: SetupKey.Transactions,
    onBack: () -> Unit
) {
    val payees by viewModel.payees.collectAsStateWithLifecycle()
    val payee = shownOrLeave(
        payees?.find { it.counterpart.key == key.payeeKey },
        isLoaded = payees != null,
        onGone = onBack
    ) ?: return
    val ids = payee.transactionIds
    LaunchedEffect(ids) { transactions.list.show(key.title, ids) }
    val month = rememberMonthFormatter()
    FixedTransactionsScreen(
        title = key.title,
        subtitle = pluralStringResource(
            R.plurals.next_setup_payee_transactions,
            ids.size,
            ids.size,
            month.format(payee.since)
        ),
        onBack = onBack
    ) {
        transactions.list.List(it, transactions.details)
    }
}
