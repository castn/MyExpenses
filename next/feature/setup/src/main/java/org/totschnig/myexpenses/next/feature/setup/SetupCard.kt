package org.totschnig.myexpenses.next.feature.setup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.ActionCard
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.setup.SetupOffer

/**
 * The card on the overview that starts or resumes the setup, while it is offered, see
 * [SetupViewModel.offer]; "Skip setup" closes it for good
 */
@Composable
fun SetupCard(viewModel: SetupViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val offer by viewModel.offer.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val current = overview
    if (offer == SetupOffer.HIDDEN || current == null) return
    ActionCard(
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
}
