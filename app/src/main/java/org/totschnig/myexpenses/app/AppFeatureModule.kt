package org.totschnig.myexpenses.app

import org.totschnig.dropbox.sync.DropboxProviderFactory
import org.totschnig.fints.BankingFeatureImpl
import org.totschnig.myexpenses.activity.MainScreenEntry
import org.totschnig.myexpenses.di.FeatureModule
import org.totschnig.myexpenses.di.SqlCryptProvider
import org.totschnig.myexpenses.feature.BankingFeature
import org.totschnig.myexpenses.feature.OcrFeature
import org.totschnig.myexpenses.next.MainActivity
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.sync.BackendService
import org.totschnig.myexpenses.sync.SyncBackendProviderFactory
import org.totschnig.ocr.OcrFeatureImpl
import org.totschnig.onedrive.sync.OneDriveProviderFactory
import org.totschnig.sqlcrypt.SQLiteOpenHelperFactory
import org.totschnig.webdav.sync.WebDavBackendProviderFactory

/**
 * Hands the implementations of the feature modules and the main screen of :ui_next to the component
 * of :myExpenses, which only knows their interfaces.
 */
class AppFeatureModule : FeatureModule() {
    private val sqlCryptProvider by lazy { SQLiteOpenHelperFactory() }
    private var ocrFeature: OcrFeature? = null
    private val bankingFeature by lazy { BankingFeatureImpl() }

    override fun provideOcrFeature(prefHandler: PrefHandler): OcrFeature =
        ocrFeature ?: OcrFeatureImpl(prefHandler).also { ocrFeature = it }

    override fun provideBankingFeature(): BankingFeature = bankingFeature

    override fun provideSqlCryptProvider(): SqlCryptProvider = sqlCryptProvider

    override fun provideMainScreen() = MainScreenEntry(MainActivity::class.java)

    override fun provideSyncBackendFactories(): Map<BackendService, SyncBackendProviderFactory> =
        super.provideSyncBackendFactories() + mapOf(
            BackendService.DROPBOX to DropboxProviderFactory(),
            BackendService.ONEDRIVE to OneDriveProviderFactory(),
            BackendService.WEBDAV to WebDavBackendProviderFactory()
        )
}
