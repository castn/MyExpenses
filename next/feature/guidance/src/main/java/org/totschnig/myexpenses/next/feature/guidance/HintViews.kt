package org.totschnig.myexpenses.next.feature.guidance

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.HintCard
import org.totschnig.myexpenses.designsystem.InviteCard
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.guidance.DataGap
import org.totschnig.myexpenses.next.data.guidance.HintTopic
import org.totschnig.myexpenses.next.data.guidance.isSupermarket
import org.totschnig.myexpenses.next.data.guidance.overviewHint
import org.totschnig.myexpenses.next.data.guidance.topic

/** What the snackbar says after saving a transaction with a hint about [topic] */
@StringRes
fun HintTopic.afterSaveText(): Int = when (this) {
    HintTopic.CATEGORY -> R.string.next_hint_saved_category
    HintTopic.RECEIPT -> R.string.next_hint_saved_receipt
}

/**
 * Card of the overview about the most useful gap of the analysis, which the user can close
 *
 * @param onOpen opens the analysis, which lists what to fix; null without it, then the card only tells
 */
@Composable
fun OverviewHint(viewModel: HintsViewModel, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val gap by viewModel.overviewHint.collectAsStateWithLifecycle()
    val current = gap ?: return
    val topic = current.topic ?: return
    val (title, text) = when (current) {
        is DataGap.Uncategorized -> stringResource(R.string.next_hint_overview_uncategorized_title) to
                pluralStringResource(R.plurals.next_hint_overview_uncategorized_text, current.count, current.count)

        is DataGap.VagueCategories -> stringResource(R.string.next_hint_overview_vague_title) to
                pluralStringResource(R.plurals.next_hint_overview_vague_text, current.count, current.count)

        is DataGap.FewReceipts -> stringResource(R.string.next_hint_overview_receipts_title) to
                stringResource(R.string.next_hint_overview_receipts_text)

        DataGap.NoTags -> return
    }
    InviteCard(
        icon = if (topic == HintTopic.RECEIPT) Icons.Outlined.DocumentScanner else Icons.Outlined.Category,
        title = title,
        text = text,
        onClick = onOpen,
        onDismiss = { viewModel.dismiss(topic) },
        dismissLabel = stringResource(R.string.next_hint_never),
        modifier = modifier
    )
}

/**
 * Hint in the details of a supermarket purchase without receipt, next to the chip that scans one.
 * Nothing once the user closed it.
 */
@Composable
fun ReceiptHint(viewModel: HintsViewModel, transaction: ShownTransaction, modifier: Modifier = Modifier) {
    if (!transaction.canHaveReceipt || !isSupermarket(transaction.payeeName)) return
    val dismissed by viewModel.dismissed.collectAsStateWithLifecycle()
    val scanned by viewModel.scanned.collectAsStateWithLifecycle()
    // Nothing while either is loading, so that the hint does not flash up, and nothing when receipts cannot be scanned
    if (dismissed?.contains(HintTopic.RECEIPT) != false || scanned?.contains(transaction.id) != false) return
    HintCard(
        text = stringResource(R.string.next_hint_details_receipt),
        dismissLabel = stringResource(R.string.next_hint_never),
        onDismiss = { viewModel.dismiss(HintTopic.RECEIPT) },
        modifier = modifier
    )
}
