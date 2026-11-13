package org.totschnig.myexpenses.next.feature.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.designsystem.ChipRow
import org.totschnig.myexpenses.designsystem.LabelChip
import org.totschnig.myexpenses.designsystem.MultiChoiceSheet
import org.totschnig.myexpenses.designsystem.SingleChoiceDialog
import org.totschnig.myexpenses.designsystem.ToggleChip
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.core.ui.LabelIcons
import org.totschnig.myexpenses.next.data.tags.TagMeaning

@Composable
fun TagMeaning?.label() = stringResource(
    when (this) {
        null -> R.string.next_tag_meaning_none
        TagMeaning.ONE_OFF -> R.string.next_tag_meaning_one_off
        TagMeaning.REIMBURSED -> R.string.next_tag_meaning_reimbursed
        TagMeaning.TRIP -> R.string.next_tag_meaning_trip
        TagMeaning.BUSINESS -> R.string.next_tag_meaning_business
    }
)

/**
 * The tags of a transaction below its title in the details, next to the category. Tags are shown
 * to the user as groups (trips and themes) and markers ([TagMeaning.isMarker]). Tapping a group
 * sets whether it is a trip or removes it, "+ Gruppe" chooses the groups of the transaction, and
 * the markers are switched on and off.
 */
@Composable
fun TagChips(
    viewModel: TagsViewModel,
    transaction: ShownTransaction,
    modifier: Modifier = Modifier
) {
    TagChipRows(
        viewModel = viewModel,
        tags = transaction.tags,
        onSetTags = { viewModel.setTags(transaction.id, it) },
        onMarked = { meaning, marked, label ->
            viewModel.setMarked(transaction, meaning, marked, label)
        },
        modifier = modifier
    )
}

/**
 * The tags of a transaction that is typed in a form, like [TagChips], but nothing is saved until
 * the form is: a marker without its tag yet creates the tag right away.
 */
@Composable
fun TagField(
    viewModel: TagsViewModel,
    tagIds: List<Long>,
    onChange: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val all by viewModel.tags.collectAsStateWithLifecycle()
    val meanings by viewModel.meanings.collectAsStateWithLifecycle()
    val current by rememberUpdatedState(tagIds)
    val change by rememberUpdatedState(onChange)
    TagChipRows(
        viewModel = viewModel,
        tags = tagIds.mapNotNull { id -> all.find { it.id == id } },
        onSetTags = { change(it.toList()) },
        onMarked = { meaning, marked, label ->
            if (marked) {
                viewModel.markerTag(meaning, label) { id ->
                    if (id !in current) change(current + id)
                }
            } else {
                change(current.filter { meanings[it] != meaning })
            }
        },
        modifier = modifier
    )
}

/**
 * Groups and markers of [tags]
 *
 * @param onSetTags all tags after groups were chosen or removed
 * @param onMarked a marker was switched, with the label of a tag created for it
 */
@Composable
private fun TagChipRows(
    viewModel: TagsViewModel,
    tags: List<TagInfo>,
    onSetTags: (Collection<Long>) -> Unit,
    onMarked: (meaning: TagMeaning, marked: Boolean, label: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val meanings by viewModel.meanings.collectAsStateWithLifecycle()
    val tagIds = tags.map { it.id }
    val (markerIds, groupIds) = tagIds.partition { meanings[it]?.isMarker == true }
    val markerLabels = TagMeaning.MARKERS.associateWith { it.label() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChipRow(stringResource(R.string.next_tags_belongs_to)) {
            tags.filter { it.id in groupIds }.forEach { tag ->
                LabelChip(text = tag.label, placeholder = "", onClick = {
                    editingId = tag.id
                }, icon = LabelIcons.group)
            }
            LabelChip(text = null, placeholder = stringResource(R.string.next_tag_add), onClick = {
                picking =
                    true
            })
        }
        ChipRow(
            stringResource(R.string.next_markers),
            hint = stringResource(R.string.next_markers_hint)
        ) {
            markerLabels.forEach { (meaning, label) ->
                val marked = markerIds.any { meanings[it] == meaning }
                ToggleChip(
                    text = label,
                    selected = marked,
                    onClick = { onMarked(meaning, !marked, label) }
                )
            }
        }
    }
    if (picking) {
        GroupPickerSheet(
            viewModel = viewModel,
            initial = groupIds.toSet(),
            onConfirm = { selected ->
                if (selected != groupIds.toSet()) onSetTags(selected + markerIds)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
    editingId?.let { id ->
        tags.find { it.id == id }?.let { tag ->
            SingleChoiceDialog(
                title = tag.label,
                options = listOf(null, TagMeaning.TRIP),
                selected = meanings[tag.id],
                label = { it.label() },
                onSelect = {
                    viewModel.setMeaning(tag.id, it)
                    editingId = null
                },
                onDismiss = { editingId = null },
                intro = stringResource(R.string.next_tag_meaning_intro),
                footer = {
                    TextButton(onClick = {
                        onSetTags(tagIds - tag.id)
                        editingId = null
                    }) {
                        Text(stringResource(R.string.next_tag_remove))
                    }
                }
            )
        }
    }
}

/** Why a new group is not created right away */
private sealed interface NameWarning {
    val label: String

    /** Named like a category, can be created anyway */
    data class Category(override val label: String, val category: String) : NameWarning

    /** Named like the tag behind a marker, which is switched on instead */
    data class Marker(override val label: String, val meaning: TagMeaning) : NameWarning
}

/**
 * Chooses any number of groups, tags that are markers are not offered; new groups are created
 * right away and chosen. A new group named like a category is only created after a warning, as
 * the category is usually what is meant; one named like a marker is not created.
 */
@Composable
private fun GroupPickerSheet(
    viewModel: TagsViewModel,
    initial: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val meanings by viewModel.meanings.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(initial) }
    var newLabel by rememberSaveable { mutableStateOf("") }
    var warning by remember { mutableStateOf<NameWarning?>(null) }
    val scope = rememberCoroutineScope()
    val groups = tags.filter { meanings[it.id]?.isMarker != true }
    val labels = groups.associate { it.id to it.label }
    val markerNames = TagMeaning.MARKERS.associateBy { it.label() }
    fun create(label: String) {
        newLabel = ""
        warning = null
        viewModel.createTag(label) { selected = selected + it }
    }
    MultiChoiceSheet(
        title = stringResource(R.string.next_tags),
        options = groups.map { it.id },
        selected = selected,
        onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
        label = { labels[it] ?: "" },
        supportingText = { id -> meanings[id]?.label() },
        onConfirm = { onConfirm(selected) },
        onDismiss = onDismiss,
        header = {
            Text(
                stringResource(R.string.next_groups_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        },
        footer = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = {
                        newLabel = it
                        if (it.trim() != warning?.label) warning = null
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.next_tag_new)) },
                    modifier = Modifier.weight(1f)
                )
                val current = warning
                TextButton(
                    onClick = {
                        val label = newLabel.trim()
                        if (current is NameWarning.Category && current.label == label) {
                            create(label)
                            return@TextButton
                        }
                        val marker = tags.find { it.label.equals(label, ignoreCase = true) }
                            ?.let { meanings[it.id] }?.takeIf { it.isMarker }
                            ?: markerNames.entries.find {
                                it.key.equals(label, ignoreCase = true)
                            }?.value
                        if (marker != null) {
                            warning = NameWarning.Marker(label, marker)
                            return@TextButton
                        }
                        scope.launch {
                            val category = viewModel.categoryNamed(label)
                            if (category != null) {
                                warning = NameWarning.Category(label, category)
                            } else {
                                create(label)
                            }
                        }
                    },
                    enabled = newLabel.isNotBlank() && current !is NameWarning.Marker
                ) {
                    Text(
                        stringResource(
                            if (
                                current is NameWarning.Category
                            ) {
                                R.string.next_group_create_anyway
                            } else {
                                R.string.next_tag_create
                            }
                        )
                    )
                }
            }
            warning?.let {
                Text(
                    when (it) {
                        is NameWarning.Category -> stringResource(
                            R.string.next_group_name_is_category,
                            it.category
                        )

                        is NameWarning.Marker -> stringResource(
                            R.string.next_group_name_is_marker,
                            it.meaning.label()
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    )
}
