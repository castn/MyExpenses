package org.totschnig.myexpenses.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.vectorResource

/**
 * Colors of an illustration that follow the theme. The drawables built by
 * illustrations/build_illustrations.py paint them with a placeholder color each, in this order.
 */
enum class IllustrationSlot {
    PRIMARY, PRIMARY_SHADE, SECONDARY, SECONDARY_SHADE, TERTIARY, TERTIARY_SHADE,
    NEUTRAL, NEUTRAL_SHADE, PAPER, LINE, INK;

    val placeholder = 0xFFFE0001.toInt() + ordinal

    fun color(scheme: ColorScheme): Color = when (this) {
        PRIMARY -> scheme.primary
        SECONDARY -> scheme.secondary
        TERTIARY -> scheme.tertiary
        NEUTRAL -> scheme.surfaceVariant
        // a receipt, a card or a screen, as cards of the app
        PAPER -> if (scheme.isLight) scheme.surfaceContainerLowest else scheme.surfaceContainerHighest
        // lines of text on paper
        LINE -> if (scheme.isLight) scheme.outlineVariant else scheme.outline
        INK -> scheme.onSurfaceVariant
        else -> entries[ordinal - 1].color(scheme).shaded()
    }
}

private val ColorScheme.isLight get() = surface.luminance() > 0.5f

private fun Color.shaded() = Color(red * (1 - SHADE), green * (1 - SHADE), blue * (1 - SHADE), alpha)

// same as SHADE in build_illustrations.py
private const val SHADE = 0.22f

/**
 * An illustration from the drawables of the design system, with its placeholders replaced by the
 * colors of the current [MaterialTheme], so that it follows dynamic colors and the dark theme
 */
@Composable
fun illustration(@DrawableRes id: Int): ImageVector {
    val source = ImageVector.vectorResource(id)
    val scheme = MaterialTheme.colorScheme
    return remember(source, scheme) { source.recolored(scheme) }
}

private fun ImageVector.recolored(scheme: ColorScheme): ImageVector {
    val colors = IllustrationSlot.entries.associate { it.placeholder to it.color(scheme) }
    fun Brush?.recolored() =
        if (this is SolidColor) colors[value.toArgb()]?.let { SolidColor(it) } ?: this else this

    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        autoMirror = autoMirror
    )

    fun copy(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorGroup -> {
                    builder.addGroup(
                        node.name, node.rotation, node.pivotX, node.pivotY, node.scaleX, node.scaleY,
                        node.translationX, node.translationY, node.clipPathData
                    )
                    copy(node)
                    builder.clearGroup()
                }

                is VectorPath -> builder.addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = node.fill.recolored(),
                    fillAlpha = node.fillAlpha,
                    stroke = node.stroke.recolored(),
                    strokeAlpha = node.strokeAlpha,
                    strokeLineWidth = node.strokeLineWidth,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = node.strokeLineJoin,
                    strokeLineMiter = node.strokeLineMiter,
                    trimPathStart = node.trimPathStart,
                    trimPathEnd = node.trimPathEnd,
                    trimPathOffset = node.trimPathOffset
                )
            }
        }
    }
    copy(root)
    return builder.build()
}
