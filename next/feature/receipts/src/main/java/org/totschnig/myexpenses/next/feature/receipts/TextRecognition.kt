package org.totschnig.myexpenses.next.feature.receipts

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import org.totschnig.myexpenses.feature.RecognizedText

/** The text recognition the user configured in the app, implemented by the shell */
interface TextRecognition {
    /** The text with positions, null if recognition happens in an external app */
    suspend fun recognize(photo: Uri): RecognizedText?

    /** Intent of a document scanner that crops and straightens the photo, null if there is none */
    suspend fun documentScanIntent(activity: Activity): IntentSender?

    /** Photo returned by the scanner of [documentScanIntent] */
    fun documentScanResult(data: Intent?): Uri?
}
