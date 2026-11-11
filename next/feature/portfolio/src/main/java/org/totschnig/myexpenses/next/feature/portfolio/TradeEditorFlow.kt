package org.totschnig.myexpenses.next.feature.portfolio

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.LoadingBlock
import org.totschnig.myexpenses.designsystem.PageSubtitle
import org.totschnig.myexpenses.designsystem.PageTopBar
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.portfolio.canSave

/**
 * The screen of a trade: the [TradeForm] with "save", which closes it, and for a new trade "save
 * and new", which keeps it open for the next one, e.g. several buys of one day
 */
@Composable
fun TradeEditorFlow(viewModel: TradeEditViewModel, start: TradeStart, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        when (start) {
            is TradeStart.New -> viewModel.startNew(start.portfolioId, start.kind)
            is TradeStart.Edit -> viewModel.edit(start.tradeId)
        }
    }
    TradeEditorScreen(state, viewModel, isNew = start is TradeStart.New, onClose)
}

@Composable
private fun TradeEditorScreen(
    state: TradeEditState,
    actions: TradeFormActions,
    isNew: Boolean,
    onClose: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val messages = mapOf(
        TradeMessage.SAVED to stringResource(R.string.next_trade_saved),
        TradeMessage.SAVE_FAILED to stringResource(R.string.next_trade_save_failed),
        TradeMessage.ASSET_TAKEN to stringResource(R.string.next_asset_taken)
    )
    LaunchedEffect(state.isDone) {
        if (state.isDone) onClose()
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        try {
            snackbar.showSnackbar(messages.getValue(message))
        } finally {
            actions.messageShown()
        }
    }
    BackHandler(onBack = onClose)
    val draft = state.draft
    val portfolio = state.portfolio
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            PageTopBar(
                onBack = onClose,
                title = stringResource(
                    if (isNew) R.string.next_trade_new_title else R.string.next_trade_edit_title
                ),
                subtitle = portfolio?.label?.let { label -> { PageSubtitle(label) } }
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    state.isNotFound -> Banner(
                        title = stringResource(R.string.next_trade_not_found),
                        messages = emptyList(),
                        modifier = Modifier.padding(Spacing.screen)
                    )

                    draft != null && portfolio != null -> TradeForm(
                        state,
                        draft,
                        portfolio,
                        actions
                    )

                    else -> LoadingBlock(Modifier.padding(Spacing.screen).fillMaxWidth())
                }
            }
            if (draft != null && portfolio != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (draft.isNew) {
                        TextButton(onClick = {
                            actions.save(andNew = true)
                        }, enabled = draft.canSave) {
                            Text(stringResource(R.string.next_trade_save_and_new))
                        }
                    }
                    Button(onClick = { actions.save() }, enabled = draft.canSave) {
                        Text(stringResource(R.string.next_trade_save))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
