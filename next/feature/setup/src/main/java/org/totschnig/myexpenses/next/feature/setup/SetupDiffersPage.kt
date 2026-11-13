package org.totschnig.myexpenses.next.feature.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.FormatStyle
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.PreviewEntry
import org.totschnig.myexpenses.designsystem.SelectableEntryRow
import org.totschnig.myexpenses.designsystem.SelectionBar
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.categories.LocalCategoryPicker
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.core.ui.shownOrLeave

/**
 * The debits without category of the payee of [key] when they are for different things
 * ("Unterschiedlich"): the user chooses those that belong together and gives them a category,
 * without a rule. Once they all have one, the page leaves.
 */
@Composable
internal fun SetupDiffersPage(
    viewModel: SetupViewModel,
    currency: CurrencyInfo,
    key: SetupKey.Differs,
    onBack: () -> Unit
) {
    val payees by viewModel.payees.collectAsStateWithLifecycle()
    val payee = shownOrLeave(
        payees?.find { it.counterpart.key == key.payeeKey },
        isLoaded = payees != null,
        onGone = onBack
    ) ?: return
    var chosen by rememberSaveable { mutableStateOf(LongArray(0)) }
    // Debits that got a category are no longer open and drop out of the choice
    val open = payee.transactions.map { it.id }.toSet()
    val selected = chosen.filter { it in open }
    val pick = rememberLauncherForActivityResult(LocalCategoryPicker.current) { picked ->
        viewModel.picked(picked?.first)
    }
    val amount = rememberAmountFormat(currency)
    val date = rememberDateFormatter(FormatStyle.SHORT)
    DetailScaffold(
        onBack = onBack,
        title = key.title,
        header = {
            if (selected.isEmpty()) {
                Text(
                    stringResource(R.string.next_setup_differs_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.content)
                )
            } else {
                SelectionBar(
                    text = pluralStringResource(
                        R.plurals.next_setup_transactions_chosen,
                        selected.size,
                        selected.size
                    ),
                    actionLabel = stringResource(R.string.next_setup_choose_category),
                    onAction = {
                        viewModel.pickCategory(PayeeCard(payee), selected)
                        // Expenses only, the payees of the setup are those of debits
                        pick.launch(false)
                    },
                    onCancel = { chosen = LongArray(0) }
                )
            }
        }
    ) {
        DetailCard {
            payee.transactions.forEachIndexed { index, transaction ->
                val isChosen = transaction.id in selected
                SelectableEntryRow(
                    entry = PreviewEntry(
                        date.format(transaction.date),
                        transaction.purpose,
                        amount(transaction.amount)
                    ),
                    selected = isChosen,
                    onToggle = {
                        chosen = if (isChosen) {
                            (selected - transaction.id).toLongArray()
                        } else {
                            (selected + transaction.id).toLongArray()
                        }
                    },
                    isFirst = index == 0
                )
            }
        }
    }
}
