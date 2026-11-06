package org.totschnig.fixturecollector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import org.totschnig.myexpenses.feature.Box

/**
 * The area painted over: recognition draws boxes tight and sometimes beside the first or last
 * letter, so a quarter of the height is added on each side
 */
fun Box.redactionArea(): Box {
    val margin = maxOf(6, (bottom - top) / 4)
    return Box(left - margin, top - margin, right + margin, bottom + margin)
}

/**
 * The photo as JPEG with [boxes] painted black. Decoded upright like ML Kit reads it, so the boxes
 * fit; the result carries no EXIF data, neither rotation nor location.
 */
fun blacken(photo: File, boxes: List<Box>): ByteArray {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(photo)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
    } else {
        BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inMutable = true })
    }
    val paint = Paint().apply { color = android.graphics.Color.BLACK }
    Canvas(bitmap).apply {
        boxes.map { it.redactionArea() }.forEach { area ->
            drawRect(
                area.left.toFloat(),
                area.top.toFloat(),
                area.right.toFloat(),
                area.bottom.toFloat(),
                paint
            )
        }
    }
    return ByteArrayOutputStream().also {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
    }.toByteArray()
        .also { bitmap.recycle() }
}
