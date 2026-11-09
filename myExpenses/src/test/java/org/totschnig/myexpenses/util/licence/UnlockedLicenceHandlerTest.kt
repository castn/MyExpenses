package org.totschnig.myexpenses.util.licence

import com.google.android.vending.licensing.PreferenceObfuscator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class UnlockedLicenceHandlerTest {
    private lateinit var licenceHandler: LicenceHandler

    @Before
    fun setUp() {
        licenceHandler = UnlockedLicenceHandler(
            Mockito.mock(MyApplication::class.java),
            Mockito.mock(PreferenceObfuscator::class.java),
            Mockito.mock(CrashHandler::class.java),
            Mockito.mock(PrefHandler::class.java),
            Mockito.mock(Repository::class.java),
            Mockito.mock(CurrencyFormatter::class.java)
        ).apply { init() }
    }

    @Test
    fun everyFeatureIsUnlocked() {
        ContribFeature.entries.forEach {
            assertThat(licenceHandler.hasAccessTo(it)).isTrue()
        }
    }

    @Test
    fun offersNoUpgrade() {
        assertThat(licenceHandler.isUpgradeable).isFalse()
        assertThat(licenceHandler.isProfessionalEnabled).isTrue()
    }

    @Test
    fun voidingTheLicenceKeepsEverythingUnlocked() = runTest {
        licenceHandler.voidLicenceStatus(keepFeatures = false)
        assertThat(licenceHandler.licenceStatus).isEqualTo(LicenceStatus.PROFESSIONAL)
        assertThat(licenceHandler.hasAccessTo(ContribFeature.BANKING)).isTrue()
    }
}
