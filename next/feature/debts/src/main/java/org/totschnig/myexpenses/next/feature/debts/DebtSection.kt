package org.totschnig.myexpenses.next.feature.debts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.ActionChip
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.BannerStyle
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.PickDialog
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.core.ui.LocalAmountFormatter
import org.totschnig.myexpenses.next.data.debts.Debt
import org.totschnig.myexpenses.next.data.debts.DebtChoice
import org.totschnig.myexpenses.next.data.debts.DebtOutcome
import org.totschnig.myexpenses.next.data.debts.DebtStep

/** Splits, their parts and transfers are not linked to debts here */
private val ShownTransaction.canPayDebt get() = !isSplit && !isSplitPart && !isTransfer

@Composable
private fun rememberChoice(viewModel: DebtsViewModel, transaction: ShownTransaction): DebtChoice? {
    val choice by remember(transaction.id) { viewModel.choice(transaction.id) }
        .collectAsStateWithLifecycle(null)
    return choice
}

/**
 * The debt a transaction pays in its details: how much was owed before and after it, in words
 * what it changes, and unlinking it unless the debt is closed. Shown only for a linked
 * transaction, linking is offered by [DebtChip].
 */
@Composable
fun DebtSection(viewModel: DebtsViewModel, transaction: ShownTransaction) {
    if (!transaction.canPayDebt) return
    val linked = rememberChoice(viewModel, transaction) as? DebtChoice.Linked
    val isFailed = viewModel.failedFor.collectAsStateWithLifecycle().value == transaction.id
    if (linked == null && !isFailed) return
    SectionTitle(stringResource(R.string.next_debt_title))
    if (isFailed) {
        Banner(
            title = stringResource(R.string.next_debt_link_failed),
            messages = emptyList(),
            style = BannerStyle.Alert
        ) {
            TextButton(onClick = viewModel::dismissFailure) {
                Text(stringResource(R.string.next_receipt_close))
            }
        }
    }
    if (linked == null) return
    val debt = linked.debt
    val outcome = linked.outcome
    DetailCard {
        InfoRow(debt.label, outcome?.let { balanceChange(debt, it) } ?: "", isFirst = true)
        if (outcome == null) {
            HintRow(stringResource(R.string.next_debt_unconverted), isWarning = true)
        } else {
            HintRow(explanation(debt, outcome))
        }
        if (debt.isSealed) {
            HintRow(stringResource(R.string.next_debt_closed), icon = Icons.Outlined.Lock)
        } else {
            LinkRow(
                stringResource(R.string.next_debt_unlink),
                onClick = { viewModel.unlink(transaction.id) }
            )
        }
    }
}

/**
 * Chip for the [org.totschnig.myexpenses.designsystem.ActionChipRow] below the details of a
 * transaction without debt, if open debts of its payee fit: links it, after asking which debt if
 * there are several
 */
@Composable
fun DebtChip(viewModel: DebtsViewModel, transaction: ShownTransaction) {
    if (!transaction.canPayDebt) return
    val offered = rememberChoice(viewModel, transaction) as? DebtChoice.Offered ?: return
    var pick by rememberSaveable { mutableStateOf(false) }
    ActionChip(
        text = stringResource(R.string.next_debt_add),
        icon = Icons.Outlined.Handshake,
        onClick = {
            val single = offered.options.singleOrNull()
            if (single != null) viewModel.link(transaction.id, single.debt.id) else pick = true
        }
    )
    if (!pick) return
    PickDialog(
        title = stringResource(R.string.next_debt_pick_title),
        options = offered.options,
        label = { it.debt.label },
        supportingText = { option -> option.outcome?.let { explanation(option.debt, it) } },
        value = { option -> option.outcome?.let { balanceChange(option.debt, it) } },
        onSelect = {
            viewModel.link(transaction.id, it.debt.id)
            pick = false
        },
        onDismiss = { pick = false }
    )
}

/** E.g. "70,00 € → 40,00 €", positive while the payee owes me */
@Composable
private fun balanceChange(debt: Debt, outcome: DebtOutcome): String {
    val formatter = LocalAmountFormatter.current
    return "${formatter.format(outcome.before, debt.currency)} → " +
        formatter.format(outcome.after, debt.currency)
}

/** What the payment does and what is owed after it, e.g. "Alice pays you back 30 €. …" */
@Composable
private fun explanation(debt: Debt, outcome: DebtOutcome): String {
    val formatter = LocalAmountFormatter.current
    val payee = debt.payeeName
    val payment = formatter.format(kotlin.math.abs(outcome.payment), debt.currency)
    val after = formatter.format(kotlin.math.abs(outcome.after), debt.currency)
    val step = when (outcome.step) {
        DebtStep.Receive -> stringResource(R.string.next_debt_receive, payee, payment)
        DebtStep.BorrowMore -> stringResource(R.string.next_debt_borrow_more, payee, payment)
        DebtStep.Pay -> stringResource(R.string.next_debt_pay, payee, payment)
        DebtStep.LendMore -> stringResource(R.string.next_debt_lend_more, payee, payment)
        null -> null
    }
    val result = when {
        outcome.after > 0 -> stringResource(R.string.next_debt_they_owe, payee, after)
        outcome.after < 0 -> stringResource(R.string.next_debt_you_owe, payee, after)
        else -> stringResource(R.string.next_debt_settled)
    }
    return listOfNotNull(step, result).joinToString(" ")
}
