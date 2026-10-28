package org.totschnig.myexpenses.next.feature.receipts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.BannerStyle
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.ProgressRow
import org.totschnig.myexpenses.designsystem.RadioRow
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.common.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.absoluteValue
import org.totschnig.myexpenses.next.data.receipts.BookedTransaction
import org.totschnig.myexpenses.next.data.receipts.ReceiptMatch
import org.totschnig.myexpenses.next.data.receipts.ReceiptTarget

/** How many other expenses are shown at most, the search narrows them down */
private const val MAX_OTHER_EXPENSES = 50

/**
 * Scanning a receipt on its own from the overview: the button, the progress of reading it, the
 * choice of its transaction in [ReceiptAssignmentDialog], also of a waiting one, and checking it
 * in [ReceiptReviewDialog]. The button is shown only if receipts can be read.
 */
@Composable
fun ReceiptScanAction(viewModel: ReceiptViewModel) {
    val startScan = rememberReceiptScanner(viewModel, viewModel::recognizeUnassigned)
    if (viewModel.canRecognize) {
        ExtendedFloatingActionButton(
            onClick = startScan,
            icon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
            text = { Text(stringResource(R.string.next_receipt_scan)) }
        )
    }

    when (val scan = viewModel.scan.collectAsStateWithLifecycle().value?.takeIf { it.target == ReceiptTarget.Unassigned }) {
        is ReceiptScanState.Recognizing -> Dialog(onDismissRequest = {}) {
            DetailCard { ProgressRow(stringResource(R.string.next_receipt_reading), isFirst = true) }
        }

        is ReceiptScanState.Failed -> Dialog(onDismissRequest = viewModel::dismissScan) {
            Banner(
                title = stringResource(R.string.next_receipt_failed),
                messages = listOf(stringResource(scan.reason.messageRes)),
                style = BannerStyle.Alert
            ) {
                TextButton(onClick = viewModel::dismissScan) { Text(stringResource(R.string.next_receipt_close)) }
                TextButton(onClick = { viewModel.dismissScan(); startScan() }) {
                    Text(stringResource(R.string.next_receipt_retry))
                }
            }
        }

        null -> {}
    }

    if (viewModel.assignments.assignment.collectAsStateWithLifecycle().value != null) ReceiptAssignmentDialog(viewModel)
    val draft = viewModel.draft.collectAsStateWithLifecycle().value
    val currency = viewModel.assignments.standaloneCurrency
    if (draft?.isStandalone == true && currency != null) ReceiptReviewDialog(viewModel, currency)
}

internal val ReceiptScanState.Reason.messageRes: Int
    get() = when (this) {
        ReceiptScanState.Reason.NoRecognition -> R.string.next_receipt_no_recognition
        ReceiptScanState.Reason.Error -> R.string.next_receipt_error
        ReceiptScanState.Reason.NoItems -> R.string.next_receipt_no_items
    }

/**
 * Chooses the transaction of a receipt scanned on its own: what was read from the receipt, the
 * suggested transactions with the reasons, the sure one already chosen, other expenses of the
 * last weeks on request, and waiting for a transaction not yet booked.
 */
@Composable
fun ReceiptAssignmentDialog(viewModel: ReceiptViewModel) {
    val assignment = viewModel.assignments.assignment.collectAsStateWithLifecycle().value ?: return
    val others by viewModel.assignments.otherExpenses.collectAsStateWithLifecycle()
    val formatter = LocalAmountFormatter.current
    val home = viewModel.homeCurrency.collectAsStateWithLifecycle().value
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    var selectedId by rememberSaveable(assignment) { mutableStateOf(assignment.preselected?.transaction?.id) }
    var search by rememberSaveable(assignment) { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val selected = assignment.candidates.map { it.transaction }.plus(others.orEmpty()).find { it.id == selectedId }
    fun amountOf(transaction: BookedTransaction) =
        formatter.format(transaction.amount.absoluteValue, transaction.currency ?: home)
    fun supportingText(transaction: BookedTransaction) =
        listOfNotNull(transaction.date.format(dateFormat), transaction.accountLabel).joinToString(" · ")
    val noPayee = stringResource(R.string.next_receipt_no_payee)
    fun titleOf(transaction: BookedTransaction) =
        transaction.payeeName?.takeIf { it.isNotBlank() } ?: transaction.comment?.takeIf { it.isNotBlank() } ?: noPayee

    val scope = rememberCoroutineScope()
    // The new transaction then is among the candidates, chosen if amount and shop fit
    val newTransaction = rememberLauncherForActivityResult(LocalNewTransactionEditor.current) { saved ->
        if (saved) viewModel.assignments.refreshCandidates()
    }

    Dialog(onDismissRequest = viewModel.assignments::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
            DetailScaffold(
                onBack = viewModel.assignments::close,
                title = stringResource(R.string.next_receipt_assign_title),
                actions = {
                    TextButton(onClick = { selected?.let(viewModel.assignments::assign) }, enabled = selected != null) {
                        Text(stringResource(R.string.next_receipt_assign))
                    }
                }
            ) {
                ReceiptFacts(assignment, home, dateFormat)

                SectionTitle(stringResource(R.string.next_receipt_candidates))
                if (assignment.candidates.isEmpty()) {
                    Banner(
                        title = stringResource(R.string.next_receipt_no_candidates),
                        messages = listOf(stringResource(R.string.next_receipt_wait_hint))
                    )
                } else DetailCard {
                    assignment.candidates.forEachIndexed { index, match ->
                        RadioRow(
                            title = titleOf(match.transaction),
                            selected = match.transaction.id == selectedId,
                            onClick = { selectedId = match.transaction.id },
                            supportingText = supportingText(match.transaction),
                            note = reasons(match),
                            value = amountOf(match.transaction),
                            isFirst = index == 0
                        )
                    }
                }

                val otherList = others
                if (otherList == null) {
                    DetailCard {
                        LinkRow(stringResource(R.string.next_receipt_other), onClick = viewModel.assignments::loadOtherExpenses, isFirst = true)
                    }
                } else {
                    SectionTitle(stringResource(R.string.next_receipt_other_title))
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text(stringResource(R.string.next_receipt_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.content)
                    )
                    val candidateIds = assignment.candidates.map { it.transaction.id }.toSet()
                    val shown = otherList
                        .filter { it.id !in candidateIds && matches(it, search, ::titleOf, ::amountOf) }
                        .take(MAX_OTHER_EXPENSES)
                    if (shown.isNotEmpty()) DetailCard {
                        shown.forEachIndexed { index, transaction ->
                            RadioRow(
                                title = titleOf(transaction),
                                selected = transaction.id == selectedId,
                                onClick = { selectedId = transaction.id },
                                supportingText = supportingText(transaction),
                                value = amountOf(transaction),
                                isFirst = index == 0
                            )
                        }
                    }
                }

                SectionTitle(stringResource(R.string.next_receipt_wait_section))
                DetailCard {
                    if (assignment.waitingId == null) {
                        LinkRow(stringResource(R.string.next_receipt_wait), onClick = viewModel.assignments::keepWaiting, isFirst = true)
                    } else {
                        LinkRow(stringResource(R.string.next_receipt_keep_waiting), onClick = viewModel.assignments::close, isFirst = true)
                    }
                    LinkRow(stringResource(R.string.next_receipt_new_transaction), onClick = {
                        scope.launch {
                            viewModel.assignments.newTransactionPrefill()?.let { newTransaction.launch(it) }
                        }
                    })
                    if (assignment.waitingId != null) {
                        LinkRow(stringResource(R.string.next_receipt_discard_waiting), onClick = { confirmDelete = true })
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.next_receipt_discard_waiting),
            text = stringResource(R.string.next_receipt_discard_waiting_text),
            confirmLabel = stringResource(R.string.next_receipt_discard),
            onConfirm = {
                confirmDelete = false
                viewModel.assignments.deleteWaiting()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}


/** What was read from the receipt and is compared with the transactions */
@Composable
private fun ReceiptFacts(assignment: ReceiptAssignment, currency: CurrencyInfo, dateFormat: DateTimeFormatter) {
    val formatter = LocalAmountFormatter.current
    val receipt = assignment.receipt
    val rows = buildList {
        receipt.merchant?.let { add(stringResource(R.string.next_receipt_merchant) to it) }
        receipt.date?.let { add(stringResource(R.string.next_receipt_date) to it.format(dateFormat)) }
        receipt.total?.let { add(stringResource(R.string.next_receipt_sum) to formatter.format(it, currency)) }
        receipt.cardAmount?.takeIf { it != receipt.total }?.let {
            add(stringResource(R.string.next_receipt_card_amount) to formatter.format(it, currency))
        }
        add(stringResource(R.string.next_receipt_column_item) to pluralStringResource(
            R.plurals.next_receipt_item_count, receipt.items.size, receipt.items.size
        ))
    }
    DetailCard {
        rows.forEachIndexed { index, (label, value) -> InfoRow(label, value, isFirst = index == 0) }
    }
}

/** Why a transaction is suggested: "Same amount · Same shop · 1 day later" */
@Composable
private fun reasons(match: ReceiptMatch): String = listOfNotNull(
    stringResource(R.string.next_receipt_reason_amount).takeIf { match.amountFits },
    stringResource(R.string.next_receipt_reason_merchant).takeIf { match.merchantFits },
    match.daysAfter?.let { days ->
        when {
            days < 0 -> stringResource(R.string.next_receipt_reason_day_before)
            days == 0L -> stringResource(R.string.next_receipt_reason_same_day)
            else -> pluralStringResource(R.plurals.next_receipt_reason_days_after, days.toInt(), days.toInt())
        }
    }
).joinToString(" · ")

/** The search finds a transaction by its title, purpose or amount as shown */
private fun matches(
    transaction: BookedTransaction,
    search: String,
    titleOf: (BookedTransaction) -> String,
    amountOf: (BookedTransaction) -> String,
): Boolean {
    val query = search.trim()
    if (query.isEmpty()) return true
    return listOfNotNull(titleOf(transaction), transaction.comment, amountOf(transaction))
        .any { it.contains(query, ignoreCase = true) }
}

/**
 * Receipts waiting for their transaction on the overview: how many, the first ones with whether
 * their transaction has been booked meanwhile, and opening the next one to assign
 */
@Composable
fun WaitingReceiptsBanner(viewModel: ReceiptViewModel, modifier: Modifier = Modifier) {
    val waiting = viewModel.assignments.waiting.collectAsStateWithLifecycle(emptyList()).value
    val next = waiting.firstOrNull() ?: return
    val formatter = LocalAmountFormatter.current
    val home = viewModel.homeCurrency.collectAsStateWithLifecycle().value
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val found = stringResource(R.string.next_receipt_waiting_found)
    val notYet = stringResource(R.string.next_receipt_waiting_not_yet)
    val unknownShop = stringResource(R.string.next_receipt_title)
    Banner(
        title = pluralStringResource(R.plurals.next_receipt_waiting, waiting.size, waiting.size),
        messages = waiting.take(WAITING_SHOWN).map { assignment ->
            val receipt = assignment.receipt
            listOfNotNull(
                receipt.merchant ?: unknownShop,
                receipt.date?.format(dateFormat),
                receipt.total?.let { formatter.format(it, home) }
            ).joinToString(" · ") + " – " + if (assignment.candidates.isNotEmpty()) found else notYet
        },
        modifier = modifier
    ) {
        TextButton(onClick = { viewModel.assignments.openWaiting(next) }) {
            Text(stringResource(if (next.candidates.isNotEmpty()) R.string.next_receipt_assign else R.string.next_receipt_show))
        }
    }
}

/** Waiting receipts named in the banner, the others are only counted */
private const val WAITING_SHOWN = 3
