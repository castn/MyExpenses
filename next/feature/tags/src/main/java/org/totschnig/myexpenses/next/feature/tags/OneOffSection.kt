package org.totschnig.myexpenses.next.feature.tags

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.tags.TagMeaning

/**
 * Asks in the details of an unusually high expense whether it was one-off, so that it no longer
 * counts in averages and tips. Marked expenses can be unmarked.
 */
@Composable
fun OneOffSection(viewModel: TagsViewModel, transaction: ShownTransaction) {
    val meanings by viewModel.meanings.collectAsStateWithLifecycle()
    val isOneOff = transaction.tags.any { meanings[it.id] == TagMeaning.ONE_OFF }
    val label = stringResource(R.string.next_tag_meaning_one_off)
    Banner(
        title = stringResource(if (isOneOff) R.string.next_tag_one_off_done else R.string.next_tag_one_off_question),
        messages = listOf(stringResource(R.string.next_tag_one_off_text))
    ) {
        TextButton(onClick = { viewModel.setMarked(transaction, TagMeaning.ONE_OFF, !isOneOff, label) }) {
            Text(stringResource(if (isOneOff) R.string.next_tag_one_off_undo else R.string.next_tag_one_off_action))
        }
    }
}
