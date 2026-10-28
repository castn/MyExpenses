package org.totschnig.myexpenses.next.onboarding

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BackupRestoreActivity
import org.totschnig.myexpenses.activity.OnboardingHostActivity
import org.totschnig.myexpenses.designsystem.PickDialog
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.next.MyExpensesNext
import org.totschnig.myexpenses.next.NextTheme
import org.totschnig.myexpenses.next.merchants.MerchantSettings
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.TimePreference
import org.totschnig.myexpenses.sync.BackendService
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.util.PermissionHelper
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.distrib.DistributionHelper.distribution
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.CurrencyViewModel
import org.totschnig.myexpenses.viewmodel.OnBoardingDataViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.totschnig.myexpenses.next.core.common.R as NextR

/**
 * The first start of the app in the new UI: a few pages instead of the forms of [org.totschnig.myexpenses.activity.OnboardingActivity]
 */
class NextOnboarding : OnboardingHostActivity() {

    override val mainScreenClass: Class<*> = MyExpensesNext::class.java

    override val snackBarContainerId = android.R.id.content

    private val merchantLogoConsent by lazy { MerchantSettings.consent(this) }

    private val currencyViewModel: CurrencyViewModel by viewModels()
    private val dataViewModel: OnBoardingDataViewModel by viewModels()

    /** Stored at once on every change, so that skipping keeps the defaults */
    private var privacyChoices by mutableStateOf<PrivacyChoices?>(null)

    private var isStarting by mutableStateOf(false)

    /** Where sync can restore from, shown while the user picks one */
    private var syncTargets by mutableStateOf<List<SyncTarget>?>(null)

    /** An account of a sync service on this device or a service to set one up with */
    private class SyncTarget(val label: String, val isSetUp: Boolean, val onPick: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        injector.inject(currencyViewModel)
        injector.inject(dataViewModel)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                merchantLogoConsent.isGiven.collect { privacyChoices = readPrivacyChoices(it) }
            }
        }
        dataViewModel.accountSave.observe(this) { result ->
            result.onSuccess {
                start()
            }.onFailure {
                isStarting = false
                CrashHandler.report(it)
                showSnackBar(it.safeMessage)
            }
        }
        val initialDraft = AccountDraft(getString(R.string.default_account_name), currencyViewModel.default.code, "")
        val accountChoices = AccountChoices(currencies = currencyViewModel.currenciesFromEnum)
        val offersBanking = resources.getBoolean(R.bool.fints)
        setContent {
            NextTheme {
                OnboardingScreen(
                    privacyChoices = privacyChoices,
                    onPrivacyChange = ::onPrivacyChange,
                    offersBanking = offersBanking,
                    onRestore = ::onRestore,
                    initialDraft = initialDraft,
                    accountChoices = accountChoices,
                    isStarting = isStarting,
                    onStart = ::createAccountAndStart
                )
                syncTargets?.let { targets ->
                    PickDialog(
                        title = stringResource(NextR.string.next_onboarding_sync_title),
                        options = targets,
                        label = { it.label },
                        supportingText = {
                            stringResource(if (it.isSetUp) NextR.string.next_onboarding_sync_existing else NextR.string.next_onboarding_sync_new)
                        },
                        onSelect = {
                            syncTargets = null
                            it.onPick()
                        },
                        onDismiss = { syncTargets = null }
                    )
                }
            }
        }
    }

    private fun readPrivacyChoices(merchantLogos: Boolean) = PrivacyChoices(
        offersReporting = distribution.supportsTrackingAndCrashReporting,
        autoBackupTime = LocalTime.of(TimePreference.DEFAULT_VALUE / 100, TimePreference.DEFAULT_VALUE % 100)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
        checked = buildSet {
            if (prefHandler.getBoolean(PrefKey.CRASHREPORT_ENABLED, true)) add(PrivacySetting.CRASH_REPORTS)
            if (prefHandler.getBoolean(PrefKey.TRACKING, true)) add(PrivacySetting.TRACKING)
            if (merchantLogos) add(PrivacySetting.MERCHANT_LOGOS)
            if (prefHandler.getBoolean(PrefKey.AUTO_BACKUP, false)) add(PrivacySetting.AUTO_BACKUP)
            if (prefHandler.encryptDatabase) add(PrivacySetting.ENCRYPTION)
        }
    )

    private fun onPrivacyChange(setting: PrivacySetting, checked: Boolean) {
        when (setting) {
            PrivacySetting.CRASH_REPORTS -> {
                prefHandler.putBoolean(PrefKey.CRASHREPORT_ENABLED, checked)
                crashHandler.setEnabled(checked)
            }

            PrivacySetting.TRACKING -> {
                prefHandler.putBoolean(PrefKey.TRACKING, checked)
                tracker.setEnabled(checked)
            }

            PrivacySetting.MERCHANT_LOGOS -> {
                lifecycleScope.launch { merchantLogoConsent.set(checked) }
                return // the flow of the consent updates the choices
            }

            PrivacySetting.AUTO_BACKUP -> {
                prefHandler.putBoolean(PrefKey.AUTO_BACKUP, checked)
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermission(PermissionHelper.PERMISSIONS_REQUEST_NOTIFICATIONS_AUTO_BACKUP)
                }
            }

            PrivacySetting.ENCRYPTION -> {
                prefHandler.putBoolean(PrefKey.ENCRYPT_DATABASE, checked)
                if (checked) requireSqlCrypt()
            }
        }
        privacyChoices = privacyChoices?.let { choices ->
            choices.copy(checked = if (checked) choices.checked + setting else choices.checked - setting)
        }
    }

    private fun onRestore(choice: StartChoice) {
        when (choice) {
            StartChoice.NEW_ACCOUNT -> {}

            StartChoice.BACKUP_FILE -> withResultCallbackLauncher.launch(
                Intent(this, BackupRestoreActivity::class.java).apply {
                    action = BackupRestoreActivity.ACTION_RESTORE
                }
            )

            StartChoice.SYNC -> syncTargets =
                GenericAccountService.getAccountNames(this).map { name ->
                    SyncTarget(name, isSetUp = true) { fetchAccountData(name) }
                } + BackendService.allAvailable(this).map { service ->
                    SyncTarget(service.label, isSetUp = false) { startSetup(service.id) }
                }

            StartChoice.BANK -> contribFeatureCalled(ContribFeature.BANKING, null)
        }
    }

    private fun createAccountAndStart(draft: AccountDraft) {
        val currency = currencyContext[draft.currency]
        val openingBalance = draft.openingBalance?.let { toMinorUnits(it, currency.fractionDigits) } ?: run {
            showSnackBar(NextR.string.next_onboarding_amount_invalid)
            return
        }
        doWithEncryptionCheck {
            isStarting = true
            prefHandler.putString(PrefKey.HOME_CURRENCY, currency.code)
            // Only now, so that the database is opened after the user decided about its encryption
            lifecycleScope.launch {
                val cashAccountType = dataViewModel.accountTypesRaw.first().find { it.isCashAccount }
                dataViewModel.saveAccount(
                    Account(
                        label = draft.label.trim().ifEmpty { getString(R.string.default_account_name) },
                        currency = currency.code,
                        openingBalance = openingBalance,
                        type = cashAccountType,
                        color = dataViewModel.accountColor
                    )
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu) = false //skip help
}
