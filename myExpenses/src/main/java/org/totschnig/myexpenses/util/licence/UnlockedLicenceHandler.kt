package org.totschnig.myexpenses.util.licence

import android.app.Application
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.ICurrencyFormatter
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

/**
 * The fork has no paywall: every feature is unlocked and no licence can change that. The checks
 * against the licence stay in the code until they are removed.
 */
class UnlockedLicenceHandler(
    context: Application,
    licenseStatusPrefs: PreferenceObfuscator,
    crashHandler: CrashHandler,
    prefHandler: PrefHandler,
    repository: Repository,
    currencyFormatter: ICurrencyFormatter
) : LicenceHandler(
    context,
    licenseStatusPrefs,
    crashHandler,
    prefHandler,
    repository,
    currencyFormatter
) {
    override fun init() {
        licenceStatus = LicenceStatus.PROFESSIONAL
    }

    override fun isEnabledFor(licenceStatus: LicenceStatus) = true

    override suspend fun voidLicenceStatus(keepFeatures: Boolean) {}

    override suspend fun updateLicenceStatus(licence: Licence) {}
}
