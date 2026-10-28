package org.totschnig.myexpenses.next.feature.analysis

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.totschnig.myexpenses.designsystem.InfoBox
import org.totschnig.myexpenses.designsystem.InfoPoint
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.data.guidance.DataGap

/**
 * What the analysis still lacks, as text with a way to fix each gap. Nothing if nothing is missing.
 *
 * @param onShow shows the transactions of a gap, see [DataGap.transactionIds]
 */
@Composable
fun ReadinessBox(gaps: List<DataGap>, onShow: (DataGap) -> Unit, modifier: Modifier = Modifier) {
    if (gaps.isEmpty()) return
    InfoBox(
        title = stringResource(R.string.next_readiness_title),
        points = gaps.map { gap -> point(gap) { onShow(gap) } },
        modifier = modifier
    )
}

@Composable
private fun point(gap: DataGap, onShow: () -> Unit): InfoPoint {
    val action = when (gap) {
        is DataGap.Uncategorized -> R.string.next_readiness_uncategorized_action
        is DataGap.VagueCategories -> R.string.next_readiness_vague_action
        is DataGap.FewReceipts -> R.string.next_readiness_receipts_action
        DataGap.NoTags -> null
    }?.takeIf { gap.transactionIds.isNotEmpty() }?.let { stringResource(it) }
    val text = when (gap) {
        is DataGap.Uncategorized -> pluralStringResource(R.plurals.next_readiness_uncategorized, gap.count, gap.count, gap.total)
        is DataGap.VagueCategories -> pluralStringResource(R.plurals.next_readiness_vague, gap.count, gap.count, gap.total)
        is DataGap.FewReceipts -> {
            val scanned = if (gap.scanned == 0) stringResource(R.string.next_readiness_receipts_none)
            else pluralStringResource(R.plurals.next_readiness_receipts_few, gap.scanned, gap.scanned)
            scanned + " " + stringResource(R.string.next_readiness_receipts_why)
        }
        DataGap.NoTags -> stringResource(R.string.next_readiness_no_tags)
    }
    return InfoPoint(text, action, onShow)
}

/** Title of the list of the transactions of [gap] */
@Composable
fun gapTitle(gap: DataGap): String = when (gap) {
    is DataGap.Uncategorized -> stringResource(R.string.next_analysis_no_category)
    is DataGap.VagueCategories -> stringResource(R.string.next_readiness_vague_action)
    is DataGap.FewReceipts -> stringResource(R.string.next_readiness_receipts_action)
    DataGap.NoTags -> stringResource(R.string.next_readiness_title)
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadinessBoxPreview() {
    NextPreviewTheme {
        ReadinessBox(
            gaps = listOf(
                DataGap.Uncategorized(List(41) { it.toLong() }, 180),
                DataGap.FewReceipts(scanned = 2, supermarketPurchases = 14, unscanned = listOf(1, 2), missingRecent = 2, missingUsual = 6),
                DataGap.NoTags
            ),
            onShow = {},
            modifier = Modifier.padding(Spacing.screen)
        )
    }
}
