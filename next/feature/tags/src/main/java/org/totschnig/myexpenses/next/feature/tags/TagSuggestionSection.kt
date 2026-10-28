package org.totschnig.myexpenses.next.feature.tags

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.data.tags.TagSuggestion
import org.totschnig.myexpenses.next.data.tags.addTag

/**
 * Part of the details of a transaction: a tag suggested by the other transactions of its
 * counterpart, and after taking it, the offer to give it to the others and to new ones. A tag that
 * is a marker ([TagMeaning.isMarker]) is shown by its meaning, e.g. "Beruflich?".
 */
@Composable
fun TagSuggestionSection(viewModel: TagLearningViewModel, tagsViewModel: TagsViewModel, transaction: ShownTransaction) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val justAccepted by viewModel.justAccepted.collectAsStateWithLifecycle()
    val tags by tagsViewModel.tags.collectAsStateWithLifecycle()
    val meanings by tagsViewModel.meanings.collectAsStateWithLifecycle()
    DisposableEffect(transaction.id) {
        onDispose { viewModel.clearAccepted(transaction.id) }
    }
    justAccepted[transaction.id]?.let { accepted ->
        val marker = meanings[accepted.tagId]?.takeIf { it.isMarker }
        FollowUp(
            tagLabel = marker?.label() ?: tags.find { it.id == accepted.tagId }?.label ?: "",
            isMarker = marker != null,
            suggestion = accepted,
            onApplyToOthers = { viewModel.addTag(it, accepted.tagId) },
            onAddRule = { viewModel.addRule(accepted.counterpart, accepted.tagId) }
        )
        return
    }
    val suggestion = suggestions[transaction.id]?.takeIf { it.tagId !in transaction.tags.map { tag -> tag.id } }
        ?: return
    val label = tags.find { it.id == suggestion.tagId }?.label ?: return
    val marker = meanings[suggestion.tagId]?.takeIf { it.isMarker }
    Banner(
        title = if (marker != null) stringResource(R.string.next_marker_suggested, marker.label())
        else stringResource(R.string.next_tag_suggested, label),
        messages = listOf(
            if (marker != null) pluralStringResource(
                R.plurals.next_marker_suggested_reason, suggestion.tagged, suggestion.counterpartName, suggestion.tagged, marker.label()
            ) else pluralStringResource(
                R.plurals.next_tag_suggested_reason, suggestion.tagged, suggestion.counterpartName, suggestion.tagged
            )
        )
    ) {
        TextButton(onClick = { viewModel.rejectSuggestion(suggestion) }) { Text(stringResource(R.string.next_reject)) }
        TextButton(onClick = { viewModel.acceptSuggestion(suggestion) }) { Text(stringResource(R.string.next_tag_accept)) }
    }
}

/**
 * After a suggested tag was taken: offers it for the other transactions of the counterpart without
 * it, and for the future. Stays until the details are closed.
 */
@Composable
private fun FollowUp(
    tagLabel: String,
    isMarker: Boolean,
    suggestion: TagSuggestion,
    onApplyToOthers: (List<Long>) -> Unit,
    onAddRule: () -> Unit,
) {
    var appliedCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var ruleAdded by rememberSaveable { mutableStateOf(false) }
    val others = suggestion.others - suggestion.transactionId
    val name = suggestion.counterpartName
    SectionTitle(stringResource(if (isMarker) R.string.next_markers else R.string.next_tags))
    DetailCard {
        InfoRow(stringResource(if (isMarker) R.string.next_marker_taken else R.string.next_tag_taken), tagLabel, isFirst = true)
        val applied = appliedCount
        if (applied != null) {
            InfoRow(stringResource(R.string.next_category_applied_others, name), applied.toString())
        } else if (others.isNotEmpty()) {
            LinkRow(
                pluralStringResource(R.plurals.next_tag_apply_others, others.size, others.size, name),
                onClick = {
                    onApplyToOthers(others)
                    appliedCount = others.size
                }
            )
        }
        if (ruleAdded) {
            InfoRow(stringResource(R.string.next_category_rule_added, name), tagLabel)
        } else {
            LinkRow(
                stringResource(R.string.next_category_always, name),
                onClick = {
                    onAddRule()
                    ruleAdded = true
                }
            )
        }
    }
}
