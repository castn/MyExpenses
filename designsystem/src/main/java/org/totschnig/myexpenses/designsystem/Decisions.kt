package org.totschnig.myexpenses.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A card that asks for one decision, e.g. the category of a payee in the setup: [leading] tile,
 * [title] and [value] in the head, then [content] that helps to decide, e.g. the last
 * transactions and a suggestion, and the [actions] at the bottom, the main one last, wrapped into
 * lines where there is little room; without them, e.g. when a bar below the page decides
 *
 * @param supportingText below the title, e.g. "12 transactions since March"
 */
@Composable
fun DecisionCard(
    title: String,
    value: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: (@Composable FlowRowScope.() -> Unit)? = null
) {
    Surface(
        shape = Shapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Spacing.content)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading()
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.content)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (supportingText != null) {
                        Text(
                            supportingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(
                Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
                content = content
            )
            if (actions != null) {
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.End),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

/** One entry of an [EntryPreview], e.g. a transaction with its purpose as [text] */
data class PreviewEntry(val date: String, val text: String?, val amount: String)

/**
 * The last [entries] of what a decision is about, e.g. the transactions of a payee, muted on a
 * tile: date, text in at most two lines, e.g. the purpose from the bank, and amount
 *
 * @param moreLabel shows all of them with [onMore], e.g. "Show all 7 transactions"
 */
@Composable
fun EntryPreview(
    entries: List<PreviewEntry>,
    modifier: Modifier = Modifier,
    moreLabel: String? = null,
    onMore: () -> Unit = {}
) {
    Surface(
        shape = Shapes.tile,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(
                start = 12.dp,
                top = 8.dp,
                end = 12.dp,
                bottom = if (moreLabel != null) 0.dp else 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            entries.forEach { entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        entry.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        entry.text.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(entry.amount, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (moreLabel != null) {
                TextButton(onClick = onMore, modifier = Modifier.align(Alignment.End)) {
                    Text(moreLabel)
                }
            }
        }
    }
}

/**
 * A few answers that change what follows, e.g. whether the debits of a payee are for one category:
 * segmented buttons in one row, always in the same place, and below them the part of the answer
 * [selected], which changes with a short animation
 *
 * @param title above the buttons, e.g. "What applies?"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable (T) -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    // Without the check mark, so that longer answers keep their room
                    icon = {},
                    label = {
                        Text(
                            label(option),
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            label = "segmented choice"
        ) { content(it) }
    }
}

/**
 * What was decided last, with a way to change it, e.g. "Last: REWE → Groceries" at the top of the
 * next decision, so that the user sees what a tap did
 */
@Composable
fun LastDecisionRow(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.small)
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
