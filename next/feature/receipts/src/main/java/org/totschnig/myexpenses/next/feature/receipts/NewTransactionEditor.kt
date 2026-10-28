package org.totschnig.myexpenses.next.feature.receipts

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.staticCompositionLocalOf

/** The screen of the app for a new expense, filled from a receipt; its result is whether it was saved */
typealias NewTransactionEditor = ActivityResultContract<NewTransactionPrefill, Boolean>

/** Where the app provides none, e.g. in previews: saves nothing */
private object NoNewTransactionEditor : NewTransactionEditor() {
    override fun createIntent(context: Context, input: NewTransactionPrefill) = Intent()
    override fun parseResult(resultCode: Int, intent: Intent?) = false
}

/** The screen of the app for a new expense, see [NewTransactionEditor] */
val LocalNewTransactionEditor = staticCompositionLocalOf<NewTransactionEditor> { NoNewTransactionEditor }
