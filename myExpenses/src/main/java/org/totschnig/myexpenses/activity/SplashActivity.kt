package org.totschnig.myexpenses.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.isOnboardingCompleted
import org.totschnig.myexpenses.widget.EXTRA_START_FROM_WIDGET_DATA_ENTRY
import kotlin.system.exitProcess

@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as? MyApplication)?.also {
            val prefHandler = it.appComponent.prefHandler()
            val isDataEntry = intent.getBooleanExtra(EXTRA_START_FROM_WIDGET_DATA_ENTRY, false)
            // The main screen starts the first start itself, when the app is not set up yet
            val targetIntent = if (prefHandler.isOnboardingCompleted && isDataEntry) {
                // The launcher shortcut for a new transaction
                newTransactionIntent().asDataEntryFromWidget()
            } else {
                mainScreenIntent()
            }
            startActivity(targetIntent.apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
        } ?: run {
            Handler().post { exitProcess(0) }
        }
        finish()
    }
}
