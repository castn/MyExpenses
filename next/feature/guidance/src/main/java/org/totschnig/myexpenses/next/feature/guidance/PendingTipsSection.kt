package org.totschnig.myexpenses.next.feature.guidance

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.PendingRow
import org.totschnig.myexpenses.designsystem.SectionHeader
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.valueOrNull
import org.totschnig.myexpenses.next.data.tips.SavingTipsOverview
import org.totschnig.myexpenses.next.data.guidance.PendingTip
import org.totschnig.myexpenses.next.data.guidance.pendingTips

/**
 * The saving tips that lack data, below the tips. Nothing while loading or if none lacks data.
 */
@Composable
fun PendingTipsSection(readiness: DataReadinessViewModel, tips: AnalysisState<SavingTipsOverview>) {
    val value = readiness.readiness.collectAsStateWithLifecycle().value ?: return
    val overview = tips.valueOrNull ?: return
    val ids = (overview.tips + overview.dismissed).mapTo(HashSet()) { it.id }
    PendingTips(pendingTips(value, ids))
}

@Composable
private fun PendingTips(pending: List<PendingTip>) {
    if (pending.isEmpty()) return
    Column {
        SectionHeader(title = stringResource(R.string.next_pending_tips_title))
        DetailCard {
            pending.forEachIndexed { index, tip ->
                val (title, text) = when (tip) {
                    PendingTip.ITEM_GROUPS_RISING ->
                        stringResource(R.string.next_pending_item_groups) to stringResource(R.string.next_pending_item_groups_text)

                    PendingTip.DRUGSTORE_IN_SUPERMARKETS ->
                        stringResource(R.string.next_pending_drugstore) to stringResource(R.string.next_pending_drugstore_text)

                    PendingTip.DEPOSIT_NOT_RETURNED ->
                        stringResource(R.string.next_pending_deposit) to stringResource(R.string.next_pending_deposit_text)

                    PendingTip.OUTLIERS -> stringResource(R.string.next_pending_outliers) to
                            stringResource(R.string.next_pending_outliers_text, stringResource(R.string.next_tab_analysis))
                }
                PendingRow(title, text, isFirst = index == 0)
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PendingTipsPreview() {
    NextPreviewTheme {
        Column(Modifier.padding(Spacing.screen)) {
            PendingTips(PendingTip.entries)
        }
    }
}
