package org.totschnig.myexpenses.sync

import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.feature.Feature
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

enum class BackendService(
    val id: Int,
    val label: String,
    val feature: Feature?,
    val supportsReconfiguration: Boolean = false
) {
    DRIVE(
        R.id.SYNC_BACKEND_DRIVE,
        "Drive",
        Feature.DRIVE
    ),
    SAF(
        R.id.SYNC_BACKEND_LOCAL,
        "SAF",
        null
    ),
    DROPBOX(
        R.id.SYNC_BACKEND_DROPBOX,
        "Dropbox",
        Feature.DROPBOX
    ),
    ONEDRIVE(
        R.id.SYNC_BACKEND_ONEDRIVE,
        "OneDrive",
        Feature.ONEDRIVE
    ),
    WEBDAV(
        R.id.SYNC_BACKEND_WEBDAV,
        "WebDAV",
        Feature.WEBDAV,
        true
    );

    /**
     * The backends built into this app, see FeatureModule.provideSyncBackendFactories.
     */
    private val factory: SyncBackendProviderFactory?
        get() = MyApplication.instance.appComponent.syncBackendFactories()[this]

    fun isAvailable() = factory != null

    fun instantiate(): Result<SyncBackendProviderFactory> = kotlin.runCatching {
        checkNotNull(factory) { "$label is not part of this build" }
    }.onFailure {
        CrashHandler.report(it)
    }

    fun buildAccountName(extra: String): String {
        return "$label - $extra"
    }

    companion object {
        fun forAccount(account: String) = kotlin.runCatching {
            entries.firstOrNull { account.startsWith(it.label) }
                ?: throw IllegalArgumentException("No Backend found for $account")
        }

        fun allAvailable() = entries.filter { it.isAvailable() }

    }
}
