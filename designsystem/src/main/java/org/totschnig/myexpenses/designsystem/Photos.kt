package org.totschnig.myexpenses.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A frame or an area drawn on a [MarkedPhoto], e.g. around a recognized line of a receipt
 *
 * @param rect in pixels of the photo's bitmap
 * @param width of the frame, ignored if [filled]
 * @param filled covers the area, e.g. with a translucent colour to highlight it or black to hide it
 */
@Immutable
data class PhotoMark(val rect: Rect, val color: Color, val width: Dp = 1.5.dp, val filled: Boolean = false)

/**
 * A photo in the full width with [marks] drawn on it, e.g. a scanned receipt with the lines read
 * from it. Collapsed, it shows a window of [collapsedHeight] around [focus], or its top without
 * one, so that a long receipt does not fill the page.
 *
 * @param collapsedHeight null shows the whole photo
 * @param focus in pixels of the bitmap, centred in the collapsed window
 * @param onTap with the point tapped in pixels of the bitmap, null if tapping does nothing
 */
@Composable
fun MarkedPhoto(
    bitmap: ImageBitmap,
    marks: List<PhotoMark>,
    modifier: Modifier = Modifier,
    collapsedHeight: Dp? = null,
    focus: Rect? = null,
    onTap: ((Offset) -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        // From pixels of the bitmap to pixels on screen
        val scale = widthPx / bitmap.width
        val photoHeightPx = bitmap.height * scale
        val windowPx = collapsedHeight?.let { with(density) { it.toPx() } }?.coerceAtMost(photoHeightPx)
        val shift = if (windowPx == null) 0f else focus?.let { (it.center.y * scale - windowPx / 2).coerceIn(0f, photoHeightPx - windowPx) } ?: 0f
        val frame = if (windowPx == null) Modifier else Modifier.height(with(density) { windowPx.toDp() })
        Box(frame.clipToBounds()) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height(with(density) { photoHeightPx.toDp() })
                    .offset { IntOffset(0, -shift.roundToInt()) }
                    .pointerInput(onTap, scale) {
                        if (onTap == null) return@pointerInput
                        detectTapGestures { onTap(Offset(it.x / scale, it.y / scale)) }
                    }
                    .drawWithContent {
                        drawContent()
                        marks.forEach { mark ->
                            drawRect(
                                color = mark.color,
                                topLeft = Offset(mark.rect.left * scale, mark.rect.top * scale),
                                size = mark.rect.size * scale,
                                style = if (mark.filled) Fill else Stroke(mark.width.toPx())
                            )
                        }
                    }
            )
        }
    }
}
