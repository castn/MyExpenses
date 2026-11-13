package org.totschnig.myexpenses.next.feature.setup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.ActionCard
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.InviteCard
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.setup.SetupOffer

/**
 * The card on the overview that starts or resumes the setup, see [SetupViewModel.offer]: until the
 * setup was opened a big one, where "Skip setup" closes it for good, afterwards a small one with
 * the steps left that can be put away for a while, see [SetupCardActions]
 */
@Composable
fun SetupCard(viewModel: SetupViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val offer by viewModel.offer.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val asksToStop by viewModel.asksToStop.collectAsStateWithLifecycle()
    val current = overview ?: return
    when (offer) {
        SetupOffer.HIDDEN -> Unit

        SetupOffer.FULL -> ActionCard(
            icon = Icons.Outlined.Checklist,
            title = stringResource(R.string.next_setup_card_title),
            text = stringResource(R.string.next_setup_card_text, minutesText(current.seconds)),
            confirmLabel = stringResource(
                if (current.hasStarted) R.string.next_setup_go_on else R.string.next_setup_start
            ),
            onConfirm = onOpen,
            modifier = modifier,
            dismissLabel = stringResource(R.string.next_setup_skip_all),
            onDismiss = viewModel::close
        )

        SetupOffer.COMPACT -> CompactSetupCard(
            current,
            asksToStop,
            onOpen,
            viewModel,
            onStop = viewModel::close,
            modifier = modifier
        )
    }
}

/** The steps left with their time and how many are done as a ring; × puts it away or asks to stop */
@Composable
private fun CompactSetupCard(
    overview: SetupOverview,
    asksToStop: Boolean,
    onOpen: () -> Unit,
    actions: SetupCardActions,
    onStop: () -> Unit,
    modifier: Modifier
) {
    var isAsking by rememberSaveable { mutableStateOf(false) }
    val left = overview.steps.count { it.state != StepState.DONE }
    val done = overview.steps.size - left
    InviteCard(
        icon = Icons.Outlined.Checklist,
        title = stringResource(R.string.next_setup_name),
        text = pluralStringResource(
            R.plurals.next_setup_steps_left,
            left,
            left,
            minutesText(overview.seconds)
        ),
        onClick = onOpen,
        onDismiss = { if (asksToStop) isAsking = true else actions.dismissCard() },
        dismissLabel = stringResource(R.string.next_setup_remind_later),
        modifier = modifier,
        progress = if (overview.steps.isEmpty()) 0f else done.toFloat() / overview.steps.size
    )
    if (isAsking) {
        ConfirmDialog(
            title = stringResource(R.string.next_setup_stop_title),
            text = stringResource(R.string.next_setup_stop_text),
            confirmLabel = stringResource(R.string.next_setup_stop),
            onConfirm = {
                isAsking = false
                onStop()
            },
            onDismiss = {
                isAsking = false
                actions.dismissCard()
            }
        )
    }
}
