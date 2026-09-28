package org.totschnig.myexpenses.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * All building blocks on one page, to check how they look together
 */
@Preview(name = "Light", showBackground = true, heightDp = 1400)
@Preview(name = "Dark", showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Showcase() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        DetailScaffold(onBack = {}, title = "Design system") {
            Banner(
                title = "Banner",
                messages = listOf("Asks for a decision or tells about something new"),
                actions = {
                    TextButton(onClick = {}) { Text("No") }
                    Button(onClick = {}, modifier = Modifier.padding(start = 8.dp)) { Text("Yes") }
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelChip(text = "Insurance", placeholder = "Category", onClick = {})
                LabelChip(text = null, placeholder = "Category", onClick = {}, modifier = Modifier.padding(start = 8.dp))
                TextBadge("Badge", Modifier.padding(start = 8.dp))
                Dot(Modifier.padding(start = 8.dp))
            }
            SectionTitle("Rows")
            DetailCard {
                InfoRow("Info", "Value", isFirst = true)
                EditableRow("Editable", "Value", onClick = {})
                RemovableRow("Removable", "Value", removeLabel = "Remove", onRemove = {})
                SwitchRow("Switch", checked = true, onCheckedChange = {}, supportingText = "Explanation")
                LinkRow("Link", onClick = {})
            }
            SectionHeader("Group", count = 3, trailing = "123,45 €")
            (0 until 3).forEach {
                GroupItem(GroupPosition.of(it, 3)) {
                    Text("Item ${it + 1}", Modifier.padding(Spacing.content))
                }
            }
            EmptyState("Nothing here", hint = "Shown instead of an empty list", modifier = Modifier.size(320.dp, 160.dp))
        }
    }
}
