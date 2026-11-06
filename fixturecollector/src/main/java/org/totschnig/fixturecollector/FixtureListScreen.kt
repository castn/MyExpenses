package org.totschnig.fixturecollector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.EmptyState
import org.totschnig.myexpenses.designsystem.NavigationRow
import org.totschnig.myexpenses.designsystem.PageTopBar
import org.totschnig.myexpenses.designsystem.SectionHeader
import org.totschnig.myexpenses.designsystem.Spacing

@Composable
fun FixtureListScreen(
    fixtures: List<FixtureStore.Summary>,
    onOpen: (String) -> Unit,
    onScan: () -> Unit,
    onGallery: () -> Unit,
    onShare: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        PageTopBar(onBack = null, title = stringResource(R.string.fixtures)) {
            if (fixtures.isNotEmpty()) {
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.share_all)
                    )
                }
            }
        }
        if (fixtures.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_title),
                hint = stringResource(R.string.empty_hint),
                modifier = Modifier.weight(1f)
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
            ) {
                SectionHeader(stringResource(R.string.fixtures), count = fixtures.size)
                DetailCard {
                    fixtures.forEachIndexed { index, fixture ->
                        NavigationRow(
                            title = fixture.name,
                            supportingText = stringResource(
                                R.string.fixture_summary,
                                fixture.items,
                                fixture.total?.let(::formatCents) ?: stringResource(R.string.none)
                            ),
                            onClick = { onOpen(fixture.name) },
                            isFirst = index == 0,
                            value = fixture.merchant
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.content)
        ) {
            Button(onClick = onScan, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = null,
                    Modifier.padding(end = ButtonDefaults.IconSpacing)
                )
                Text(stringResource(R.string.scan))
            }
            OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    Modifier.padding(end = ButtonDefaults.IconSpacing)
                )
                Text(stringResource(R.string.gallery))
            }
        }
    }
}
