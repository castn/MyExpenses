package org.totschnig.myexpenses.next.contracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import org.totschnig.myexpenses.compose.transactions.FutureCriterion
import org.totschnig.myexpenses.compose.transactions.TransactionEvent
import org.totschnig.myexpenses.compose.transactions.TransactionEventHandler
import org.totschnig.myexpenses.compose.transactions.TransactionListContent
import org.totschnig.myexpenses.next.NextTransactionList
import org.totschnig.myexpenses.next.R
import org.totschnig.myexpenses.viewmodel.data.HeaderDataEmpty
import org.totschnig.myexpenses.viewmodel.data.Transaction2

/**
 * The debits a contract was detected from, shown like the transactions of an account.
 * The list is fixed to the contract: it can neither be searched nor filtered.
 *
 * @param transactionList renders the list, see [ContractTransactionList]
 */
@Composable
fun ContractTransactionsScreen(
    contract: Contract,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    transactionList: @Composable (Modifier) -> Unit,
) {
    FixedTransactionsScreen(
        title = contract.displayName.ifEmpty { "–" },
        subtitle = pluralStringResource(
            R.plurals.next_contracts_based_on,
            contract.transactions.size,
            contract.transactions.size
        ),
        onBack = onBack,
        modifier = modifier,
        transactionList = transactionList
    )
}

/**
 * A fixed set of transactions with a title, e.g. the debits of a contract
 */
@Composable
fun FixedTransactionsScreen(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    transactionList: @Composable (Modifier) -> Unit,
) {
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.next_back)
                )
            }
            Column(Modifier.padding(start = 4.dp, end = 16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        transactionList(Modifier.weight(1f))
    }
}

private val NoTransactionEvents = object : TransactionEventHandler {
    override fun invoke(event: TransactionEvent, transaction: Transaction2) {}
}

/**
 * Read-only transaction list of the new design. Without day headers, since balances make no sense
 * across accounts, so each transaction shows its date and account.
 */
@Composable
fun ContractTransactionList(
    items: LazyPagingItems<Transaction2>,
    modifier: Modifier = Modifier,
    /** Added to the details of a transaction, which a tap opens */
    detailsContent: (@Composable (Transaction2) -> Unit)? = null,
) {
    val content = remember(items) {
        TransactionListContent(
            lazyPagingItems = items,
            headerData = HeaderDataEmpty,
            selectionHandler = null,
            onEvent = NoTransactionEvents,
            modificationAllowed = false,
            accountCount = 0,
            isFiltered = true,
            futureCriterion = FutureCriterion.EndOfDay
        )
    }
    NextTransactionList(
        content = content,
        modifier = modifier,
        isReadOnly = true,
        showDateAndAccount = true,
        detailsContent = detailsContent
    )
}

/**
 * The debits of [contract] from [viewModel], updated when further debits are detected
 */
@Composable
fun ContractTransactionList(
    viewModel: ContractsViewModel,
    contract: Contract,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(contract.transactions) { viewModel.showTransactionsOf(contract) }
    ContractTransactionList(viewModel.contractTransactions.collectAsLazyPagingItems(), modifier) {
        PaymentContractSection(viewModel, it.id)
    }
}
