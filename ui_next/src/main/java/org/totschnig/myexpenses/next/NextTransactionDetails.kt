package org.totschnig.myexpenses.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.totschnig.myexpenses.compose.HierarchicalMenu
import org.totschnig.myexpenses.compose.Menu
import org.totschnig.myexpenses.next.contracts.DetailCard
import org.totschnig.myexpenses.next.contracts.InfoRow
import org.totschnig.myexpenses.viewmodel.data.Transaction2
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.totschnig.myexpenses.compose.Icon as CategoryIcon

/**
 * Details of a transaction on a page of its own, laid out like the details of a contract.
 * The actions of the transaction (edit, delete, …) are in the menu at the top.
 *
 * Only the basics for now, the details of transactions are to be designed further.
 *
 * @param menu actions of the transaction
 * @param content added below the details, e.g. the contract the transaction belongs to
 */
@Composable
fun TransactionDetailDialog(
    transaction: Transaction2,
    menu: () -> Menu,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            TransactionDetailScreen(transaction, menu, onDismiss, content)
        }
    }
}

@Composable
private fun TransactionDetailScreen(
    transaction: Transaction2,
    menu: () -> Menu,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.next_back))
            }
            Spacer(Modifier.weight(1f))
            val showMenu = rememberSaveable { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu.value = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.next_transaction_actions))
                }
                HierarchicalMenu(showMenu, remember(transaction, showMenu.value) {
                    if (showMenu.value) menu() else emptyList()
                })
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            DetailCard {
                Header(transaction)
                InfoRow(stringResource(R.string.next_transaction_date), dateFormatter.format(transaction.date))
                transaction.accountLabel?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(stringResource(R.string.next_transaction_account), it)
                }
                transaction.categoryPath?.let {
                    InfoRow(stringResource(R.string.next_contracts_transaction_category), it)
                }
                transaction.party?.name?.let {
                    InfoRow(stringResource(R.string.next_contracts_payees), it)
                }
                transaction.methodLabel?.let {
                    InfoRow(stringResource(R.string.next_transaction_method), it)
                }
                transaction.comment?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(stringResource(R.string.next_transaction_comment), it)
                }
            }
            content()
        }
    }
}

@Composable
private fun Header(transaction: Transaction2) {
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
            val icon = transaction.icon
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
        Column(Modifier.weight(1f)) {
            Text(
                transaction.party?.name ?: transaction.categoryPath ?: "–",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            SignedAmount(transaction.displayAmount, style = MaterialTheme.typography.titleMedium)
        }
    }
}
