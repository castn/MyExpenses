package org.totschnig.myexpenses.next.feature.receipts

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import timber.log.Timber

/** Loads the photo of a receipt for [ReceiptViewModel] */
class ReceiptPhotoLoader(private val contentResolver: ContentResolver) {

    /**
     * Decodes the photo upright, as text recognition sees it, but reduced, so that the excerpts
     * of a long receipt fit into memory
     */
    fun load(uri: Uri): ReceiptPhoto? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_PHOTO_SIZE) sampleSize *= 2
        val decoded = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }
        val rotation = contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        decoded?.let { bitmap ->
            val upright = if (rotation == 0f) bitmap else Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true
            )
            ReceiptPhoto(uri, upright, 1f / sampleSize)
        }
    } catch (e: Exception) {
        Timber.w(e, "Loading photo of receipt failed")
        null
    }

    private companion object {
        const val MAX_PHOTO_SIZE = 2400
    }
}
