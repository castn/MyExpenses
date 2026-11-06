package org.totschnig.fixturecollector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.designsystem.MarkedPhoto
import org.totschnig.myexpenses.designsystem.PhotoMark
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.RecognizedText

/** Long side of the preview, enough to read the receipt on a phone */
private const val PREVIEW_SIZE = 2000

private val blockColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
    Color(0xFF00897B),
    Color(0xFF6D4C41),
    Color(0xFFD81B60)
)

/** Downsampled bitmap and the factor to its full size, which the boxes refer to */
private class Preview(val bitmap: Bitmap, val sampleSize: Int)

/**
 * The photo with the boxes of [text]: one color per block, so that a name column and a price
 * column recognized as separate blocks are told apart, thin frames around the words.
 *
 * @param redactions painted black as in the export, with grey frames around the other lines;
 * null to show the boxes
 * @param onTapLine called with the box of the line tapped, null if tapping does nothing
 */
@Composable
fun ReceiptImage(
    photo: File,
    text: RecognizedText?,
    showBoxes: Boolean,
    modifier: Modifier = Modifier,
    redactions: List<Box>? = null,
    onTapLine: ((Box) -> Unit)? = null
) {
    val preview by produceState<Preview?>(null, photo, photo.lastModified()) {
        value = withContext(Dispatchers.IO) { runCatching { decode(photo) }.getOrNull() }
    }
    val current = preview ?: return
    val bitmap = remember(current) { current.bitmap.asImageBitmap() }
    fun Box.rect() = Rect(
        left / current.sampleSize.toFloat(),
        top / current.sampleSize.toFloat(),
        right / current.sampleSize.toFloat(),
        bottom / current.sampleSize.toFloat()
    )
    val marks = buildList {
        if (redactions != null) {
            if (showBoxes && text != null) {
                text.lines.mapNotNull { it.box }.filter { it !in redactions }
                    .forEach { add(PhotoMark(it.rect(), Color.Gray, 1.dp)) }
            }
            redactions.forEach {
                add(PhotoMark(it.redactionArea().rect(), Color.Black, filled = true))
            }
        } else if (showBoxes && text != null) {
            text.blocks.forEachIndexed { index, block ->
                val color = blockColors[index % blockColors.size]
                block.lines.forEach { line ->
                    line.elements.forEach { element ->
                        element.box?.let {
                            add(PhotoMark(it.rect(), color.copy(alpha = 0.5f), 0.5.dp))
                        }
                    }
                    line.box?.let { add(PhotoMark(it.rect(), color, 1.5.dp)) }
                }
            }
        }
    }
    MarkedPhoto(
        bitmap = bitmap,
        marks = marks,
        modifier = modifier,
        onTap = if (onTapLine == null || text == null) {
            null
        } else {
            { offset ->
                lineAt(
                    text,
                    (offset.x * current.sampleSize).toInt(),
                    (offset.y * current.sampleSize).toInt()
                )?.let(onTapLine)
            }
        }
    )
}

/** Lines are hard to hit exactly on a phone, so the box may be missed by this much */
private const val TAP_TOLERANCE = 15

/** The smallest line whose box contains the point of the photo, as lines may overlap */
private fun lineAt(text: RecognizedText, x: Int, y: Int) = text.lines.mapNotNull { it.box }
    .filter {
        x in it.left - TAP_TOLERANCE..it.right + TAP_TOLERANCE &&
            y in it.top - TAP_TOLERANCE..it.bottom + TAP_TOLERANCE
    }
    .minByOrNull { (it.right - it.left).toLong() * (it.bottom - it.top) }

private fun sampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= PREVIEW_SIZE) sample *= 2
    return sample
}

/** Upright as ML Kit reads it: ImageDecoder applies the EXIF rotation, the scanner writes none */
private fun decode(photo: File): Preview {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        var sample = 1
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(photo)) {
                decoder,
                info,
                _
            ->
            sample = sampleSize(info.size.width, info.size.height)
            decoder.setTargetSampleSize(sample)
        }
        return Preview(bitmap, sample)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(photo.path, bounds)
    val sample = sampleSize(bounds.outWidth, bounds.outHeight)
    val bitmap = BitmapFactory.decodeFile(
        photo.path,
        BitmapFactory.Options().apply {
            inSampleSize =
                sample
        }
    )
    return Preview(bitmap, sample)
}
