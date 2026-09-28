package org.totschnig.myexpenses.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Distances used throughout the new UI
 */
object Spacing {
    /** Between the edge of the screen and cards */
    val screen = 16.dp

    /** Inside of rows and cards */
    val content = 16.dp

    /** Above a section title or header */
    val section = 24.dp

    /** Between small elements, e.g. text and badge */
    val small = 6.dp
}

/**
 * Shapes used throughout the new UI
 */
object Shapes {
    val card = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(8.dp)
    val badge = RoundedCornerShape(4.dp)

    /**
     * Items of a group look like one card: only the outer corners are rounded
     */
    fun groupItem(position: GroupPosition) = RoundedCornerShape(
        topStart = if (position.isFirst) 16.dp else 0.dp,
        topEnd = if (position.isFirst) 16.dp else 0.dp,
        bottomStart = if (position.isLast) 16.dp else 0.dp,
        bottomEnd = if (position.isLast) 16.dp else 0.dp,
    )
}

/**
 * Where an item is in a group of items that look like one card
 */
@Immutable
data class GroupPosition(val isFirst: Boolean, val isLast: Boolean) {
    companion object {
        val Single = GroupPosition(isFirst = true, isLast = true)

        fun of(index: Int, count: Int) = GroupPosition(isFirst = index == 0, isLast = index == count - 1)
    }
}

/**
 * Colors with a meaning beyond the Material color scheme. The app provides its own values,
 * see [LocalSemanticColors].
 */
@Immutable
data class SemanticColors(
    val income: Color = Color(0xFF2E7D32),
    val expense: Color = Color(0xFFC62828),
)

val LocalSemanticColors = staticCompositionLocalOf { SemanticColors() }
