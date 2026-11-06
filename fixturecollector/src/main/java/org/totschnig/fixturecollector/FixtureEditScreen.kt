package org.totschnig.fixturecollector

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.EditableItemRow
import org.totschnig.myexpenses.designsystem.EditableRow
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.ProgressRow
import org.totschnig.myexpenses.designsystem.RowDivider
import org.totschnig.myexpenses.designsystem.SectionHeader
import org.totschnig.myexpenses.designsystem.SelectableTextRow
import org.totschnig.myexpenses.designsystem.SelectionBar
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.SwitchRow
import org.totschnig.myexpenses.feature.ExpectedItem
import org.totschnig.myexpenses.feature.ExpectedReceipt
import org.totschnig.myexpenses.feature.ReceiptAnonymizer

/** Field of the expected values edited in a dialog */
private enum class Field { NAME, MERCHANT, TOTAL, ITEM_COUNT, TRANSACTION_AMOUNT }

/**
 * Item edited in a dialog: [index] null for a new one, which goes to [insertAt] or at the end
 */
private data class ItemEdit(val index: Int?, val item: ExpectedItem, val insertAt: Int? = null)

@Composable
fun FixtureEditScreen(draft: Draft, viewModel: CollectorViewModel) {
    var showBoxes by rememberSaveable { mutableStateOf(true) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var field by remember { mutableStateOf<Field?>(null) }
    var itemEdit by remember { mutableStateOf<ItemEdit?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    /** Indices of recognized rows chosen to make up one item, empty when not selecting */
    var selectedRows by remember(draft.text) { mutableStateOf(emptySet<Int>()) }
    val rows = remember(draft.text) {
        draft.text?.let(Suggestion::rows).orEmpty()
    }

    /** What sharing will export, for checking before it leaves the phone */
    val anonymized = remember(draft.text, draft.expected, draft.marked) {
        draft.text?.let { ReceiptAnonymizer.anonymize(it, draft.expected, draft.marked) }
    }
    val expected = draft.expected
    val none = stringResource(R.string.none)

    val close = { if (draft.isDirty) confirmDiscard = true else viewModel.discard() }
    BackHandler(onBack = close)
    BackHandler(enabled = selectedRows.isNotEmpty()) { selectedRows = emptySet() }

    DetailScaffold(
        onBack = close,
        title = draft.name.ifEmpty { stringResource(R.string.new_receipt) },
        actions = {
            IconButton(onClick = viewModel::recognizeAgain, enabled = !draft.isRecognizing) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.recognize_again)
                )
            }
            if (draft.previousName != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
            IconButton(
                onClick = viewModel::save,
                enabled =
                    !draft.isRecognizing && draft.text != null
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
            }
        },
        // Stays visible while scrolling through the rows to select
        header = if (selectedRows.isEmpty()) {
            null
        } else {
            {
                SelectionBar(
                    text = pluralStringResource(
                        R.plurals.rows_selected,
                        selectedRows.size,
                        selectedRows.size
                    ),
                    actionLabel = stringResource(R.string.combine_rows),
                    onCancel = { selectedRows = emptySet() },
                    onAction = {
                        val chosen = selectedRows.sorted()
                        itemEdit = ItemEdit(
                            null,
                            Suggestion.combine(chosen.map { rows[it] }),
                            Suggestion.insertionIndex(rows, expected.items, chosen.first())
                        )
                        selectedRows = emptySet()
                    }
                )
            }
        }
    ) {
        DetailCard {
            ReceiptImage(
                draft.photo,
                draft.text,
                showBoxes,
                redactions = anonymized?.redactions?.takeIf { showExport },
                onTapLine = if (showExport) viewModel::toggleMark else null
            )
            if (draft.isRecognizing) {
                ProgressRow(stringResource(R.string.recognizing))
            } else {
                SwitchRow(
                    label = stringResource(R.string.show_boxes),
                    checked = showBoxes,
                    onCheckedChange = { showBoxes = it },
                    supportingText = stringResource(R.string.show_boxes_hint)
                )
                SwitchRow(
                    label = stringResource(R.string.show_export),
                    checked = showExport,
                    onCheckedChange = { showExport = it },
                    supportingText = stringResource(R.string.show_export_hint)
                )
            }
        }

        SectionHeader(stringResource(R.string.details))
        DetailCard {
            EditableRow(stringResource(R.string.name), draft.name.ifEmpty { none }, {
                field =
                    Field.NAME
            }, isFirst = true)
            EditableRow(stringResource(R.string.merchant), expected.merchant ?: none, {
                field =
                    Field.MERCHANT
            })
            EditableRow(
                stringResource(
                    R.string.total
                ),
                expected.total?.let(::formatCents) ?: none,
                {
                    field =
                        Field.TOTAL
                }
            )
            EditableRow(
                stringResource(
                    R.string.item_count
                ),
                expected.itemCount?.toString() ?: none,
                {
                    field =
                        Field.ITEM_COUNT
                }
            )
            EditableRow(
                stringResource(R.string.transaction_amount),
                expected.transactionAmount?.let(::formatCents) ?: none,
                { field = Field.TRANSACTION_AMOUNT }
            )
        }

        val sum = expected.items.sumOf { it.amount }
        SectionHeader(
            stringResource(R.string.items),
            count = expected.items.size,
            trailing = formatCents(sum)
        )
        DetailCard {
            expected.items.forEachIndexed { index, item ->
                EditableItemRow(
                    title = item.name,
                    value = formatCents(item.amount),
                    removeLabel = stringResource(R.string.remove_item),
                    onClick = { itemEdit = ItemEdit(index, item) },
                    onRemove = {
                        viewModel.updateExpected {
                            it.copy(
                                items = it.items.filterIndexed { i, _ ->
                                    i !=
                                        index
                                }
                            )
                        }
                    },
                    supportingText = details(item),
                    isFirst = index == 0,
                    badge = ItemGroups.ofId(item.group)?.let { stringResource(it.label) }
                )
            }
            val difference = expected.total?.let { sum - it }
            if (difference != null && difference != 0L) {
                InfoRow(
                    stringResource(R.string.items_difference),
                    formatCents(difference),
                    isFirst = expected.items.isEmpty(),
                    valueColor = MaterialTheme.colorScheme.error
                )
            }
            LinkRow(
                stringResource(R.string.add_item),
                onClick = { itemEdit = ItemEdit(null, ExpectedItem("", 0)) },
                isFirst = expected.items.isEmpty() && difference in listOf(null, 0L)
            )
        }

        if (rows.isNotEmpty()) {
            SectionHeader(stringResource(R.string.recognized_rows), count = rows.size)
            Text(
                stringResource(R.string.recognized_rows_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
            )
            DetailCard {
                rows.forEachIndexed { index, row ->
                    SelectableTextRow(
                        text = row,
                        isFirst = index == 0,
                        selected = if (selectedRows.isEmpty()) null else index in selectedRows,
                        onClick = {
                            if (selectedRows.isEmpty()) {
                                itemEdit = ItemEdit(
                                    null,
                                    Suggestion.combine(listOf(row)),
                                    Suggestion.insertionIndex(rows, expected.items, index)
                                )
                            } else {
                                selectedRows =
                                    if (index in
                                        selectedRows
                                    ) {
                                        selectedRows - index
                                    } else {
                                        selectedRows + index
                                    }
                            }
                        },
                        onLongClick = { selectedRows = selectedRows + index }
                    )
                }
            }
        }

        anonymized?.let { result ->
            val exportRows = remember(result) { Suggestion.rows(result.text) }
            SectionHeader(stringResource(R.string.export_rows), count = exportRows.size)
            Text(
                stringResource(R.string.export_rows_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
            )
            DetailCard {
                exportRows.forEachIndexed { index, row ->
                    if (index > 0) RowDivider()
                    Text(
                        row,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.content, vertical = 10.dp)
                    )
                }
            }
        }
    }

    field?.let { current ->
        FieldDialog(current, draft, onDismiss = { field = null }, onConfirm = { value ->
            when (current) {
                Field.NAME -> viewModel.rename(value)

                Field.MERCHANT -> viewModel.updateExpected {
                    it.copy(merchant = value.trim().ifEmpty { null })
                }

                Field.TOTAL -> viewModel.updateExpected { it.copy(total = parseCents(value)) }

                Field.ITEM_COUNT -> viewModel.updateExpected {
                    it.copy(itemCount = value.trim().toIntOrNull())
                }

                Field.TRANSACTION_AMOUNT -> viewModel.updateExpected {
                    it.copy(transactionAmount = parseCents(value))
                }
            }
            field = null
        })
    }

    itemEdit?.let { edit ->
        ItemDialog(
            item = edit.item,
            onDismiss = { itemEdit = null },
            onConfirm = { item ->
                viewModel.updateExpected { receipt ->
                    receipt.copy(
                        items = receipt.items.toMutableList().also { items ->
                            when {
                                edit.index != null -> items[edit.index] = item

                                edit.insertAt != null -> items.add(
                                    edit.insertAt.coerceAtMost(items.size),
                                    item
                                )

                                else -> items += item
                            }
                        }
                    )
                }
                itemEdit = null
            }
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_text),
            confirmLabel = stringResource(R.string.discard),
            onConfirm = {
                confirmDiscard = false
                viewModel.discard()
            },
            onDismiss = { confirmDiscard = false }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_title),
            text = stringResource(R.string.delete_text, draft.previousName.orEmpty()),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** "2 × 0,89 · A", null for one piece without tax class */
private fun details(item: ExpectedItem) = listOfNotNull(
    if (item.quantity != "1" || item.unit != null || item.unitPrice != null) {
        listOfNotNull(
            item.quantity + (item.unit?.let { " $it" } ?: ""),
            item.unitPrice?.let(::formatCents)
        ).joinToString(" × ")
    } else {
        null
    },
    item.taxClass
).joinToString(" · ").ifEmpty { null }

@Composable
private fun FieldDialog(
    field: Field,
    draft: Draft,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val expected: ExpectedReceipt = draft.expected
    val (title, initial, keyboard) = when (field) {
        Field.NAME -> Triple(R.string.name, draft.name, KeyboardType.Ascii)

        Field.MERCHANT -> Triple(R.string.merchant, expected.merchant.orEmpty(), KeyboardType.Text)

        Field.TOTAL -> Triple(
            R.string.total,
            expected.total?.let(::formatCents).orEmpty(),
            KeyboardType.Decimal
        )

        Field.ITEM_COUNT -> Triple(
            R.string.item_count,
            expected.itemCount?.toString().orEmpty(),
            KeyboardType.Number
        )

        Field.TRANSACTION_AMOUNT -> Triple(
            R.string.transaction_amount,
            expected.transactionAmount?.let(::formatCents).orEmpty(),
            KeyboardType.Decimal
        )
    }
    val hint = when (field) {
        Field.NAME -> R.string.name_hint
        Field.TRANSACTION_AMOUNT -> R.string.transaction_amount_hint
        else -> null
    }
    TextFieldDialog(
        stringResource(title),
        initial,
        keyboard,
        hint?.let {
            stringResource(it)
        },
        onDismiss,
        onConfirm
    )
}
