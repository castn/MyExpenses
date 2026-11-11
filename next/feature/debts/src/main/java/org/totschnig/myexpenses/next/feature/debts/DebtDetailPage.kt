package org.totschnig.myexpenses.next.feature.debts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.FormatStyle
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LoadingBlock
import org.totschnig.myexpenses.designsystem.MenuButton
import org.totschnig.myexpenses.designsystem.MenuEntry
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.TopBarAction
import org.totschnig.myexpenses.designsystem.theme.LocalSemanticColors
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.data.debts.Debt
import org.totschnig.myexpenses.next.data.debts.DebtEntry
import timber.log.Timber

/**
 * A debt: what is left, its history with the balance after each payment, and editing, closing,
 * sharing and deleting it. A closed debt can only be reopened, shared or deleted.
 */
@Composable
internal fun DebtDetailPage(
    viewModel: DebtBookViewModel,
    debtId: Long,
    onBack: () -> Unit,
    onEdit: (Debt) -> Unit
) {
    val detail by remember(debtId) { viewModel.detail(debtId) }.collectAsStateWithLifecycle(null)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val shown = detail
    val debt = shown?.summary?.debt
    val context = LocalContext.current
    val shareText = shown?.let { shareText(it) }
    DetailScaffold(
        onBack = onBack,
        title = debt?.label,
        actions = {
            if (debt != null && !debt.isSealed) {
                TopBarAction(Icons.Outlined.Edit, stringResource(R.string.next_debt_edit)) {
                    onEdit(debt)
                }
            }
            if (debt != null) {
                val sealLabel = stringResource(
                    if (debt.isSealed) R.string.next_debt_reopen else R.string.next_debt_close
                )
                val sealIcon = if (debt.isSealed) Icons.Outlined.LockOpen else Icons.Outlined.Lock
                val shareLabel = stringResource(R.string.next_debt_share)
                val deleteLabel = stringResource(R.string.next_debt_delete)
                MenuButton(
                    entries = listOf(
                        MenuEntry(sealLabel, sealIcon) {
                            viewModel.setSealed(debt, !debt.isSealed)
                        },
                        MenuEntry(shareLabel, Icons.Outlined.Share) {
                            shareText?.let { context.share(debt.label, it) }
                        },
                        MenuEntry(deleteLabel, Icons.Outlined.Delete) { confirmDelete = true }
                    ),
                    contentDescription = stringResource(R.string.next_debt_actions)
                )
            }
        }
    ) {
        if (shown == null) {
            LoadingBlock()
            return@DetailScaffold
        }
        val summary = shown.summary
        val formatter = LocalAmountFormatter.current
        val colors = LocalSemanticColors.current
        DetailCard {
            InfoRow(
                debtTitle(summary),
                formatter.format(summary.balance.balance, summary.debt.currency),
                isFirst = true,
                valueColor = colors.amountColor(summary.balance.balance.compareTo(0))
            )
            summary.debt.description.takeIf { it.isNotBlank() }?.let { HintRow(it) }
            if (summary.debt.isSealed) {
                HintRow(stringResource(R.string.next_debt_closed), icon = Icons.Outlined.Lock)
            }
            if (summary.balance.unconverted > 0) {
                HintRow(stringResource(R.string.next_debt_unconverted), isWarning = true)
            }
        }
        SectionTitle(stringResource(R.string.next_debt_history))
        DetailCard {
            val lines = historyLines(summary.debt, shown.history)
            lines.forEachIndexed { index, line ->
                InfoRow(
                    line.label,
                    line.value,
                    isFirst = index == 0,
                    valueColor = colors.amountColor(line.balance.compareTo(0))
                )
            }
        }
    }
    if (confirmDelete && debt != null) {
        ConfirmDialog(
            title = stringResource(R.string.next_debt_delete),
            text = stringResource(R.string.next_debt_delete_text, debt.label),
            confirmLabel = stringResource(R.string.next_debt_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete(debt)
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/**
 * A line of the history as shown and shared
 *
 * @param label date and what happened, e.g. "12 Mar 2025 · +30.00 €"
 * @param value what is left after it
 */
internal data class HistoryLine(val label: String, val value: String, val balance: Long)

@Composable
private fun historyLines(debt: Debt, history: List<DebtEntry>): List<HistoryLine> {
    val formatter = LocalAmountFormatter.current
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)
    val start = stringResource(R.string.next_debt_start)
    val unconverted = stringResource(R.string.next_debt_unconverted_short)
    return history.map { entry ->
        val date = entry.date?.let(dateFormatter::format)
        val payment = entry.payment
        val what = when {
            entry.transactionId == null -> start

            payment == null -> unconverted

            // From the point of view of the balance: a payment received reduces what is owed to me
            else -> formatter.format(-payment, debt.currency)
        }
        HistoryLine(
            listOfNotNull(date, what).joinToString(" · "),
            formatter.format(entry.balance, debt.currency),
            entry.balance
        )
    }
}

/** The debt as text to share: label, who owes whom, note, then a line per entry of the history */
@Composable
private fun shareText(detail: DebtDetail): String {
    val debt = detail.summary.debt
    val lines = historyLines(debt, detail.history)
    return buildString {
        appendLine(debt.label)
        appendLine(debtTitle(detail.summary))
        debt.description.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine()
        lines.forEach { appendLine("${it.label} | ${it.value}") }
    }
}

private fun Context.share(subject: String, text: String) {
    try {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                subject
            )
        )
    } catch (e: ActivityNotFoundException) {
        Timber.w(e)
    }
}
