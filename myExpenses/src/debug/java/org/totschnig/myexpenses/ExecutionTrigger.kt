package org.totschnig.myexpenses

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.totschnig.myexpenses.service.AutoBackupWorker
import org.totschnig.myexpenses.service.DailyExchangeRateDownloadService
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.util.doAsync

class ExecutionTrigger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        doAsync {
            when (intent.action) {
                "TRIGGER_SYNC" -> GenericAccountService.requestSync(intent.getStringExtra("ACCOUNT")!!)
                "TRIGGER_BACKUP" -> AutoBackupWorker.enqueue(context)
                "TRIGGER_EXCHANGE_RATE_DOWNLOAD" -> DailyExchangeRateDownloadService.enqueue(context)
            }
        }
    }
}