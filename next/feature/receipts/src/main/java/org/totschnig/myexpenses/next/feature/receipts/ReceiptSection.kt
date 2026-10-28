package org.totschnig.myexpenses.next.feature.receipts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DecimalFormatSymbols
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.designsystem.ActionChip
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.BannerStyle
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.ProgressRow
import org.totschnig.myexpenses.designsystem.ReceiptLine
import org.totschnig.myexpenses.designsystem.ReceiptTable
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.ShareRow
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import timber.log.Timber
import org.totschnig.myexpenses.next.core.ui.receipts.labelRes
import org.totschnig.myexpenses.next.data.receipts.AmountInput
import org.totschnig.myexpenses.next.data.receipts.Receipt
import org.totschnig.myexpenses.next.data.receipts.ReceiptItem
import org.totschnig.myexpenses.next.data.receipts.ReceiptTarget
import org.totschnig.myexpenses.next.data.receipts.itemGroupShares

/** Long receipts show only this many items in the details at first */
private const val COLLAPSED_ITEMS = 15

/** The saved receipt of a transaction once it is loaded, [receipt] is null if there is none */
private class LoadedReceipt(val receipt: Receipt?)

/**
 * Starts the document scanner, or the photo picker without it, and hands the photo to [onPhoto]
 */
@Composable
internal fun rememberReceiptScanner(viewModel: ReceiptViewModel, onPhoto: (Uri) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.documentScanResult(result.data)?.let(onPhoto)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPhoto)
    }
    return {
        scope.launch {
            val intentSender = activity?.let { viewModel.documentScanIntent(it) }
            if (intentSender != null) {
                scanner.launch(IntentSenderRequest.Builder(intentSender).build())
            } else {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
}

/** The saved receipt of [transaction] once loaded, the outer null while it is loading */
@Composable
private fun rememberLoadedReceipt(viewModel: ReceiptViewModel, transaction: ShownTransaction): LoadedReceipt? {
    val loaded by remember(transaction.id) { viewModel.receipt(transaction.id, transaction.payeeName).map(::LoadedReceipt) }
        .collectAsStateWithLifecycle(null)
    return loaded
}

/**
 * Receipt of a purchase in its details: the progress of reading it and the saved receipt.
 * Shown only while there is one of them, adding a receipt is offered by [ReceiptChip].
 * Checking and correcting the items happens in [ReceiptReviewDialog].
 */
@Composable
fun ReceiptSection(viewModel: ReceiptViewModel, transaction: ShownTransaction) {
    if (!transaction.canHaveReceipt) return
    val payee = transaction.payeeName
    val loaded = rememberLoadedReceipt(viewModel, transaction)
    val target = ReceiptTarget.Transaction(transaction.id)
    val scan = viewModel.scan.collectAsStateWithLifecycle().value?.takeIf { it.target == target }
    val draft = viewModel.draft.collectAsStateWithLifecycle().value?.takeIf { it.target == target && !it.isStandalone }
    val amount = transaction.amount.absoluteValue
    val startScan = rememberReceiptScanner(viewModel) { viewModel.recognize(transaction.id, amount, payee, it) }
    val currency = transaction.currency
    val formatter = LocalAmountFormatter.current
    val receipt = loaded?.receipt

    if (draft != null) ReceiptReviewDialog(viewModel, currency)
    if (scan == null && receipt == null) return
    SectionTitle(stringResource(R.string.next_receipt_title))
    when (scan) {
        is ReceiptScanState.Recognizing -> DetailCard {
            ProgressRow(stringResource(R.string.next_receipt_reading), isFirst = true)
        }

        is ReceiptScanState.Failed -> Banner(
            title = stringResource(R.string.next_receipt_failed),
            messages = listOf(
                stringResource(
                    when (scan.reason) {
                        ReceiptScanState.Reason.NoRecognition -> R.string.next_receipt_no_recognition
                        ReceiptScanState.Reason.Error -> R.string.next_receipt_error
                        ReceiptScanState.Reason.NoItems -> R.string.next_receipt_no_items
                    }
                )
            ),
            style = BannerStyle.Alert
        ) {
            TextButton(onClick = viewModel::dismissScan) { Text(stringResource(R.string.next_receipt_close)) }
            TextButton(onClick = startScan) { Text(stringResource(R.string.next_receipt_retry)) }
        }

        null -> SavedReceipt(
            receipt = receipt!!,
            transactionAmount = amount,
            format = { formatter.format(it, currency) },
            input = remember(currency) {
                AmountInput(currency.fractionDigits, DecimalFormatSymbols.getInstance().decimalSeparator)
            },
            viewable = viewModel::viewableUri,
            onEdit = { viewModel.edit(transaction.id, amount, payee, receipt) },
            onDelete = { viewModel.deleteReceipt(transaction.id) }
        )
    }
}

/**
 * Chip for the [org.totschnig.myexpenses.designsystem.ActionChipRow] below the details of a
 * purchase without receipt: scans one. Left out while the receipt is loading or being read.
 */
@Composable
fun ReceiptChip(viewModel: ReceiptViewModel, transaction: ShownTransaction) {
    if (!transaction.canHaveReceipt) return
    val loaded = rememberLoadedReceipt(viewModel, transaction)
    val scan = viewModel.scan.collectAsStateWithLifecycle().value
        ?.takeIf { it.target == ReceiptTarget.Transaction(transaction.id) }
    val startScan = rememberReceiptScanner(viewModel) {
        viewModel.recognize(transaction.id, transaction.amount.absoluteValue, transaction.payeeName, it)
    }
    if (loaded == null || loaded.receipt != null || scan != null) return
    ActionChip(
        text = stringResource(R.string.next_receipt_add),
        icon = Icons.Default.DocumentScanner,
        onClick = startScan
    )
}

/**
 * What the purchase went to, by product type. Shown once at least one item has a type; the items
 * without type lead to the review, where they can be assigned.
 */
@Composable
private fun ItemGroupShares(items: List<ReceiptItem>, format: (Long) -> String, onAssign: () -> Unit) {
    val shares = itemGroupShares(items)
    if (shares.none { it.group != null }) return
    SectionTitle(stringResource(R.string.next_item_groups))
    DetailCard {
        shares.forEachIndexed { index, share ->
            val group = share.group
            ShareRow(
                label = group?.let { stringResource(it.labelRes) }
                    ?: pluralStringResource(R.plurals.next_item_groups_missing, share.itemCount, share.itemCount),
                value = "${format(share.amount)} · ${(share.share * 100).roundToInt()} %",
                share = share.share,
                isFirst = index == 0,
                isMuted = group == null,
                onClick = if (group == null) onAssign else null
            )
        }
    }
}

/**
 * Saved receipt: all items with their prices like on a clean receipt, and what can be done with it.
 * A difference to the transaction is shown, so that a wrong item is not forgotten.
 */
@Composable
private fun SavedReceipt(
    receipt: Receipt,
    transactionAmount: Long,
    format: (Long) -> String,
    input: AmountInput,
    /** The photo as an uri other apps can open */
    viewable: (Uri) -> Uri,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val itemsSum = receipt.items.sumOf { it.amount }
    DetailCard {
        ReceiptTable(
            title = receipt.merchant,
            itemHeader = stringResource(R.string.next_receipt_column_item),
            amountHeader = stringResource(R.string.next_receipt_column_price),
            lines = receipt.items.map { ReceiptLine(it.name, format(it.amount), it.quantityText(input, format)) },
            totalLabel = stringResource(R.string.next_receipt_sum),
            total = format(itemsSum),
            collapsedLines = COLLAPSED_ITEMS,
            showAllLabel = pluralStringResource(R.plurals.next_receipt_show_all, receipt.items.size, receipt.items.size)
        )
        if (itemsSum != transactionAmount) {
            InfoRow(
                stringResource(R.string.next_receipt_difference),
                format(itemsSum - transactionAmount),
                valueColor = MaterialTheme.colorScheme.error
            )
        }
        receipt.photo?.let { photo ->
            LinkRow(stringResource(R.string.next_receipt_photo), onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(viewable(photo), "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                } catch (e: ActivityNotFoundException) {
                    Timber.w(e)
                }
            })
        }
        LinkRow(stringResource(R.string.next_receipt_edit), onClick = onEdit)
        LinkRow(stringResource(R.string.next_receipt_delete), onClick = { confirmDelete = true })
    }
    ItemGroupShares(receipt.items, format, onAssign = onEdit)
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.next_receipt_delete),
            text = stringResource(R.string.next_receipt_delete_text),
            confirmLabel = stringResource(R.string.next_receipt_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
