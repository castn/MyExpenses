package org.totschnig.myexpenses.next.feature.portfolio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.format.FormatStyle
import org.totschnig.myexpenses.designsystem.ActionChip
import org.totschnig.myexpenses.designsystem.ActionChipRow
import org.totschnig.myexpenses.designsystem.ChipRow
import org.totschnig.myexpenses.designsystem.ChoiceChips
import org.totschnig.myexpenses.designsystem.DateDialog
import org.totschnig.myexpenses.designsystem.FormPickerField
import org.totschnig.myexpenses.designsystem.FormTextField
import org.totschnig.myexpenses.designsystem.KeyFigure
import org.totschnig.myexpenses.designsystem.KeyFigures
import org.totschnig.myexpenses.designsystem.LabelChip
import org.totschnig.myexpenses.designsystem.SingleChoiceDialog
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.TimeDialog
import org.totschnig.myexpenses.designsystem.ToggleChip
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.LabelIcons
import org.totschnig.myexpenses.next.core.ui.categories.LocalCategoryPicker
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.core.ui.rememberTimeFormatter
import org.totschnig.myexpenses.next.data.portfolio.Asset
import org.totschnig.myexpenses.next.data.portfolio.CostCategory
import org.totschnig.myexpenses.next.data.portfolio.Funding
import org.totschnig.myexpenses.next.data.portfolio.Portfolio
import org.totschnig.myexpenses.next.data.portfolio.TradeDraft
import org.totschnig.myexpenses.next.data.portfolio.TradeFigures
import org.totschnig.myexpenses.next.data.portfolio.TradeKind
import org.totschnig.myexpenses.next.data.portfolio.isKind
import org.totschnig.myexpenses.next.data.portfolio.kindChoices
import org.totschnig.myexpenses.next.data.portfolio.parseDecimalText
import org.totschnig.myexpenses.next.data.portfolio.roundingChoices
import org.totschnig.myexpenses.next.data.portfolio.withCost
import org.totschnig.myexpenses.next.data.portfolio.withNewCost
import org.totschnig.myexpenses.next.data.portfolio.withoutCost

/**
 * All fields of a trade: its kind, day and time, the asset with quantity and price or the amount of
 * a cash movement, the costs, where the money comes from or goes to with a matching transaction of
 * that account, and a note. Saving is left to the screen around it.
 */
@Composable
internal fun TradeForm(
    state: TradeEditState,
    draft: TradeDraft,
    portfolio: Portfolio,
    actions: TradeFormActions,
    modifier: Modifier = Modifier
) {
    var dialog by rememberSaveable { mutableStateOf<FormDialogKind?>(null) }
    // The cost whose category is picked
    var costPicked by rememberSaveable { mutableStateOf<Int?>(null) }
    val pickCategory = rememberLauncherForActivityResult(LocalCategoryPicker.current) { picked ->
        val index = costPicked
        costPicked = null
        if (picked != null && index != null) {
            draft.costs.getOrNull(index)?.let { cost ->
                val category = CostCategory(picked.first, picked.second)
                actions.changeDraft(draft.withCost(index, cost.copy(category = category)))
            }
        }
    }
    val formatAmount = rememberAmountFormat(portfolio.currency)
    val currencySymbol = portfolio.currency.symbol
    val figures = state.figures
    Column(
        modifier.padding(horizontal = Spacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val choices = kindChoices(state.original?.kind)
        if (choices.size > 1) {
            ChoiceChips(
                options = choices,
                selected = choices.find { draft.isKind(it) },
                label = { stringResource(it.label) },
                onSelect = actions::chooseKind
            )
        }
        if (draft.kind.isTransfer) {
            if (draft.isNew) {
                ChoiceChips(
                    options = listOf(TradeKind.TRANSFER_OUT, TradeKind.TRANSFER_IN),
                    selected = draft.kind,
                    label = { stringResource(it.directionLabel) },
                    onSelect = { if (it != draft.kind) actions.invertTransfer() }
                )
            }
            FormPickerField(
                label = stringResource(R.string.next_trade_other_portfolio),
                value = state.accounts.otherPortfolios.find { it.id == draft.peerAccountId }?.label
                    ?: stringResource(R.string.next_trade_choose),
                onClick = { dialog = FormDialogKind.OTHER_PORTFOLIO },
                enabled = draft.isNew
            )
        }
        DateTimeFields(draft, onDate = { dialog = FormDialogKind.DATE }, onTime = {
            dialog =
                FormDialogKind.TIME
        })
        if (draft.kind.movesAsset) {
            FormPickerField(
                label = stringResource(R.string.next_trade_asset),
                value =
                    state.asset?.let { "${it.label} (${it.code})" }
                        ?: stringResource(R.string.next_trade_choose),
                onClick = { dialog = FormDialogKind.ASSET }
            )
        }
        QuantityField(state, draft, portfolio, actions)
        if (draft.kind.movesAsset) {
            val isInvalid =
                draft.priceText.isNotBlank() && parseDecimalText(draft.priceText) == null
            FormTextField(
                label = stringResource(R.string.next_trade_price),
                value = draft.priceText,
                onValueChange = { actions.changeDraft(draft.copy(priceText = it)) },
                keyboardType = KeyboardType.Decimal,
                isError = isInvalid,
                supportingText = if (isInvalid) {
                    stringResource(
                        R.string.next_trade_invalid
                    )
                } else {
                    null
                },
                trailing = { Text(currencySymbol) }
            )
        }
        if (!draft.kind.isTransfer) {
            Costs(draft, currencySymbol, actions) { index ->
                costPicked = index
                pickCategory.launch(false)
            }
        }
        if (figures != null) Figures(figures, formatAmount)
        if (figures?.isRoundingNecessary == true) {
            FormPickerField(
                label = stringResource(R.string.next_trade_rounding),
                value = stringResource(portfolio.rounding.label),
                onClick = { dialog = FormDialogKind.ROUNDING }
            )
        }
        if (!draft.kind.isTransfer) {
            val fundingAccount = state.accounts.funding.find { it.id == draft.peerAccountId }
            FormPickerField(
                label = stringResource(R.string.next_trade_funding),
                value = when (draft.funding) {
                    Funding.PORTFOLIO -> stringResource(R.string.next_trade_funding_portfolio)
                    Funding.EXTERNAL -> stringResource(R.string.next_trade_funding_external)
                    Funding.ACCOUNT -> fundingAccount?.label.orEmpty()
                },
                onClick = { dialog = FormDialogKind.FUNDING }
            )
            Matches(state, draft, actions)
        }
        FormTextField(
            label = stringResource(R.string.next_trade_comment),
            value = draft.comment,
            onValueChange = { actions.changeDraft(draft.copy(comment = it)) },
            singleLine = false
        )
    }
    FormDialogs(dialog, state, draft, portfolio, actions) { dialog = it }
}

/** The dialogs the fields of the form open */
private enum class FormDialogKind {
    DATE,
    TIME,
    ASSET,
    NEW_ASSET,
    ROUNDING,
    FUNDING,
    OTHER_PORTFOLIO
}

@Composable
private fun DateTimeFields(draft: TradeDraft, onDate: () -> Unit, onTime: () -> Unit) {
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)
    val timeFormatter = rememberTimeFormatter()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FormPickerField(
            label = stringResource(R.string.next_trade_date),
            value = dateFormatter.format(draft.date),
            onClick = onDate,
            modifier = Modifier.weight(1f)
        )
        FormPickerField(
            label = stringResource(R.string.next_trade_time),
            value = timeFormatter.format(draft.date),
            onClick = onTime,
            modifier = Modifier.weight(1f)
        )
    }
}

/** The quantity of the asset or the amount of cash, with what is available to move out */
@Composable
private fun QuantityField(
    state: TradeEditState,
    draft: TradeDraft,
    portfolio: Portfolio,
    actions: TradeFormActions
) {
    val available = state.figures?.available
    val availableText = available?.let { rememberAmountFormat(it.currency)(it.amount).trim() }
    val isInvalid = draft.quantityText.isNotBlank() && parseDecimalText(draft.quantityText) == null
    val isOverLimit = state.figures?.isOverLimit == true
    val unit = if (draft.kind.movesAsset) state.asset?.code.orEmpty() else portfolio.currency.symbol
    FormTextField(
        label = stringResource(
            if (draft.kind.movesAsset) R.string.next_trade_quantity else R.string.next_trade_amount
        ),
        value = draft.quantityText,
        onValueChange = { actions.changeDraft(draft.copy(quantityText = it)) },
        keyboardType = KeyboardType.Decimal,
        enabled = !draft.kind.movesAsset || draft.assetCode != null,
        isError = isInvalid || isOverLimit,
        supportingText = when {
            isInvalid -> stringResource(R.string.next_trade_invalid)
            availableText == null -> null
            isOverLimit -> stringResource(R.string.next_trade_over_limit, availableText)
            else -> stringResource(R.string.next_trade_available, availableText)
        },
        trailing = { Text(unit) }
    )
}

/** The costs of a trade, each with its amount and category, and a chip for one more */
@Composable
private fun Costs(
    draft: TradeDraft,
    currencySymbol: String,
    actions: TradeFormActions,
    onPickCategory: (Int) -> Unit
) {
    draft.costs.forEachIndexed { index, cost ->
        val isInvalid = cost.amountText.isNotBlank() && parseDecimalText(cost.amountText) == null
        FormTextField(
            label = cost.category?.path ?: stringResource(R.string.next_trade_costs),
            value = cost.amountText,
            onValueChange = { text ->
                actions.changeDraft(draft.withCost(index, cost.copy(amountText = text)))
            },
            keyboardType = KeyboardType.Decimal,
            isError = isInvalid,
            supportingText = if (isInvalid) stringResource(R.string.next_trade_invalid) else null,
            trailing = { Text(currencySymbol) }
        )
        ActionChipRow {
            LabelChip(
                text = cost.category?.path,
                placeholder = stringResource(R.string.next_trade_cost_category),
                onClick = { onPickCategory(index) },
                icon = LabelIcons.category
            )
            ActionChip(
                text = stringResource(R.string.next_trade_cost_remove),
                icon = Icons.Outlined.Delete,
                onClick = { actions.changeDraft(draft.withoutCost(index)) }
            )
        }
    }
    ActionChipRow {
        ActionChip(
            text = stringResource(R.string.next_trade_cost_add),
            icon = Icons.Outlined.Add,
            onClick = { actions.changeDraft(draft.withNewCost()) }
        )
    }
}

/** The transactions of the funding account the trade may be linked to */
@Composable
private fun Matches(state: TradeEditState, draft: TradeDraft, actions: TradeFormActions) {
    if (draft.funding != Funding.ACCOUNT || state.matches.isEmpty()) return
    val portfolio = state.portfolio ?: return
    val formatAmount = rememberAmountFormat(portfolio.currency)
    ChipRow(
        stringResource(R.string.next_trade_match),
        hint = stringResource(R.string.next_trade_match_hint)
    ) {
        state.matches.forEach { match ->
            val isLinked = draft.linkedTransactionId == match.id
            ToggleChip(
                text = listOfNotNull(match.label, formatAmount(match.amount)).joinToString(" · "),
                selected = isLinked,
                onClick = { actions.link(if (isLinked) null else match.id) }
            )
        }
    }
}

@Composable
private fun FormDialogs(
    dialog: FormDialogKind?,
    state: TradeEditState,
    draft: TradeDraft,
    portfolio: Portfolio,
    actions: TradeFormActions,
    onDialog: (FormDialogKind?) -> Unit
) {
    val dismiss = { onDialog(null) }
    when (dialog) {
        FormDialogKind.DATE -> DateDialog(
            initial = draft.date.toLocalDate(),
            onConfirm = {
                actions.changeDraft(draft.copy(date = draft.date.with(it)))
                dismiss()
            },
            onDismiss = dismiss
        )

        FormDialogKind.TIME -> TimeDialog(
            title = stringResource(R.string.next_trade_time),
            hour = draft.date.hour,
            minute = draft.date.minute,
            onConfirm = { hour, minute ->
                actions.changeDraft(draft.copy(date = draft.date.withHour(hour).withMinute(minute)))
                dismiss()
            },
            onDismiss = dismiss
        )

        FormDialogKind.ASSET -> AssetChoiceDialog(state, portfolio, actions, onNew = {
            onDialog(FormDialogKind.NEW_ASSET)
        }, onDismiss = dismiss)

        FormDialogKind.NEW_ASSET -> AssetDialog(
            onConfirm = {
                actions.createAsset(it)
                dismiss()
            },
            onDismiss = dismiss
        )

        FormDialogKind.ROUNDING -> SingleChoiceDialog(
            title = stringResource(R.string.next_trade_rounding),
            options = roundingChoices,
            selected = portfolio.rounding,
            label = { stringResource(it.label) },
            onSelect = {
                actions.chooseRounding(it)
                dismiss()
            },
            onDismiss = dismiss
        )

        FormDialogKind.FUNDING -> FundingDialog(state, draft, portfolio, actions, dismiss)

        FormDialogKind.OTHER_PORTFOLIO -> SingleChoiceDialog(
            title = stringResource(R.string.next_trade_other_portfolio),
            options = state.accounts.otherPortfolios,
            selected = state.accounts.otherPortfolios.find { it.id == draft.peerAccountId },
            label = { it.label },
            onSelect = {
                actions.changeDraft(draft.copy(peerAccountId = it.id))
                dismiss()
            },
            onDismiss = dismiss
        )

        null -> Unit
    }
}

/** The assets held with their quantity, then the others known, and a new one */
@Composable
private fun AssetChoiceDialog(
    state: TradeEditState,
    portfolio: Portfolio,
    actions: TradeFormActions,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val held = portfolio.holdings.associateBy { it.asset.code }
    val options: List<Asset> = portfolio.holdings.map { it.asset } + state.assets
    SingleChoiceDialog(
        title = stringResource(R.string.next_trade_asset),
        options = options,
        selected = state.asset,
        label = { "${it.label} (${it.code})" },
        onSelect = {
            actions.chooseAsset(it.code)
            onDismiss()
        },
        onDismiss = onDismiss,
        trailing = { asset ->
            held[asset.code]?.let { holding ->
                Text(rememberAmountFormat(asset.quantityFormat)(holding.balance).trim())
            }
        },
        footer = {
            TextButton(onClick = onNew) { Text(stringResource(R.string.next_trade_asset_new)) }
        }
    )
}

/** Where the money comes from or goes to: the cash of the portfolio for an asset, outside the app or an account */
@Composable
private fun FundingDialog(
    state: TradeEditState,
    draft: TradeDraft,
    portfolio: Portfolio,
    actions: TradeFormActions,
    onDismiss: () -> Unit
) {
    val options = buildList {
        if (draft.kind.movesAsset) add(Funding.PORTFOLIO to null)
        add(Funding.EXTERNAL to null)
        state.accounts.funding.forEach { add(Funding.ACCOUNT to it) }
    }
    val formatAmount = rememberAmountFormat(portfolio.currency)
    SingleChoiceDialog(
        title = stringResource(R.string.next_trade_funding),
        options = options,
        selected = options.find { (funding, account) ->
            funding == draft.funding &&
                (funding != Funding.ACCOUNT || account?.id == draft.peerAccountId)
        },
        label = { (funding, account) ->
            when (funding) {
                Funding.PORTFOLIO -> stringResource(R.string.next_trade_funding_portfolio)
                Funding.EXTERNAL -> stringResource(R.string.next_trade_funding_external)
                Funding.ACCOUNT -> account?.label.orEmpty()
            }
        },
        onSelect = { (funding, account) ->
            actions.chooseFunding(funding, account?.id)
            onDismiss()
        },
        onDismiss = onDismiss,
        trailing = { (funding, _) ->
            if (funding == Funding.PORTFOLIO) Text(formatAmount(portfolio.cashBalance))
        }
    )
}

/** Value, costs and total of the trade, once the quantity can be read */
@Composable
private fun Figures(figures: TradeFigures, formatAmount: (Long) -> String) {
    val principal = figures.principal ?: return
    val principalLabel = stringResource(R.string.next_trade_principal)
    val costsLabel = stringResource(R.string.next_trade_costs)
    val totalLabel = stringResource(R.string.next_trade_total)
    KeyFigures(
        listOfNotNull(
            KeyFigure(principalLabel, formatAmount(principal)),
            KeyFigure(costsLabel, formatAmount(figures.costs)).takeIf { figures.costs != 0L },
            figures.total?.let { KeyFigure(totalLabel, formatAmount(it)) }
        )
    )
}
