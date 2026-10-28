package org.totschnig.myexpenses.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.evernote.android.state.State
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment
import org.totschnig.myexpenses.dialog.RestoreFromCloudDialogFragment
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.isOnboardingCompleted
import org.totschnig.myexpenses.provider.KEY_SYNC_ACCOUNT_NAME
import org.totschnig.myexpenses.sync.json.AccountMetaData
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.distrib.DistributionHelper.versionNumber
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_BACKUP_FROM_SYNC
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_PASSWORD
import org.totschnig.myexpenses.viewmodel.SyncViewModel.SyncAccountData
import org.totschnig.myexpenses.widget.updateAllWidgets

/**
 * What every first start of the app does, whatever it looks like: setting up from a backup,
 * from sync accounts or from a bank, and opening the main screen once done.
 * Hook for alternative UIs (module :ui_next) next to [OnboardingActivity].
 */
abstract class OnboardingHostActivity : SyncBackendSetupActivity() {

    private val startBanking =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                start()
            }
        }

    @State
    var accountName: String? = null

    /** The main screen opened once the app is set up */
    protected open val mainScreenClass: Class<*> = MyExpensesV2::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefHandler.isOnboardingCompleted) {
            // Set up already, e.g. from a backup, and started again: subclasses return when [isFinishing]
            startActivity(Intent(this, mainScreenClass))
            finish()
            return
        }
        if (savedInstanceState == null) {
            try {
                prefHandler.setDefaultValues(this)
            } catch (e: Exception) {
                //According to report, setDefaultValues fails in some scenario
                //where there is a value of the wrong type already present
                //java.lang.ClassCastException: java.lang.Boolean cannot be cast to java.lang.String
                //maybe when data is restored via Play Store app backup
                CrashHandler.report(e)
            }
        }
    }

    fun start() {
        prefHandler.putInt(PrefKey.CURRENT_VERSION, versionNumber)
        prefHandler.putInt(PrefKey.FIRST_INSTALL_VERSION, versionNumber)
        updateAllWidgets(this)
        val intent = Intent(this, mainScreenClass)
        startActivity(intent)
        finish()
    }

    override fun restartAfterRestore() {
        (application as MyApplication).invalidateHomeCurrency()
        if (!isFinishing) {
            finishAffinity()
            startActivity(Intent(this, mainScreenClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }

    override val createAccountTaskShouldReturnBackups = true

    override val createAccountTaskShouldQueryLocalAccounts = false

    /** The sync accounts on this device have changed, e.g. a new one was set up */
    protected open fun onSyncAccountsChanged() {}

    /** The user did not agree to the encryption of the database */
    protected open fun onEncryptionCancelled() {}

    override fun onReceiveSyncAccountData(data: SyncAccountData) {
        lifecycleScope.launchWhenResumed {
            onSyncAccountsChanged()
            accountName = data.accountName
            if (data.backups.isNotEmpty() || data.remoteAccounts.isNotEmpty()) {
                if (checkForDuplicateUuids(data.remoteAccounts)) {
                    showSnackBar("Found accounts with duplicate uuids")
                } else {
                    RestoreFromCloudDialogFragment.newInstance(data.backups, data.remoteAccounts)
                        .show(supportFragmentManager, FRAGMENT_TAG_RESTORE)
                }
            } else {
                showSnackBar("Neither backups nor sync accounts found")
            }
        }
    }

    override fun onPostRestoreTask(result: Result<Unit>) {
        super.onPostRestoreTask(result)
        result.onSuccess {
            restartAfterRestore()
        }
    }

    fun setupFromBackup(backup: String?, password: String?) {
        prefHandler.isOnboardingCompleted = true
        doRestore(Bundle(4).apply {
            putString(KEY_SYNC_ACCOUNT_NAME, accountName)
            putString(KEY_BACKUP_FROM_SYNC, backup)
            putString(KEY_PASSWORD, password)
        })
    }

    fun setupFromSyncAccounts(syncAccounts: List<AccountMetaData>) {
        prefHandler.isOnboardingCompleted = true
        doWithEncryptionCheck {
            showSnackBarIndefinite(R.string.progress_dialog_fetching_data_from_sync_backend)
            syncViewModel.setupFromSyncAccounts(syncAccounts.map { it.uuid }, accountName!!)
                .observe(this) { result ->
                    dismissSnackBar()
                    result.onSuccess {
                        start()
                    }.onFailure {
                        showDismissibleSnackBar(it.safeMessage)
                    }
                }
        }
    }

    override fun onNegative(args: Bundle) {
        if (args.getInt(ConfirmationDialogFragment.KEY_COMMAND_NEGATIVE) == R.id.ENCRYPT_CANCEL_COMMAND) {
            prefHandler.putBoolean(PrefKey.ENCRYPT_DATABASE, false)
            onEncryptionCancelled()
        }
    }

    override fun startBanking() {
        bankingFeature.bankingActivityClass?.let {
            startBanking.launch(Intent(this, bankingFeature.bankingActivityClass))
        } ?: run {
            showSnackBar("BankingFeature not installed")
        }
    }
}
