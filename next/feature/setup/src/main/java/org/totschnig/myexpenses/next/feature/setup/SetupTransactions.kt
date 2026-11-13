package org.totschnig.myexpenses.next.feature.setup

import androidx.compose.runtime.Composable
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.core.ui.transactions.FixedTransactions

/**
 * How the setup shows the transactions of a payee, from the shell
 *
 * @param details what the areas add to the details of a transaction
 */
class SetupTransactions(
    val list: FixedTransactions,
    val details: (@Composable (ShownTransaction) -> Unit)? = null
)
