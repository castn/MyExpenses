package org.totschnig.myexpenses.next.feature.categories

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.InfoRow
import org.totschnig.myexpenses.designsystem.LabelChip
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.next.core.ui.LabelIcons
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.feature.categories.CategorySuggestionsViewModel.CategoryState
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.categories.counterpart
import org.totschnig.myexpenses.next.data.categories.SuggestionSource

/**
 * Opens the category selection of the app, filtered to the direction of [transaction]
 *
 * @param onPicked with id and path of the category chosen
 */
@Composable
private fun rememberCategoryPicker(transaction: ShownTransaction, onPicked: (Long, String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(LocalCategoryPicker.current) { picked ->
        picked?.let { (id, path) -> onPicked(id, path) }
    }
    return { launcher.launch(transaction.amount > 0) }
}

/** Splits and transfers keep their categories in the form, they get no chip and no suggestions */
fun ShownTransaction.hasCategoryChoice() = !isSplit && !isTransfer && !isSplitPart

/**
 * The category of a transaction below the title of its details, like the category of a contract.
 * Tapping it chooses another one, without category it invites to set one.
 */
@Composable
fun CategoryChip(viewModel: CategorySuggestionsViewModel, transaction: ShownTransaction, modifier: Modifier = Modifier) {
    val pick = rememberCategoryPicker(transaction) { categoryId, path ->
        viewModel.change(transaction.id, transaction.categoryId, categoryId)
        viewModel.markApplied(transaction.id, categoryId, path)
    }
    LabelChip(
        text = transaction.categoryPath,
        placeholder = stringResource(R.string.next_category_set),
        onClick = pick,
        modifier = modifier,
        icon = LabelIcons.category
    )
}

/**
 * Part of the details of a transaction: the suggested category with the choice to take it, the
 * note that the category was applied without asking, or after a category was given, the offer to
 * give it to the other transactions of the counterpart.
 */
@Composable
fun CategorySuggestionSection(viewModel: CategorySuggestionsViewModel, transaction: ShownTransaction) {
    if (!transaction.hasCategoryChoice()) return
    val state by remember(transaction.id) { viewModel.stateOf(transaction.id, transaction.categoryId) }
        .collectAsStateWithLifecycle(CategoryState.None)
    /** Payee, or the merchant for a payment through PayPal and alike */
    val counterpartInfo by remember(transaction.id) { viewModel.counterpartOf(transaction.id) }
        .collectAsStateWithLifecycle(null)
    val counterpart = counterpartInfo?.counterpart?.takeIf { it.name.isNotBlank() }
    val justApplied by viewModel.justApplied.collectAsStateWithLifecycle()
    DisposableEffect(transaction.id) {
        onDispose { viewModel.clearApplied(transaction.id) }
    }

    justApplied[transaction.id]?.let { (categoryId, path) ->
        FollowUp(
            path = path,
            counterpartName = counterpart?.name,
            others = counterpartInfo?.uncategorized.orEmpty(),
            onApplyToOthers = { viewModel.accept(it, categoryId) },
            onAddRule = { counterpart?.let { viewModel.addRule(it, categoryId) } }
        )
        return
    }

    when (val current = state) {
        CategoryState.None -> {}
        is CategoryState.Suggested -> Banner(
            title = stringResource(R.string.next_category_suggested, current.path),
            messages = listOf(current.suggestion.source.reason(counterpart?.name))
        ) {
            TextButton(onClick = { viewModel.reject(current.suggestion) }) {
                Text(stringResource(R.string.next_reject))
            }
            Button(
                onClick = {
                    viewModel.accept(listOf(transaction.id), current.suggestion.categoryId)
                    viewModel.markApplied(transaction.id, current.suggestion.categoryId, current.path)
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.next_category_accept))
            }
        }

        // Another category is chosen with the chip
        is CategoryState.Automatic -> {
            SectionTitle(stringResource(R.string.next_contracts_transaction_category))
            DetailCard {
                InfoRow(stringResource(R.string.next_category_automatic), current.path, isFirst = true)
                LinkRow(
                    stringResource(R.string.next_category_undo),
                    onClick = { viewModel.undoAutomatic(transaction.id, current.categoryId) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionSource.reason(counterpartName: String?): String = when (this) {
    SuggestionSource.RULE -> stringResource(R.string.next_category_reason_rule)
    SuggestionSource.PAYEE -> counterpartName?.let { stringResource(R.string.next_category_reason_payee, it) }
        ?: stringResource(R.string.next_category_reason_words)
    SuggestionSource.WORDS -> stringResource(R.string.next_category_reason_words)
    SuggestionSource.MERCHANT -> stringResource(R.string.next_category_reason_merchant)
}

/**
 * After a category was taken: offers it for the other transactions of the payee without category,
 * and for the future. Stays until the details are closed.
 *
 * @param counterpartName payee or merchant, null if there is none, then there is nothing to offer
 */
@Composable
private fun FollowUp(
    path: String,
    counterpartName: String?,
    others: List<Long>,
    onApplyToOthers: (List<Long>) -> Unit,
    onAddRule: () -> Unit,
) {
    var appliedCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var ruleAdded by rememberSaveable { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.next_contracts_transaction_category))
    DetailCard {
        InfoRow(stringResource(R.string.next_category_taken), path, isFirst = true)
        val name = counterpartName ?: return@DetailCard
        val applied = appliedCount
        if (applied != null) {
            InfoRow(stringResource(R.string.next_category_applied_others, name), applied.toString())
        } else if (others.isNotEmpty()) {
            LinkRow(
                pluralStringResource(R.plurals.next_category_apply_others, others.size, others.size, name),
                onClick = {
                    onApplyToOthers(others)
                    appliedCount = others.size
                }
            )
        }
        if (ruleAdded) {
            InfoRow(stringResource(R.string.next_category_rule_added, name), path)
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

/**
 * Asks once whether the certain suggestions for transactions from before shall be applied
 */
@Composable
fun CategoryBacklogBanner(viewModel: CategorySuggestionsViewModel, modifier: Modifier = Modifier) {
    val backlog by viewModel.backlog.collectAsStateWithLifecycle()
    val current = backlog ?: return
    Banner(
        title = stringResource(R.string.next_category_backlog_title),
        messages = listOf(pluralStringResource(R.plurals.next_category_backlog_text, current.uncategorized, current.certain, current.uncategorized)),
        modifier = modifier
    ) {
        TextButton(onClick = viewModel::declineBacklog) {
            Text(stringResource(R.string.next_reject))
        }
        Button(onClick = viewModel::applyBacklog, modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(R.string.next_category_accept))
        }
    }
}
