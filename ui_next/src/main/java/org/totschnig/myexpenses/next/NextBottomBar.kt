package org.totschnig.myexpenses.next

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Tabs of the bottom bar of the new UI. [Overview] is rendered as the highlighted center button.
 * Icons are placeholders.
 */
enum class NextTab(val icon: ImageVector, @param:StringRes val labelRes: Int) {
    Contracts(Icons.Default.Description, R.string.next_tab_contracts),
    Analysis(Icons.Default.BarChart, R.string.next_tab_analysis),
    Overview(Icons.Default.Home, R.string.next_tab_overview),
    Insurance(Icons.Default.Shield, R.string.next_tab_insurance),
    Menu(Icons.Default.Menu, R.string.next_tab_menu)
}

@Composable
fun NextBottomBar(
    selectedTab: NextTab,
    onTabClick: (NextTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NextTab.entries.forEach { tab ->
                val itemModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                if (tab == NextTab.Overview) {
                    CenterItem(tab, selected = selectedTab == tab, onClick = { onTabClick(tab) }, modifier = itemModifier)
                } else {
                    BarItem(tab, selected = selectedTab == tab, onClick = { onTabClick(tab) }, modifier = itemModifier)
                }
            }
        }
    }
}

@Composable
private fun BarItem(
    tab: NextTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = null, tint = color)
        Text(
            text = stringResource(tab.labelRes),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun CenterItem(
    tab: NextTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            selected = selected,
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    tab.icon,
                    contentDescription = stringResource(tab.labelRes),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Placeholder content for tabs that have not been implemented yet.
 */
@Composable
fun NextPlaceholderScreen(tab: NextTab, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(
            stringResource(tab.labelRes),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(R.string.next_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NextBottomBarPreview() {
    // AppTheme/NextTheme need MyApplication (injector), which is not available in previews
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        // Stateful, so that tapping a tab in interactive mode selects it
        var selectedTab by remember { mutableStateOf(NextTab.Overview) }
        NextBottomBar(selectedTab = selectedTab, onTabClick = { selectedTab = it })
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun NextPlaceholderScreenPreview() {
    MaterialTheme {
        Surface {
            NextPlaceholderScreen(NextTab.Contracts)
        }
    }
}
