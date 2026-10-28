package org.totschnig.myexpenses.next.feature.tags

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import kotlin.math.absoluteValue
import org.totschnig.myexpenses.next.core.common.NO_NAME
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.data.tags.Refund

/**
 * Asks in the details of a debit or credit whether the other one of a suggested [Refund] paid it
 * back. Confirming marks both as reimbursed, so that they do not count as spending.
 */
@Composable
fun RefundSection(viewModel: TagDetectionsViewModel, transaction: ShownTransaction) {
    val refund = viewModel.refunds.collectAsStateWithLifecycle().value[transaction.id] ?: return
    val other = refund.other(transaction.id)
    val isDebit = transaction.id == refund.debit.id
    val dateFormatter = rememberDateFormatter()
    val amount = LocalAmountFormatter.current.format(other.amount.absoluteValue, transaction.currency)
    val who = other.payeeName?.takeIf { it.isNotBlank() } ?: other.comment?.takeIf { it.isNotBlank() } ?: NO_NAME
    val label = stringResource(R.string.next_tag_meaning_reimbursed)
    Banner(
        title = stringResource(if (isDebit) R.string.next_refund_question_debit else R.string.next_refund_question_credit),
        messages = listOf(
            stringResource(
                if (isDebit) R.string.next_refund_debit_text else R.string.next_refund_credit_text,
                amount, dateFormatter.format(other.date), who
            ),
            stringResource(R.string.next_refund_effect)
        )
    ) {
        TextButton(onClick = { viewModel.rejectRefund(refund) }) { Text(stringResource(R.string.next_refund_reject)) }
        TextButton(onClick = { viewModel.acceptRefund(refund, label) }) { Text(stringResource(R.string.next_refund_accept)) }
    }
}
