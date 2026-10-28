package org.totschnig.myexpenses.next.receipts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.totschnig.myexpenses.feature.OcrFeature
import org.totschnig.myexpenses.next.feature.receipts.TextRecognition

/** [TextRecognition] with the OCR feature of the app */
class OcrTextRecognition(private val context: Context, private val ocrFeature: OcrFeature) : TextRecognition {
    override suspend fun recognize(photo: Uri) = ocrFeature.recognize(context, photo)

    override suspend fun documentScanIntent(activity: Activity) = ocrFeature.documentScanIntent(activity)

    override fun documentScanResult(data: Intent?) = ocrFeature.documentScanResult(data)
}
