package org.totschnig.myexpenses.next.feature.receipts

import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.data.receipts.InvalidItemGroupModel
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelSetupStore
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModels
import timber.log.Timber

/** Downloading or installing the model for item groups */
sealed interface ModelSetup {
    data object Working : ModelSetup

    /** Installed, the items of the open receipt are classified again */
    data object Done : ModelSetup

    /** Looking for a newer model, see [ItemGroupModelInstaller.checkForUpdate] */
    data object Checking : ModelSetup

    /** The installed model is the one offered */
    data object UpToDate : ModelSetup

    /** Another model than the installed one is offered, not yet downloaded */
    data class UpdateAvailable(val version: String) : ModelSetup

    data class Failed(val reason: Reason) : ModelSetup

    enum class Reason {
        /** No connection, or the download broke off */
        Network,
        /** No model for this version of the app offered */
        NoRelease,
        /** The picked file is no model or cannot be read */
        Invalid,
        /** The model needs a newer version of the app */
        Incompatible
    }
}

/**
 * Sets up the model for item groups for [ReceiptViewModel] and the settings: downloads it or
 * installs a file the user picked, keeps a downloaded one up to date and offers it until the user
 * declines.
 *
 * @param scope of the view model
 * @param onInstalled called on [io] once a new model is installed, e.g. to classify the open
 * receipt again
 */
class ItemGroupModelInstaller(
    private val models: ItemGroupModels,
    private val setupStore: ItemGroupModelSetupStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onInstalled: () -> Unit,
) {
    /** null until the stored model was looked at */
    private val hasModel = MutableStateFlow<Boolean?>(null)

    private val _setup = MutableStateFlow<ModelSetup?>(null)

    val setup: StateFlow<ModelSetup?> = _setup.asStateFlow()

    /** Offers to set up the model for item groups: there is none and the user did not decline */
    val offer: Flow<Boolean> =
        combine(hasModel, setupStore.declined) { has, declined -> has == false && !declined }

    init {
        scope.launch(io) { hasModel.value = models.model() != null }
    }

    /** Downloads the model for the language of the device, from then on it is kept up to date */
    fun download() = setUp(ItemGroupModelSetupStore.Source.DOWNLOAD, ModelSetup.Reason.Network) {
        models.download(models.latestRelease() ?: throw NoModelRelease())
    }

    /** A model file the user downloaded before, e.g. into the download folder; not updated */
    fun install(uri: Uri) = setUp(ItemGroupModelSetupStore.Source.FILE, ModelSetup.Reason.Invalid) {
        models.install(uri)
    }

    /**
     * Looks for another model than the installed one when the user asks for it; [download] gets it.
     * Nothing is replaced on its own, as a file the user picked may be newer than the release.
     */
    fun checkForUpdate() {
        _setup.value = ModelSetup.Checking
        scope.launch(io) {
            _setup.value = try {
                val release = models.latestRelease()
                when (release?.version) {
                    null -> ModelSetup.Failed(ModelSetup.Reason.NoRelease)
                    models.version() -> ModelSetup.UpToDate
                    else -> ModelSetup.UpdateAvailable(release.version)
                }
            } catch (e: Exception) {
                Timber.w(e, "Looking for item group model failed")
                ModelSetup.Failed(ModelSetup.Reason.Network)
            }
        }
    }

    /**
     * Installs a model and reports it in [setup]
     *
     * @param ioFailure reason to show when reading fails
     */
    private fun setUp(source: ItemGroupModelSetupStore.Source, ioFailure: ModelSetup.Reason, install: () -> Unit) {
        _setup.value = ModelSetup.Working
        scope.launch(io) {
            _setup.value = try {
                install()
                setupStore.installed(source, System.currentTimeMillis())
                hasModel.value = true
                onInstalled()
                ModelSetup.Done
            } catch (e: NoModelRelease) {
                ModelSetup.Failed(ModelSetup.Reason.NoRelease)
            } catch (e: InvalidItemGroupModel) {
                Timber.w(e)
                ModelSetup.Failed(
                    if (e.reason == InvalidItemGroupModel.Reason.Incompatible) ModelSetup.Reason.Incompatible
                    else ModelSetup.Reason.Invalid
                )
            } catch (e: IOException) {
                Timber.w(e)
                ModelSetup.Failed(ioFailure)
            } catch (e: Exception) {
                Timber.e(e)
                ModelSetup.Failed(ModelSetup.Reason.Invalid)
            }
        }
    }

    fun dismiss() {
        _setup.value = null
    }

    fun decline() {
        scope.launch { setupStore.decline() }
    }

    /**
     * Notices a model set up in the settings meanwhile. A downloaded model is replaced by a newer
     * release, looked for at most once a week when receipts are used. A model the app cannot read
     * any more after an update is replaced, too.
     */
    fun updateOccasionally() {
        scope.launch(io) {
            hasModel.value = models.model() != null
            if (!setupStore.claimUpdateCheck(System.currentTimeMillis())) return@launch
            try {
                val release = models.latestRelease() ?: return@launch
                if (release.version != models.model()?.version) {
                    models.download(release)
                    hasModel.value = true
                }
            } catch (e: Exception) {
                Timber.w(e, "Updating item group model failed")
            }
        }
    }

    /** The manifest offers no model this app can read */
    private class NoModelRelease : Exception()
}
