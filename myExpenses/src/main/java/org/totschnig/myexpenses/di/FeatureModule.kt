package org.totschnig.myexpenses.di

import androidx.annotation.Keep
import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.feature.BankingFeature
import org.totschnig.myexpenses.feature.FeatureManager
import org.totschnig.myexpenses.feature.OcrFeature
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.sync.BackendService
import org.totschnig.myexpenses.sync.StorageAccessFrameworkBackendProviderFactory
import org.totschnig.myexpenses.sync.SyncBackendProviderFactory
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.distrib.DistributionHelper
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

    @Provides
    @Singleton
    open fun provideFeatureManager(prefHandler: PrefHandler): FeatureManager = try {
        Class.forName("org.totschnig.myexpenses.util.locale.PlatformSplitManager")
                .getConstructor(PrefHandler::class.java)
                .newInstance(prefHandler) as FeatureManager
    } catch (e: Exception) {
        if (DistributionHelper.distribution != DistributionHelper.Distribution.GITHUB) {
            CrashHandler.report(e)
        }
        object : FeatureManager() {}
    }
}
