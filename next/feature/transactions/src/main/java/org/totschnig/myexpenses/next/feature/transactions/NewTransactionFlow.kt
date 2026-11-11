package org.totschnig.myexpenses.next.feature.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.PageTopBar
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.transactions.NewTransaction

/**
 * The screen for a new expense or income: the [TransactionForm] with "save", which closes it, and
 * "save and new", which keeps it open for the next transaction.
 *
 * @param prefill what the transaction starts with, unless a draft survived from before
 * @param onClose with the id of the transaction saved last, null if none was saved
 */
@Composable
fun NewTransactionFlow(
    viewModel: TransactionEditViewModel,
    prefill: NewTransaction,
    tagField: TagField?,
    onClose: (createdId: Long?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startNew(prefill) }
    NewTransactionScreen(state, viewModel, tagField, onClose)
}

@Composable
private fun NewTransactionScreen(
    state: TransactionEditState,
    actions: TransactionFormActions,
    tagField: TagField?,
    onClose: (createdId: Long?) -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.next_transaction_saved_all)
    val failedMessage = stringResource(R.string.next_transaction_create_failed)
    // The transaction saved last since the screen opened, e.g. with "save and new"
    var lastCreated by rememberSaveable { mutableStateOf<Long?>(null) }
    val created = state.created
    LaunchedEffect(created) {
        if (created == null) return@LaunchedEffect
        lastCreated = created
        if (state.draft == null) {
            actions.createdShown(created)
            onClose(created)
        } else {
            try {
                snackbar.showSnackbar(savedMessage)
            } finally {
                actions.createdShown(created)
            }
        }
    }
    BackHandler { onClose(lastCreated) }
    LaunchedEffect(state.isSaveFailed) {
        if (state.isSaveFailed) snackbar.showSnackbar(failedMessage)
    }
    val draft = state.draft
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            PageTopBar(
                onBack = { onClose(lastCreated) },
                title = stringResource(R.string.next_transaction_new_title)
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    state.hasNoAccount -> Banner(
                        title = stringResource(R.string.next_transaction_no_account),
                        messages = listOf(
                            stringResource(R.string.next_transaction_no_account_hint)
                        ),
                        modifier = Modifier.padding(Spacing.screen)
                    )

                    draft != null -> TransactionForm(
                        state = state,
                        draft = draft,
                        actions = actions,
                        currencySymbol = state.draftAccount?.currencySymbol.orEmpty(),
                        tagField = tagField
                    )
                }
            }
            if (draft != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { actions.saveDraft(andNew = true) }) {
                        Text(stringResource(R.string.next_transaction_save_and_new))
                    }
                    Button(onClick = { actions.saveDraft() }) {
                        Text(stringResource(R.string.next_transaction_save))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
