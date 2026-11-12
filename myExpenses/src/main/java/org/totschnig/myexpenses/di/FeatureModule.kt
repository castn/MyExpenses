package org.totschnig.myexpenses.di

import androidx.annotation.Keep
import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.activity.MainScreenEntry
import org.totschnig.myexpenses.activity.TemplateScreens
import org.totschnig.myexpenses.activity.TransactionEditorEntry
import org.totschnig.myexpenses.feature.BankingFeature
import org.totschnig.myexpenses.feature.FeatureManager
import org.totschnig.myexpenses.feature.OcrFeature
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.sync.BackendService
import org.totschnig.myexpenses.sync.StorageAccessFrameworkBackendProviderFactory
import org.totschnig.myexpenses.sync.SyncBackendProviderFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Singleton

// workaround for https://issuetracker.google.com/issues/297226570
// force R8 to keep toInstant method
@Keep
object Bogus {
    fun calculateInstant(): Instant = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()
}

@Module
open class FeatureModule {
    /**
     * :myExpenses does not know :ocr, :app overrides this to hand in its implementation.
     */
    @Provides
    open fun provideOcrFeature(prefHandler: PrefHandler): OcrFeature? = null

    /**
     * :myExpenses does not know :fints, :app overrides this to hand in its implementation.
     */
    @Provides
    open fun provideBankingFeature(): BankingFeature? = null

    /**
     * :myExpenses does not know :sqlcrypt, :app overrides this to hand in its implementation.
     */
    @Provides
    open fun provideSqlCryptProvider(): SqlCryptProvider? = null

    /**
     * The sync backends built into this app. :myExpenses only brings the local one, :app adds the
     * cloud backends.
     */
    @Provides
    @Singleton
    open fun provideSyncBackendFactories(): Map<BackendService, SyncBackendProviderFactory> =
        mapOf(BackendService.SAF to StorageAccessFrameworkBackendProviderFactory())

    /**
     * All features are built in, so nothing needs to be installed on demand.
     */
    @Provides
    @Singleton
    open fun provideFeatureManager(): FeatureManager = object : FeatureManager() {}

    /**
     * The main screen. :myExpenses does not know :ui_next, which holds it, so :app overrides this.
     */
    @Provides
    open fun provideMainScreen(): MainScreenEntry =
        error(":myExpenses has no main screen, :app provides it")

    /**
     * The screen for entering and changing transactions, also in :ui_next, so :app overrides this.
     */
    @Provides
    open fun provideTransactionEditor(): TransactionEditorEntry =
        error(":myExpenses has no transaction editor, :app provides it")

    /**
     * The screens of the templates, in :ui_next like the main screen, so :app overrides this.
     */
    @Provides
    open fun provideTemplateScreens(): TemplateScreens =
        error(":myExpenses has no screens of templates, :app provides them")
}
