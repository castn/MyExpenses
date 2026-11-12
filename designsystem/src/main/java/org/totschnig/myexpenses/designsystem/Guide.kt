package org.totschnig.myexpenses.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One step in the overview of a guide, e.g. of the setup after the first start: its number, or a
 * check once done, its title and what it takes, e.g. "23 decisions · about 3 min."
 *
 * @param statusLabel says the status in words, e.g. "Skipped", shown as a badge and read out, so
 * that the colour of the number is never the only sign; null for none
 * @param onClick opens the step, null if it cannot be opened
 */
@Composable
fun GuideStepRow(
    number: Int,
    title: String,
    text: String,
    status: GuideStepStatus,
    isFirst: Boolean = false,
    statusLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    if (!isFirst) RowDivider()
    val isMuted = status == GuideStepStatus.DONE || status == GuideStepStatus.SKIPPED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.content, vertical = 12.dp)
            .semantics(mergeDescendants = true) { statusLabel?.let { stateDescription = it } },
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepNumber(number, status)
        Column(
            Modifier
                .weight(1f)
                .padding(start = Spacing.content)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isMuted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (statusLabel != null) {
            TextBadge(
                statusLabel,
                Modifier.padding(start = Spacing.small),
                style = BadgeStyle.Outlined
            )
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

/** The number of a step on a circle, filled for the current one, a check once done */
@Composable
private fun StepNumber(number: Int, status: GuideStepStatus) {
    val (container, content) = when (status) {
        GuideStepStatus.CURRENT, GuideStepStatus.DONE ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary

        GuideStepStatus.OPEN ->
            MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer

        GuideStepStatus.SKIPPED ->
            MaterialTheme.colorScheme.surfaceContainerHighest to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        if (status == GuideStepStatus.DONE) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(number.toString(), style = MaterialTheme.typography.titleSmall, color = content)
        }
    }
}

/**
 * The head of a step of a guide: how far it is, e.g. "62 %" of the money with a category, on a bar
 * with a mark at the [target], and what is left, e.g. "about 3 min. left"
 *
 * @param progress from 0 to 1
 * @param value [progress] in words, e.g. "62 %", read out with the bar
 * @param target share to reach, a mark on the bar, null for none
 */
@Composable
fun GuideProgressHeader(
    title: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
    target: Float? = null,
    supportingText: String? = null
) {
    val shown = progress.coerceIn(0f, 1f)
    val isReached = target != null && shown >= target
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            if (isReached) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(16.dp)
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(ProgressMarkHeight)
                .semantics(mergeDescendants = true) {
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                    stateDescription = value
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ProgressHeight)
                    .clip(Shapes.badge)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (shown > 0f) {
                    Box(
                        Modifier
                            .weight(shown)
                            .height(ProgressHeight)
                            .clip(Shapes.badge)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                if (shown < 1f) Box(Modifier.weight(1f - shown))
            }
            if (target != null) {
                val position = target.coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth()) {
                    if (position > 0f) Box(Modifier.weight(position))
                    Box(
                        Modifier
                            .size(width = 2.dp, height = ProgressMarkHeight)
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
}

private val ProgressHeight = 8.dp
private val ProgressMarkHeight = 16.dp
