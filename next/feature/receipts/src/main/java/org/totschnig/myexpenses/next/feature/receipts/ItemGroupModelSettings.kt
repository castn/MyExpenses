package org.totschnig.myexpenses.next.feature.receipts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.ProgressRow
import org.totschnig.myexpenses.designsystem.ValueRow
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelSetupStore
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModels

/**
 * The installed model for item groups
 *
 * @param version null if there is none
 * @param source null if it is not known
 */
data class ModelStatus(val version: String?, val source: ItemGroupModelSetupStore.Source?)

/** The model for item groups in the settings */
class ItemGroupModelSettingsViewModel(
    private val models: ItemGroupModels,
    setupStore: ItemGroupModelSetupStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    /** The version of the model in a list of at most one, null until it was read */
    private val version = MutableStateFlow<List<String>?>(null)

    val installer = ItemGroupModelInstaller(models, setupStore, viewModelScope, io, ::readVersion)

    /** null until the stored model was looked at */
    val status: Flow<ModelStatus?> = combine(version, setupStore.source) { version, source ->
        version?.let { ModelStatus(it.firstOrNull(), source) }
    }

    init {
        viewModelScope.launch(io) { readVersion() }
    }

    private fun readVersion() {
        version.value = listOfNotNull(models.version())
    }
}

/** The model as the list of settings shows it, null until it was looked at */
@Composable
fun itemGroupModelValue(viewModel: ItemGroupModelSettingsViewModel): String? {
    val status by viewModel.status.collectAsStateWithLifecycle(null)
    return status?.let { versionText(it) }
}

@Composable
private fun versionText(status: ModelStatus) = status.version
    ?.let { stringResource(R.string.next_item_group_model_version, it) }
    ?: stringResource(R.string.next_item_group_model_not_set_up)

/** Shows the installed model, looks for a newer one and installs a model file from the phone */
@Composable
fun ItemGroupModelPage(viewModel: ItemGroupModelSettingsViewModel, title: String, onBack: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle(null)
    val setup by viewModel.installer.setup.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel.installer::install)
    }
    val busy = setup == ModelSetup.Working || setup == ModelSetup.Checking
    DetailScaffold(onBack = onBack, title = title) {
        DetailCard(Modifier.padding(top = 8.dp)) {
            status?.let {
                InfoRow(stringResource(R.string.next_item_group_model_installed), versionText(it), isFirst = true)
                if (it.version != null) {
                    when (it.source) {
                        ItemGroupModelSetupStore.Source.DOWNLOAD -> R.string.next_item_group_model_source_download
                        ItemGroupModelSetupStore.Source.FILE -> R.string.next_item_group_model_source_file
                        null -> null
                    }?.let { source -> HintRow(stringResource(source)) }
                }
            }
            ValueRow(
                stringResource(R.string.next_item_group_model_check),
                stringResource(R.string.next_item_group_model_check_hint),
                onClick = viewModel.installer::checkForUpdate,
                enabled = !busy
            )
            ValueRow(
                stringResource(R.string.next_item_group_model_pick_file),
                stringResource(R.string.next_item_group_model_pick_file_hint),
                // The file has no registered type, so anything can be picked and is checked afterwards
                onClick = { pickFile.launch(arrayOf("*/*")) },
                enabled = !busy
            )
            when (val state = setup) {
                ModelSetup.Checking -> ProgressRow(stringResource(R.string.next_item_group_model_checking))
                ModelSetup.Working -> ProgressRow(stringResource(R.string.next_item_group_model_working))
                ModelSetup.Done -> HintRow(stringResource(R.string.next_item_group_model_done))
                ModelSetup.UpToDate -> HintRow(stringResource(R.string.next_item_group_model_up_to_date))
                is ModelSetup.UpdateAvailable -> ValueRow(
                    stringResource(R.string.next_item_group_model_download_version, state.version),
                    null,
                    onClick = viewModel.installer::download
                )
                is ModelSetup.Failed -> HintRow(stringResource(state.reason.text), isWarning = true)
                null -> {}
            }
            HintRow(stringResource(R.string.next_item_group_model_text))
        }
    }
}
