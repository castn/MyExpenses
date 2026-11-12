package org.totschnig.myexpenses.designsystem

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A card that asks for one decision, e.g. the category of a payee in the setup: [leading] tile,
 * [title] and [value] in the head, then [content] that helps to decide, e.g. the purpose and a
 * suggestion, and the [actions] at the bottom, the main one last, wrapped into lines where there
 * is little room
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
    actions: @Composable FlowRowScope.() -> Unit
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

/**
 * A text from elsewhere, shown as it is, e.g. the purpose of a transaction from the bank: muted on
 * a tile, at first [collapsedLines] lines, all of it after tapping [expandLabel]
 */
@Composable
fun QuotedText(
    text: String,
    expandLabel: String,
    collapseLabel: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 2
) {
    var isExpanded by rememberSaveable(text) { mutableStateOf(false) }
    var canExpand by rememberSaveable(text) { mutableStateOf(false) }
    Surface(
        shape = Shapes.tile,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .animateContentSize()
                .padding(
                    start = 12.dp,
                    top = 8.dp,
                    end = 4.dp,
                    bottom = if (canExpand) 0.dp else 8.dp
                )
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else collapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!isExpanded) canExpand = it.hasVisualOverflow },
                modifier = Modifier.padding(end = 8.dp)
            )
            if (canExpand) {
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (isExpanded) collapseLabel else expandLabel)
                }
            }
        }
    }
}
