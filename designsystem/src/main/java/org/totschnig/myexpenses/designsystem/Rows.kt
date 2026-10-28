package org.totschnig.myexpenses.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Rows of a card. Each row draws a divider above itself, except the first one of a card
 * (isFirst = true).
 */

/**
 * Label on the left, value on the right
 *
 * @param valueColor e.g. for amounts, see [org.totschnig.myexpenses.designsystem.theme.SemanticColors.amountColor]
 */
@Composable
fun InfoRow(label: String, value: String, isFirst: Boolean = false, valueColor: Color = Color.Unspecified) {
    if (!isFirst) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.content, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.content)
        )
    }
}

/**
 * Like [InfoRow], with a pencil showing that tapping changes the value
 */
@Composable
fun EditableRow(label: String, value: String, onClick: () -> Unit, isFirst: Boolean = false) {
    EditableRow(label, onClick, isFirst) {
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

/**
 * [EditableRow] whose value is no text, e.g. the icon of a pot on its tile
 */
@Composable
fun EditableRow(label: String, onClick: () -> Unit, isFirst: Boolean = false, value: @Composable () -> Unit) {
    if (!isFirst) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = Spacing.content, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        value()
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(16.dp)
        )
    }
}

/**
 * Something that is not possible yet, and what it still needs, e.g. a saving tip that needs scanned
 * receipts. Muted, since it tells nothing yet.
 */
@Composable
fun PendingRow(title: String, text: String, isFirst: Boolean = false, icon: ImageVector = Icons.Outlined.HourglassEmpty) {
    if (!isFirst) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.content, vertical = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column(Modifier.padding(start = Spacing.content)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * One part of a whole with a thin bar for its share, e.g. how much of a purchase went to one
 * product type. Rows below each other are sorted by size, so the bars compare at a glance; the
 * value carries the number, so the bar is never the only way to read it.
 *
 * @param share from 0 to 1
 * @param isMuted for a rest that is not a real part, e.g. items without product type: grey bar
 * @param onClick e.g. to assign the rest, null if the row is not tappable
 */
@Composable
fun ShareRow(
    label: String,
    value: String,
    share: Float,
    isFirst: Boolean = false,
    isMuted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (!isFirst) RowDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.content, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.content)
            )
        }
        // Recessive track, so that the length of the bar reads as part of the whole
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(ShareBarHeight)
                .clip(ShareBarShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .height(ShareBarHeight)
                    .clip(ShareBarShape)
                    .background(if (isMuted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private val ShareBarHeight = 8.dp
private val ShareBarShape = Shapes.badge

/**
 * How much of a limit is used, e.g. of a pot of the budget: a meter whose track is a light step of
 * the fill, so that the whole bar reads as one state. Overspent, fill and track turn to the error
 * colours and a warning icon is shown; the [value] says it in words, so colour is never the only
 * sign.
 *
 * @param used share of the limit used, 1f = all of it, above 1f overspent
 * @param committed share that is not used yet but will be, e.g. contracts still to be paid, drawn
 * lighter after [used]
 * @param projected share expected at the end, e.g. at the pace so far, a mark on the bar, null for none
 * @param supportingText below the label, e.g. "of 300 €"
 * @param leadingIcon in place of [icon], for icons that are no vector, e.g. of a font, 24dp
 * @param onClick opens the details, null if the row is not tappable
 */
@Composable
fun MeterRow(
    label: String,
    value: String,
    used: Float,
    isFirst: Boolean = false,
    committed: Float = 0f,
    projected: Float? = null,
    supportingText: String? = null,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    if (!isFirst) RowDivider()
    val isOver = used + committed > 1f
    val fill = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val track = if (isOver) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.content, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = Spacing.content)
                    .size(24.dp)
            )
        } else if (leadingIcon != null) {
            Box(
                Modifier
                    .padding(end = Spacing.content)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) { leadingIcon() }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isOver) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(16.dp)
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Box(
                Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .height(MeterMarkHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(MeterHeight)
                        .clip(MeterShape)
                        .background(track)
                ) {
                    val usedWidth = used.coerceIn(0f, 1f)
                    val committedWidth = committed.coerceIn(0f, 1f - usedWidth)
                    if (usedWidth > 0f) {
                        Box(
                            Modifier
                                .weight(usedWidth)
                                .height(MeterHeight)
                                .clip(MeterShape)
                                .background(fill)
                        )
                    }
                    if (committedWidth > 0f) {
                        // A gap of the surface between the two fills
                        Box(
                            Modifier
                                .padding(start = if (usedWidth > 0f) 2.dp else 0.dp)
                                .weight(committedWidth)
                                .height(MeterHeight)
                                .clip(MeterShape)
                                .background(fill.copy(alpha = 0.45f))
                        )
                    }
                    val rest = 1f - usedWidth - committedWidth
                    if (rest > 0f) Box(Modifier.weight(rest))
                }
                if (projected != null) {
                    val position = projected.coerceIn(0f, 1f)
                    Row(Modifier.fillMaxWidth()) {
                        if (position > 0f) Box(Modifier.weight(position))
                        Box(
                            Modifier
                                .size(width = 2.dp, height = MeterMarkHeight)
                                .background(MaterialTheme.colorScheme.onSurface)
                        )
                        if (position < 1f) Box(Modifier.weight(1f - position))
                    }
                }
            }
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private val MeterHeight = 8.dp
private val MeterMarkHeight = 16.dp
private val MeterShape = Shapes.badge

/**
 * Leads to another page, like "show all"
 */
@Composable
fun LinkRow(text: String, onClick: () -> Unit, isFirst: Boolean = false) {
    if (!isFirst) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.content, end = 8.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
}

/**
 * An item that opens its details: title with a line below, optionally a value on the right
 *
 * @param valueColor e.g. for amounts, see [org.totschnig.myexpenses.designsystem.theme.SemanticColors.amountColor]
 * @param hasNews marks with a [Dot] that there is something new behind it
 */
@Composable
fun NavigationRow(
    title: String,
    supportingText: String,
    onClick: () -> Unit,
    isFirst: Boolean = false,
    value: String? = null,
    valueColor: Color = Color.Unspecified,
    hasNews: Boolean = false,
) {
    if (!isFirst) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.content, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        value?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                modifier = Modifier.padding(start = Spacing.content)
            )
        }
        if (hasNews) Dot(Modifier.padding(start = Spacing.content))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/**
 * A setting that is on or off, with an explanation below the label
 *
 * @param enabled false while another setting turns this one off, e.g. the parts of autofill
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
    isFirst: Boolean = false,
    enabled: Boolean = true,
) {
    if (!isFirst) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.content, vertical = 12.dp)
            .alpha(if (enabled) 1f else DisabledAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.padding(start = Spacing.content)
        )
    }
}

/**
 * A setting whose value is picked or typed in a dialog: the label with the current value below.
 * Unlike [EditableRow] the value may be long, e.g. a list of date formats.
 *
 * @param value null for an action without value, e.g. deleting a calendar
 * @param enabled false while another setting turns this one off
 */
@Composable
fun ValueRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    isFirst: Boolean = false,
    enabled: Boolean = true,
) {
    if (!isFirst) RowDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.content, vertical = 12.dp)
            .alpha(if (enabled) 1f else DisabledAlpha),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        value?.takeIf { it.isNotEmpty() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A setting with a value in a range, e.g. the quality of pictures. The value is handed over when
 * the user lets go, not on every step of dragging.
 *
 * @param valueText the value as shown next to the label, e.g. "80 %"
 * @param steps between the ends of [valueRange], 0 for a continuous value
 * @param preview below the slider, of the value while dragging, e.g. a text in a font size
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    isFirst: Boolean = false,
    steps: Int = 0,
    enabled: Boolean = true,
    preview: (@Composable (Float) -> Unit)? = null,
) {
    if (!isFirst) RowDivider()
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.content, end = Spacing.content, top = 12.dp)
            .alpha(if (enabled) 1f else DisabledAlpha)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueText(current),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.content)
            )
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onValueChange(current) },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label }
        )
        preview?.let { Box(Modifier.padding(bottom = 12.dp)) { it(current) } }
    }
}

/**
 * Explains something within a card, e.g. that backups to the cloud are not encrypted
 */
@Composable
fun HintRow(text: String, isFirst: Boolean = false, icon: ImageVector = Icons.Outlined.Info) {
    if (!isFirst) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.content, vertical = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.content)
        )
    }
}

/**
 * Entry of a page that lists other pages, e.g. the groups of the settings: icon, title and what
 * can be found there. Unlike the rows above it is not part of a card and has no divider.
 *
 * @param supportingText e.g. a few of the settings in the group, null for a single line
 */
@Composable
fun IconNavigationRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    IconNavigationRow(title, onClick, modifier, supportingText) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * [IconNavigationRow] with an icon that is no vector, e.g. of a font
 *
 * @param icon 24dp
 */
@Composable
fun IconNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.content, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 32.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Of rows that another setting turns off */
private const val DisabledAlpha = 0.38f

/**
 * Like [InfoRow], with a button to remove the entry
 *
 * @param removeLabel describes the button, null if the entry cannot be removed
 */
@Composable
fun RemovableRow(
    label: String,
    value: String,
    removeLabel: String?,
    onRemove: () -> Unit,
    isFirst: Boolean = false,
) {
    if (!isFirst) RowDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.content, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.content)
        )
        if (removeLabel != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    contentDescription = removeLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** One number of [KeyFigures], e.g. "Spent" and "29.100 €" */
data class KeyFigure(val label: String, val value: String)

/**
 * A few headline numbers side by side, e.g. earned, spent and left, each value above its label.
 * Each value is read out together with its label.
 */
@Composable
fun KeyFigures(figures: List<KeyFigure>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.content, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.content)
    ) {
        figures.forEach { figure ->
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {}
            ) {
                Text(
                    figure.value,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    figure.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
