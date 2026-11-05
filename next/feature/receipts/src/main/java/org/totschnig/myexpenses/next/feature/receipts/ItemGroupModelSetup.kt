package org.totschnig.myexpenses.next.feature.receipts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.FormDialog
import org.totschnig.myexpenses.designsystem.ProgressRow
import org.totschnig.myexpenses.next.core.common.R

/** Invites to set up the model while there are items without group */
@Composable
fun ModelOfferBanner(onSetUp: () -> Unit, onDecline: () -> Unit) {
    Banner(
        title = stringResource(R.string.next_item_group_model_offer_title),
        messages = listOf(stringResource(R.string.next_item_group_model_offer_text)),
        actions = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.next_item_group_model_decline)) }
            TextButton(onClick = onSetUp) { Text(stringResource(R.string.next_item_group_model_set_up)) }
        }
    )
}

/**
 * Downloads the model or installs a model file that is already on the phone, e.g. in the download
 * folder. Closes on its own once the model is installed.
 */
@Composable
fun ModelSetupDialog(viewModel: ReceiptViewModel, state: ModelSetup?, onDismiss: () -> Unit) {
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel.modelInstaller::install)
    }
    val working = state == ModelSetup.Working
    FormDialog(
        title = stringResource(R.string.next_item_group_model_title),
        onConfirm = viewModel.modelInstaller::download,
        onDismiss = onDismiss,
        confirmEnabled = !working,
        confirmLabel = stringResource(R.string.next_item_group_model_download),
        secondaryLabel = stringResource(R.string.next_item_group_model_pick_file),
        // The file has no registered type, so anything can be picked and is checked afterwards
        onSecondary = { if (!working) pickFile.launch(arrayOf("*/*")) }
    ) {
        Text(stringResource(R.string.next_item_group_model_text))
        Text(stringResource(R.string.next_item_group_model_source_text))
        when (state) {
            ModelSetup.Working -> ProgressRow(stringResource(R.string.next_item_group_model_working), isFirst = true)
            is ModelSetup.Failed -> Text(stringResource(state.reason.text), color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}

/** What to tell the user */
internal val ModelSetup.Reason.text: Int
    get() = when (this) {
        ModelSetup.Reason.Network -> R.string.next_item_group_model_error_network
        ModelSetup.Reason.NoRelease -> R.string.next_item_group_model_error_no_release
        ModelSetup.Reason.Invalid -> R.string.next_item_group_model_error_invalid
        ModelSetup.Reason.Incompatible -> R.string.next_item_group_model_error_incompatible
    }
