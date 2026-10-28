package org.totschnig.myexpenses.next.feature.receipts

import org.totschnig.myexpenses.designsystem.isWideWindow
import org.totschnig.myexpenses.designsystem.Spacing
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.designsystem.BadgeStyle
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.BannerStyle
import org.totschnig.myexpenses.designsystem.ChoiceChips
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DateDialog
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.EditableRow
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.NameDialog
import org.totschnig.myexpenses.designsystem.SectionHeader
import org.totschnig.myexpenses.designsystem.SelectableTextRow
import org.totschnig.myexpenses.designsystem.SelectionBar
import org.totschnig.myexpenses.designsystem.EditableItemRow
import org.totschnig.myexpenses.designsystem.FormDialog
import org.totschnig.myexpenses.designsystem.FormSheet
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.MarkedPhoto
import org.totschnig.myexpenses.designsystem.PhotoMark
import org.totschnig.myexpenses.designsystem.ProgressRow
import org.totschnig.myexpenses.designsystem.Shapes
import org.totschnig.myexpenses.feature.Box as TextBox
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.common.R
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup
import org.totschnig.myexpenses.next.core.ui.receipts.labelRes
import org.totschnig.myexpenses.next.data.receipts.AmountInput
import org.totschnig.myexpenses.next.data.receipts.LeftoverRow
import org.totschnig.myexpenses.next.data.receipts.ReceiptCheck
import org.totschnig.myexpenses.next.data.receipts.ReceiptDraft
import org.totschnig.myexpenses.next.data.receipts.ReceiptItem
import org.totschnig.myexpenses.next.data.receipts.ReceiptParser
import org.totschnig.myexpenses.next.data.receipts.ReceiptTarget

/** Index of the item being edited for a new one */
private const val NEW_ITEM = -1

/**
 * Checks and corrects [ReceiptViewModel.draft] before saving it: the photo with the items framed,
 * shop, date and amounts as read, the items with their sum and how far it is from the receipt and
 * the transaction. Tapping an item or its row on the photo edits it, removing it can be undone.
 */
@Composable
fun ReceiptReviewDialog(viewModel: ReceiptViewModel, currency: CurrencyInfo) {
    val draft = viewModel.draft.collectAsStateWithLifecycle().value ?: return
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val formatter = LocalAmountFormatter.current
    val format: (Long) -> String = { formatter.format(it, currency) }
    val input = remember(currency) {
        AmountInput(currency.fractionDigits, DecimalFormatSymbols.getInstance().decimalSeparator)
    }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    // The item last opened, highlighted on the photo
    var focused by rememberSaveable { mutableStateOf<Int?>(null) }
    var photoExpanded by rememberSaveable { mutableStateOf(false) }
    var editingDetail by rememberSaveable { mutableStateOf<Detail?>(null) }
    var confirmRecognizeAgain by rememberSaveable { mutableStateOf(false) }
    val recognizingAgain by viewModel.recognizingAgain.collectAsStateWithLifecycle()
    val recognizeAgainFailed by viewModel.recognizeAgainFailed.collectAsStateWithLifecycle()
    // Rows chosen to make up one item, empty while not choosing
    var chosenRows by remember(draft.leftoverRows) { mutableStateOf(emptySet<LeftoverRow>()) }
    // An item read from rows not taken, with where it goes, while the user checks it
    var newItem by remember { mutableStateOf<Pair<ReceiptItem, Int>?>(null) }
    val takeRows = { rows: List<LeftoverRow> ->
        val item = ReceiptParser.itemOf(rows)
        newItem = item to draft.insertionIndex(item.box!!)
        chosenRows = emptySet()
    }
    val openItem = { index: Int -> editing = index; focused = index }
    // Items without group at the start of going through them, and how far it got
    var walkThrough by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    var walkPosition by rememberSaveable { mutableIntStateOf(0) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val close = {
        when {
            chosenRows.isNotEmpty() -> chosenRows = emptySet()
            draft.isNewPhoto || draft.isChanged -> confirmDiscard = true
            else -> viewModel.closeDraft()
        }
    }
    val removedMessage = stringResource(R.string.next_receipt_item_removed)
    val undoLabel = stringResource(R.string.next_receipt_undo)

    // In wide windows the photo and the rows read from it are side by side, each scrolling by itself
    val isSideBySide = isWideWindow() && photo != null
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            Box {
                DetailScaffold(
                    onBack = close,
                    title = stringResource(R.string.next_receipt_review),
                    scrollable = !isSideBySide,
                    actions = {
                        if (draft.photo != null && viewModel.canRecognize) {
                            IconButton(
                                onClick = { if (draft.isChanged) confirmRecognizeAgain = true else viewModel.recognizeAgain() },
                                enabled = !recognizingAgain
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.next_receipt_recognize_again))
                            }
                        }
                        IconButton(onClick = viewModel::saveDraft, enabled = draft.items.isNotEmpty() && !recognizingAgain) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.next_receipt_save))
                        }
                    },
                    header = {
                        if (chosenRows.isNotEmpty()) SelectionBar(
                            text = pluralStringResource(R.plurals.next_receipt_rows_chosen, chosenRows.size, chosenRows.size),
                            actionLabel = stringResource(R.string.next_receipt_take_rows),
                            onAction = { takeRows(draft.openLeftoverRows.filter { it in chosenRows }) },
                            onCancel = { chosenRows = emptySet() }
                        ) else Column {
                            if (recognizingAgain) {
                                DetailCard { ProgressRow(stringResource(R.string.next_receipt_reading), isFirst = true) }
                            }
                            recognizeAgainFailed?.let { reason ->
                                Banner(
                                    title = stringResource(R.string.next_receipt_failed),
                                    messages = listOf(stringResource(reason.messageRes)),
                                    style = BannerStyle.Alert
                                ) {
                                    TextButton(onClick = viewModel::dismissRecognizeAgainFailed) {
                                        Text(stringResource(R.string.next_receipt_close))
                                    }
                                }
                            }
                            CountBanner(draft.check)
                            MissingGroupsBanner(draft.itemsWithoutGroup.size) {
                                walkThrough = draft.itemsWithoutGroup
                                walkPosition = 0
                            }
                        }
                    }
                ) {
                    val photoCard: @Composable () -> Unit = {
                        photo?.let { current ->
                            DetailCard {
                                ReceiptPhotoView(current, draft.items, draft.openLeftoverRows, focused, photoExpanded, onTapItem = openItem)
                                LinkRow(
                                    stringResource(if (photoExpanded) R.string.next_receipt_photo_collapse else R.string.next_receipt_photo_expand),
                                    onClick = { photoExpanded = !photoExpanded }
                                )
                            }
                        }
                    }
                    val receipt: @Composable ColumnScope.() -> Unit = {
                        SectionHeader(stringResource(R.string.next_receipt_title))
                        ReceiptDetails(draft, format, onEdit = { editingDetail = it })
                        SectionHeader(
                            stringResource(R.string.next_receipt_items),
                            count = draft.items.size,
                            trailing = format(draft.items.sumOf { it.amount })
                        )
                        DetailCard {
                            draft.items.forEachIndexed { index, item ->
                                EditableItemRow(
                                    title = item.name,
                                    value = format(item.amount),
                                    removeLabel = stringResource(R.string.next_receipt_remove_item),
                                    onClick = { openItem(index) },
                                    onRemove = {
                                        viewModel.removeItem(index)
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            val result = snackbarHostState.showSnackbar(
                                                String.format(removedMessage, item.name),
                                                actionLabel = undoLabel,
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) viewModel.insertItem(index, item)
                                        }
                                    },
                                    supportingText = item.quantityText(input, format),
                                    isFirst = index == 0,
                                    badge = when {
                                        item.group != null -> ItemGroup.ofId(item.group)?.let { stringResource(it.labelRes) }
                                        item.amount > 0 -> stringResource(R.string.next_item_group_missing)
                                        else -> null
                                    },
                                    badgeStyle = if (item.group == null) BadgeStyle.Missing else BadgeStyle.Outlined
                                )
                            }
                            DifferenceRows(draft, format, isFirst = draft.items.isEmpty())
                            LinkRow(
                                stringResource(R.string.next_receipt_add_item),
                                onClick = { editing = NEW_ITEM },
                                isFirst = draft.items.isEmpty() && draft.check.isConsistent
                            )
                        }
                        LeftoverRows(
                            rows = draft.openLeftoverRows,
                            chosen = chosenRows,
                            onTake = { takeRows(listOf(it)) },
                            onChoose = { row -> chosenRows = if (row in chosenRows) chosenRows - row else chosenRows + row }
                        )
                    }
                    if (isSideBySide) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Spacing.screen)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { photoCard() }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = receipt)
                        }
                    } else {
                        photoCard()
                        receipt()
                    }
                }
                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
            }
            newItem?.let { (item, index) ->
                ReceiptItemSheet(
                    item = item,
                    isNew = true,
                    photo = photo,
                    input = input,
                    onConfirm = {
                        viewModel.insertItem(index, it)
                        focused = index
                        newItem = null
                    },
                    onDismiss = { newItem = null }
                )
            }
            editing?.let { index ->
                ReceiptItemSheet(
                    item = draft.items.getOrNull(index),
                    photo = photo,
                    input = input,
                    onConfirm = { item ->
                        if (index == NEW_ITEM) viewModel.insertItem(draft.items.size, item)
                        else viewModel.updateItem(index, item)
                        editing = null
                    },
                    onDismiss = { editing = null }
                )
            }
            walkThrough?.let { queue ->
                // Choosing a group may also fill later items of the same product
                val current = queue.drop(walkPosition).firstOrNull { draft.items.getOrNull(it)?.group == null }
                if (current == null) {
                    walkThrough = null
                } else {
                    val next = { walkPosition = queue.indexOf(current) + 1 }
                    ItemGroupStepSheet(
                        item = draft.items[current],
                        step = queue.indexOf(current) + 1,
                        steps = queue.size,
                        photo = photo,
                        format = format,
                        onSelect = { group ->
                            viewModel.setGroup(current, group)
                            next()
                        },
                        onSkip = next,
                        onDone = { walkThrough = null }
                    )
                }
            }
            editingDetail?.let { detail ->
                DetailDialog(
                    detail = detail,
                    draft = draft,
                    input = input,
                    onDismiss = { editingDetail = null },
                    onMerchant = viewModel::setMerchant,
                    onDate = viewModel::setDate,
                    onAmount = { amount ->
                        if (detail == Detail.TOTAL) viewModel.setTotal(amount) else viewModel.setCardAmount(amount)
                    }
                )
            }
            if (confirmRecognizeAgain) {
                ConfirmDialog(
                    title = stringResource(R.string.next_receipt_recognize_again),
                    text = stringResource(R.string.next_receipt_recognize_again_text),
                    confirmLabel = stringResource(R.string.next_receipt_recognize_again),
                    onConfirm = {
                        confirmRecognizeAgain = false
                        viewModel.recognizeAgain()
                    },
                    onDismiss = { confirmRecognizeAgain = false }
                )
            }
            if (confirmDiscard) {
                ConfirmDialog(
                    title = stringResource(R.string.next_receipt_discard_changes),
                    text = stringResource(R.string.next_receipt_discard_changes_text),
                    confirmLabel = stringResource(R.string.next_receipt_discard),
                    onConfirm = {
                        confirmDiscard = false
                        viewModel.closeDraft()
                    },
                    onDismiss = { confirmDiscard = false }
                )
            }
        }
    }
}

/** "3 × 0,59 €" or "0,512 kg × 2,99 €", null for a single piece */
internal fun ReceiptItem.quantityText(input: AmountInput, format: (Long) -> String): String? {
    if (quantity.compareTo(BigDecimal.ONE) == 0 && unitPrice == null) return null
    val amount = input.formatQuantity(quantity) + (unit?.let { " $it" } ?: "")
    return unitPrice?.let { "$amount × ${format(it)}" } ?: amount
}

/** What can be corrected of the head and foot of the receipt */
private enum class Detail { MERCHANT, DATE, TOTAL, CARD_AMOUNT }

private const val NONE = "–"

/**
 * Shop, date, total and card amount as read, each corrected by tapping it, and the amount of the
 * transaction the items are checked against
 */
@Composable
private fun ReceiptDetails(draft: ReceiptDraft, format: (Long) -> String, onEdit: (Detail) -> Unit) {
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    DetailCard {
        EditableRow(stringResource(R.string.next_receipt_merchant), draft.merchant ?: NONE, { onEdit(Detail.MERCHANT) }, isFirst = true)
        EditableRow(stringResource(R.string.next_receipt_date), draft.date?.format(dateFormat) ?: NONE, { onEdit(Detail.DATE) })
        EditableRow(stringResource(R.string.next_receipt_sum), draft.total?.let(format) ?: NONE, { onEdit(Detail.TOTAL) })
        val cardAmount = draft.cardAmount
        if (cardAmount != null && cardAmount != draft.total) {
            EditableRow(stringResource(R.string.next_receipt_card_amount), format(cardAmount), { onEdit(Detail.CARD_AMOUNT) })
        }
        if (draft.target is ReceiptTarget.Transaction) {
            InfoRow(stringResource(R.string.next_receipt_transaction_amount), format(draft.transactionAmount))
        }
    }
}

/** Below the items, in the error colour: how far they are from the total, and the total from the transaction */
@Composable
private fun DifferenceRows(draft: ReceiptDraft, format: (Long) -> String, isFirst: Boolean) {
    val check = draft.check
    val error = MaterialTheme.colorScheme.error
    var first = isFirst
    if (check.itemsDifference != 0L) {
        InfoRow(
            stringResource(if (draft.total != null) R.string.next_receipt_items_difference else R.string.next_receipt_difference),
            format(check.itemsDifference), isFirst = first, valueColor = error
        )
        first = false
    }
    if (draft.target is ReceiptTarget.Transaction) {
        check.totalDifference?.takeIf { it != 0L }?.let {
            InfoRow(stringResource(R.string.next_receipt_total_difference), format(it), isFirst = first, valueColor = error)
        }
    }
}

/**
 * Rows among the items no item was read from: tapping one takes it as an item, a long press
 * chooses several for one item, e.g. a name and its price in two rows
 */
@Composable
private fun LeftoverRows(
    rows: List<LeftoverRow>,
    chosen: Set<LeftoverRow>,
    onTake: (LeftoverRow) -> Unit,
    onChoose: (LeftoverRow) -> Unit,
) {
    if (rows.isEmpty()) return
    SectionHeader(stringResource(R.string.next_receipt_leftover_title), count = rows.size)
    Text(
        stringResource(R.string.next_receipt_leftover_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
    )
    DetailCard {
        rows.forEachIndexed { index, row ->
            SelectableTextRow(
                text = row.text,
                selected = if (chosen.isEmpty()) null else row in chosen,
                onClick = { if (chosen.isEmpty()) onTake(row) else onChoose(row) },
                onLongClick = { onChoose(row) },
                isFirst = index == 0
            )
        }
    }
}

/** Only when the number of articles printed on the receipt differs from the items read */
@Composable
private fun CountBanner(check: ReceiptCheck) {
    if (!check.countDiffers) return
    Banner(
        title = stringResource(R.string.next_receipt_count_title),
        messages = listOf(check.receipt.itemCount!!.let { pluralStringResource(R.plurals.next_receipt_count_differs, it, it, check.receipt.pieces) }),
        style = BannerStyle.Alert
    )
}

/** Corrects one of the [Detail]s */
@Composable
private fun DetailDialog(
    detail: Detail,
    draft: ReceiptDraft,
    input: AmountInput,
    onDismiss: () -> Unit,
    onMerchant: (String?) -> Unit,
    onDate: (java.time.LocalDate?) -> Unit,
    onAmount: (Long?) -> Unit,
) {
    when (detail) {
        Detail.MERCHANT -> NameDialog(
            title = stringResource(R.string.next_receipt_merchant),
            initialName = draft.merchant.orEmpty(),
            onConfirm = { onMerchant(it.trim().ifEmpty { null }); onDismiss() },
            onDismiss = onDismiss,
            allowBlank = true
        )

        Detail.DATE -> DateDialog(
            initial = draft.date,
            onConfirm = { onDate(it); onDismiss() },
            onDismiss = onDismiss
        )

        Detail.TOTAL, Detail.CARD_AMOUNT -> {
            val initial = if (detail == Detail.TOTAL) draft.total else draft.cardAmount
            var text by rememberSaveable { mutableStateOf(initial?.let(input::format).orEmpty()) }
            val parsed = input.parse(text)
            FormDialog(
                title = stringResource(if (detail == Detail.TOTAL) R.string.next_receipt_sum else R.string.next_receipt_card_amount),
                onConfirm = { onAmount(parsed); onDismiss() },
                onDismiss = onDismiss,
                confirmEnabled = text.isBlank() || parsed != null
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.next_receipt_item_amount)) },
                    isError = text.isNotBlank() && parsed == null,
                    supportingText = if (text.isNotBlank() && parsed == null) {
                        { Text(stringResource(R.string.next_amount_invalid)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Invites to go through the items without group, unless there are none */
@Composable
private fun MissingGroupsBanner(count: Int, onAssign: () -> Unit) {
    if (count == 0) return
    Banner(
        title = pluralStringResource(R.plurals.next_item_groups_missing, count, count),
        messages = listOf(stringResource(R.string.next_item_groups_missing_text)),
        actions = {
            TextButton(onClick = onAssign) { Text(stringResource(R.string.next_item_groups_assign)) }
        }
    )
}

/** One item without group after the other: choosing a group goes on to the next */
@Composable
private fun ItemGroupStepSheet(
    item: ReceiptItem,
    step: Int,
    steps: Int,
    photo: ReceiptPhoto?,
    format: (Long) -> String,
    onSelect: (ItemGroup) -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    FormSheet(
        title = stringResource(R.string.next_item_group_step, step, steps),
        onConfirm = onSkip,
        onDismiss = onDone,
        confirmLabel = stringResource(R.string.next_item_group_skip),
        dismissLabel = stringResource(R.string.next_item_group_done)
    ) {
        val box = item.box
        if (photo != null && box != null) PhotoExcerpt(photo, box)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(format(item.amount), style = MaterialTheme.typography.titleMedium)
        }
        ItemGroupChoice(selected = null, onSelect = onSelect)
    }
}

@Composable
private fun ItemGroupChoice(selected: ItemGroup?, onSelect: (ItemGroup) -> Unit) {
    ChoiceChips(
        options = ItemGroup.entries,
        selected = selected,
        label = { stringResource(it.labelRes) },
        onSelect = onSelect
    )
}

/**
 * Edits an item or adds a new one ([item] null). Shows the row of the photo it was read from,
 * so that it can be compared without the paper receipt.
 */
@Composable
private fun ReceiptItemSheet(
    item: ReceiptItem?,
    photo: ReceiptPhoto?,
    isNew: Boolean = item == null,
    input: AmountInput,
    onConfirm: (ReceiptItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(item?.name ?: "") }
    var amount by rememberSaveable { mutableStateOf(item?.amount?.let(input::format) ?: "") }
    var showQuantity by rememberSaveable {
        mutableStateOf(item != null && (item.quantity.compareTo(BigDecimal.ONE) != 0 || item.unitPrice != null))
    }
    var quantity by rememberSaveable { mutableStateOf(input.formatQuantity(item?.quantity ?: BigDecimal.ONE)) }
    var unitPrice by rememberSaveable { mutableStateOf(item?.unitPrice?.let(input::format) ?: "") }
    var group by rememberSaveable { mutableStateOf(item?.group) }
    // Amount follows quantity and unit price, it can still be overwritten
    val recalculate = {
        val q = input.parseQuantity(quantity)
        val u = input.parse(unitPrice)
        if (q != null && u != null) amount = input.format(input.times(q, u))
    }
    val parsedAmount = input.parse(amount)
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    FormSheet(
        title = stringResource(if (isNew) R.string.next_receipt_add_item else R.string.next_receipt_edit_item),
        onConfirm = {
            onConfirm(
                ReceiptItem(
                    name = name.trim(),
                    amount = parsedAmount!!,
                    quantity = input.parseQuantity(quantity)?.takeIf { showQuantity } ?: BigDecimal.ONE,
                    unit = item?.unit,
                    unitPrice = input.parse(unitPrice)?.takeIf { showQuantity },
                    taxClass = item?.taxClass,
                    box = item?.box,
                    group = group,
                    groupByUser = item?.groupByUser == true || (group != null && group != item?.group)
                )
            )
        },
        onDismiss = onDismiss,
        confirmEnabled = name.isNotBlank() && parsedAmount != null && (!isNew || parsedAmount != 0L)
    ) {
        val box = item?.box
        if (photo != null && box != null) PhotoExcerpt(photo, box)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.next_receipt_item_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (showQuantity) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it; recalculate() },
                    label = { Text(stringResource(R.string.next_receipt_item_quantity)) },
                    suffix = item?.unit?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { unitPrice = it; recalculate() },
                    label = { Text(stringResource(R.string.next_receipt_item_unit_price)) },
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            TextButton(onClick = { showQuantity = true }) {
                Text(stringResource(R.string.next_receipt_add_quantity))
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(stringResource(R.string.next_receipt_item_amount)) },
            isError = amount.isNotEmpty() && parsedAmount == null,
            supportingText = if (amount.isNotEmpty() && parsedAmount == null) {
                { Text(stringResource(R.string.next_amount_invalid)) }
            } else null,
            singleLine = true,
            keyboardOptions = decimal,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.next_item_group),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        ItemGroupChoice(selected = ItemGroup.ofId(group), onSelect = { group = it.id })
    }
}

/** Height of the photo until the user expands it */
private val COLLAPSED_PHOTO = 220.dp

/**
 * The photo of the receipt with a frame around each item read from it and, in the error colour,
 * around each row not taken; the [focused] item is highlighted and shown in the middle while collapsed. Tapping a
 * framed row opens its item.
 */
@Composable
private fun ReceiptPhotoView(
    photo: ReceiptPhoto,
    items: List<ReceiptItem>,
    leftoverRows: List<LeftoverRow>,
    focused: Int?,
    expanded: Boolean,
    onTapItem: (Int) -> Unit,
) {
    val bitmap = remember(photo) { photo.bitmap.asImageBitmap() }
    fun TextBox.rect() = Rect(left * photo.scale, top * photo.scale, right * photo.scale, bottom * photo.scale)
    val frame = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val leftover = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    val focusRect = focused?.let { items.getOrNull(it)?.box?.rect() }
    MarkedPhoto(
        bitmap = bitmap,
        marks = items.mapNotNull { item -> item.box?.let { PhotoMark(it.rect(), frame, 1.dp) } } +
                leftoverRows.map { PhotoMark(it.box.rect(), leftover, 1.dp) } +
                listOfNotNull(focusRect?.let { PhotoMark(it, highlight, filled = true) }),
        collapsedHeight = if (expanded) null else COLLAPSED_PHOTO,
        focus = focusRect,
        onTap = { point ->
            items.indices
                .filter { items[it].box?.rect()?.inflate(TAP_TOLERANCE * photo.scale)?.contains(point) == true }
                .minByOrNull { items[it].box!!.rect().height }
                ?.let(onTapItem)
        }
    )
}

/** Rows are hard to hit exactly on a phone, so a frame may be missed by this much */
private const val TAP_TOLERANCE = 15f

/** The row of the photo at [box], a little higher than the text, so that it is easy to read */
@Composable
private fun PhotoExcerpt(photo: ReceiptPhoto, box: TextBox) {
    val excerpt = remember(photo, box) {
        val bitmap = photo.bitmap
        val padding = (box.bottom - box.top) * 0.4f
        fun scaled(value: Float, max: Int) = (value * photo.scale).toInt().coerceIn(0, max)
        val left = scaled(box.left - padding, bitmap.width - 1)
        val right = scaled(box.right + padding, bitmap.width).coerceAtLeast(left + 1)
        val top = scaled(box.top - padding, bitmap.height - 1)
        val bottom = scaled(box.bottom + padding, bitmap.height).coerceAtLeast(top + 1)
        Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top).asImageBitmap()
    }
    Image(
        bitmap = excerpt,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.chip)
    )
}
