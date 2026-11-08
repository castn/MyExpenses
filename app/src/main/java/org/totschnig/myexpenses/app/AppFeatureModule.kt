package org.totschnig.myexpenses.app

import org.totschnig.dropbox.sync.DropboxProviderFactory
import org.totschnig.myexpenses.di.FeatureModule
import org.totschnig.myexpenses.di.SqlCryptProvider
import org.totschnig.myexpenses.sync.BackendService
import org.totschnig.myexpenses.sync.SyncBackendProviderFactory
import org.totschnig.onedrive.sync.OneDriveProviderFactory
import org.totschnig.sqlcrypt.SQLiteOpenHelperFactory
import org.totschnig.webdav.sync.WebDavBackendProviderFactory

/**
 * Hands the implementations of the feature modules to the component of :myExpenses, which only
 * knows their interfaces.
 */
class AppFeatureModule : FeatureModule() {
    private val sqlCryptProvider by lazy { SQLiteOpenHelperFactory() }

    override fun provideSqlCryptProvider(): SqlCryptProvider = sqlCryptProvider

    override fun provideSyncBackendFactories(): Map<BackendService, SyncBackendProviderFactory> =
        super.provideSyncBackendFactories() + mapOf(
            BackendService.DROPBOX to DropboxProviderFactory(),
            BackendService.ONEDRIVE to OneDriveProviderFactory(),
            BackendService.WEBDAV to WebDavBackendProviderFactory()
        )
}
