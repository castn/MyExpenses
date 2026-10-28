package org.totschnig.mlkit_core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.RecognizedBlock
import org.totschnig.myexpenses.feature.RecognizedElement
import org.totschnig.myexpenses.feature.RecognizedLine
import org.totschnig.myexpenses.feature.RecognizedText
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Runs ML Kit text recognition. ML Kit must be initialized before, either by its init provider
 * or by MlKit.initialize.
 */
object TextRecognizer {

    suspend fun process(image: InputImage, options: TextRecognizerOptionsInterface): Text =
        withContext(Dispatchers.Default) {
            TextRecognition.getClient(options).use { recognizer ->
                suspendCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            }
        }

    /** The photo is read with its EXIF rotation, so boxes refer to the upright image */
    suspend fun recognize(context: Context, uri: Uri, options: TextRecognizerOptionsInterface) =
        process(InputImage.fromFilePath(context, uri), options).toRecognizedText()
}

fun Text.toRecognizedText() = RecognizedText(textBlocks.map { block ->
    RecognizedBlock(block.lines.map { line ->
        RecognizedLine(line.text, line.boundingBox?.toBox(), line.elements.map { element ->
            RecognizedElement(element.text, element.boundingBox?.toBox())
        })
    })
})

private fun android.graphics.Rect.toBox() = Box(left, top, right, bottom)

/** One page as JPEG, cropped and straightened, photos from the gallery allowed */
fun documentScannerOptions(): GmsDocumentScannerOptions = GmsDocumentScannerOptions.Builder()
    .setPageLimit(1)
    .setGalleryImportAllowed(true)
    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
    .build()

/** Photo returned by the scanner started with [documentScannerOptions] */
fun documentScanResult(data: Intent?) =
    GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.firstOrNull()?.imageUri
