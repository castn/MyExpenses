package org.totschnig.myexpenses.testutils

import android.content.Context
import org.totschnig.myexpenses.di.FeatureModule
import org.totschnig.myexpenses.feature.Feature
import org.totschnig.myexpenses.feature.FeatureManager

object TestFeatureModule: FeatureModule() {
    override fun provideFeatureManager() = TestFeatureManager
}

object TestFeatureManager: FeatureManager() {
    override fun isFeatureInstalled(feature: Feature, context: Context) =
        feature == Feature.SQLCRYPT // we assume running with sqlcrypt installed, provideSQLiteOpenHelper in TestDataModule mocks an encrypted database just by virtue of the file name "data.enc"
}

