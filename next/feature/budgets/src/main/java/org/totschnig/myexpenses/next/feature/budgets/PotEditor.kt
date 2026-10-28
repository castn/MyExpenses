package org.totschnig.myexpenses.next.feature.budgets

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.DateDialog
import org.totschnig.myexpenses.designsystem.DestructiveTextButton
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.DetailScaffold
import org.totschnig.myexpenses.designsystem.EditableRow
import org.totschnig.myexpenses.designsystem.HintRow
import org.totschnig.myexpenses.designsystem.IconNavigationRow
import org.totschnig.myexpenses.designsystem.LinkRow
import org.totschnig.myexpenses.designsystem.MultiChoiceSheet
import org.totschnig.myexpenses.designsystem.NameDialog
import org.totschnig.myexpenses.designsystem.SectionTitle
import org.totschnig.myexpenses.designsystem.SingleChoiceDialog
import org.totschnig.myexpenses.designsystem.SwitchRow
import org.totschnig.myexpenses.designsystem.TextDialog
import org.totschnig.myexpenses.designsystem.TopBarAction
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.ui.NextPreviewTheme
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.icons.CatalogIcon
import org.totschnig.myexpenses.next.core.ui.icons.CatalogIconPicker
import org.totschnig.myexpenses.next.core.common.icons.IconCatalog
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup
import org.totschnig.myexpenses.next.core.ui.receipts.labelRes
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.core.ui.rememberDateFormatter
import org.totschnig.myexpenses.next.data.budgets.Pot
import org.totschnig.myexpenses.next.data.budgets.PotSuggestion
import org.totschnig.myexpenses.next.data.budgets.PotSuggestions
import org.totschnig.myexpenses.next.data.budgets.PotTemplate
import org.totschnig.myexpenses.next.data.budgets.PotKind

/**
 * The templates to start a new pot from, grouped by kind, and a pot of one's own
 *
 * @param categories of the user, to show which a template would hold
 * @param kinds of pots that can be chosen, see [PotEditorData.kinds]
 */
@Composable
fun PotTemplateScreen(
    categories: List<CategoryInfo>,
    onPick: (PotTemplate?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    kinds: List<PotKind> = PotKind.entries,
) {
    val byId = categories.associateBy { it.id }
    DetailScaffold(onBack = onBack, title = stringResource(R.string.next_budget_add_pot), modifier = modifier) {
        listOf(
            R.string.next_budget_kind_running to listOf(PotKind.RUNNING),
            R.string.next_budget_kind_yearly to listOf(PotKind.YEARLY),
            R.string.next_budget_templates_purpose to listOf(PotKind.GOAL, PotKind.PROJECT),
            R.string.next_budget_templates_receipts to listOf(PotKind.ITEM_GROUP),
        ).forEach { (title, sectionKinds) ->
            val shown = sectionKinds.filter { it in kinds }
            if (shown.isEmpty()) return@forEach
            SectionTitle(stringResource(title))
            DetailCard {
                PotTemplate.entries.filter { it.kind in shown }.forEach { template ->
                    val found = template.categoriesIn(categories).mapNotNull { byId[it]?.label }
                    IconNavigationRow(
                        title = stringResource(template.nameRes),
                        supportingText = found.joinToString().ifEmpty {
                            stringResource(
                                when (template.kind) {
                                    PotKind.GOAL -> R.string.next_pot_goal_hint
                                    PotKind.PROJECT -> R.string.next_pot_project_hint
                                    PotKind.ITEM_GROUP -> R.string.next_pot_item_group_hint
                                    else -> R.string.next_pot_no_categories
                                }
                            )
                        },
                        onClick = { onPick(template) }
                    ) { CatalogIcon(template.icon) }
                }
            }
        }
        DetailCard(Modifier.padding(top = 12.dp)) {
            IconNavigationRow(
                icon = Icons.Outlined.Add,
                title = stringResource(R.string.next_budget_custom),
                onClick = { onPick(null) }
            )
        }
    }
}

/** A dialog of the editor */
private enum class EditorDialog { Name, Icon, Kind, Amount, Categories, Tags, Contracts, Savings, Start, End, ItemGroups, MaxShare, Delete }

/**
 * Creates or changes a pot. Changes are kept in [pot] by the caller and stored only by [onSave].
 *
 * @param onDelete null for a new pot
 */
@Composable
fun PotEditorScreen(
    pot: Pot,
    data: PotEditorData,
    currency: CurrencyInfo,
    onChange: (Pot) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val amount = rememberAmountFormat(currency)
    val dates = rememberDateFormatter()
    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }
    val none = stringResource(R.string.next_budget_none)
    val suggestion by produceState<PotSuggestion?>(null, data.input, pot.kind, pot.categoryIds, pot.tagIds, pot.payeeIds) {
        value = withContext(Dispatchers.Default) { PotSuggestions.suggest(data.input, pot) }
    }
    val canSave = pot.name.isNotBlank() && when (pot.kind) {
        PotKind.GOAL -> true
        PotKind.ITEM_GROUP -> pot.itemGroups.isNotEmpty() && pot.maxSharePercent != null
        else -> pot.holdsTransactions
    }

    DetailScaffold(
        onBack = onBack,
        title = stringResource(if (onDelete == null) R.string.next_budget_add_pot else R.string.next_budget_edit_pot),
        modifier = modifier,
        actions = {
            if (canSave) TopBarAction(Icons.Default.Check, stringResource(R.string.next_budget_save), onSave)
        }
    ) {
        DetailCard {
            EditableRow(stringResource(R.string.next_budget_name), pot.name.ifBlank { none }, { dialog = EditorDialog.Name }, isFirst = true)
            EditableRow(stringResource(R.string.next_budget_icon), { dialog = EditorDialog.Icon }) { PotIconTile(pot) }
            EditableRow(stringResource(R.string.next_budget_kind), stringResource(pot.kind.labelRes), { dialog = EditorDialog.Kind })
            if (pot.kind == PotKind.ITEM_GROUP) {
                val groups = ItemGroup.entries.filter { it.id in pot.itemGroups }.map { stringResource(it.labelRes) }
                EditableRow(stringResource(R.string.next_budget_item_groups), groups.joinToString().ifEmpty { none }, { dialog = EditorDialog.ItemGroups })
                EditableRow(
                    stringResource(R.string.next_budget_max_share_label),
                    pot.maxSharePercent?.let { "$it %" } ?: stringResource(R.string.next_budget_not_set),
                    { dialog = EditorDialog.MaxShare }
                )
                HintRow(stringResource(R.string.next_pot_item_group_explained))
            } else {
                EditableRow(
                    stringResource(pot.kind.amountLabelRes),
                    if (pot.amount > 0) amount(pot.amount) else stringResource(R.string.next_budget_not_set),
                    { dialog = EditorDialog.Amount }
                )
            }
            suggestion?.amount?.takeIf { it != pot.amount && (pot.kind == PotKind.RUNNING || pot.kind == PotKind.YEARLY) }?.let {
                LinkRow(stringResource(R.string.next_budget_suggestion, amount(it)), onClick = { onChange(pot.copy(amount = it)) })
                HintRow(
                    if (pot.kind == PotKind.YEARLY) stringResource(R.string.next_budget_suggestion_yearly)
                    else pluralStringResource(R.plurals.next_budget_suggestion_running, suggestion!!.periods, suggestion!!.periods)
                )
            }
        }

        if (pot.kind != PotKind.ITEM_GROUP) {
            SectionTitle(stringResource(R.string.next_budget_holds))
            DetailCard {
                val categoryNames = data.categories.filter { it.id in pot.categoryIds }.map { it.label }
                EditableRow(stringResource(R.string.next_budget_categories), categoryNames.joinToString().ifEmpty { none }, { dialog = EditorDialog.Categories }, isFirst = true)
                val tagNames = data.tags.filter { it.id in pot.tagIds }.map { it.label }
                EditableRow(stringResource(R.string.next_budget_groups), tagNames.joinToString().ifEmpty { none }, { dialog = EditorDialog.Tags })
                val contractNames = data.contracts.filter { it.rule?.id in pot.contractRuleIds }.map { it.title }
                EditableRow(stringResource(R.string.next_budget_contracts_label), contractNames.joinToString().ifEmpty { none }, { dialog = EditorDialog.Contracts })
                suggestion?.contracts?.filter { it.rule!!.id !in pot.contractRuleIds }?.forEach { contract ->
                    LinkRow(
                        stringResource(R.string.next_budget_add_contract, contract.title, amount(contract.monthlyAmount)),
                        onClick = { onChange(pot.copy(contractRuleIds = pot.contractRuleIds + contract.rule!!.id)) }
                    )
                }
                if (!pot.holdsTransactions && pot.kind != PotKind.GOAL) {
                    HintRow(stringResource(R.string.next_budget_needs_content))
                }
            }
        }

        when (pot.kind) {
            PotKind.RUNNING -> DetailCard(Modifier.padding(top = 12.dp)) {
                SwitchRow(
                    label = stringResource(R.string.next_budget_rollover),
                    checked = pot.rollover,
                    onCheckedChange = { onChange(pot.copy(rollover = it)) },
                    supportingText = stringResource(R.string.next_budget_rollover_hint),
                    isFirst = true
                )
            }

            PotKind.GOAL -> DetailCard(Modifier.padding(top = 12.dp)) {
                EditableRow(
                    stringResource(R.string.next_budget_goal_date),
                    pot.end?.let(dates::format) ?: stringResource(R.string.next_budget_no_date),
                    { dialog = EditorDialog.End },
                    isFirst = true
                )
                val savedWith = data.reserves.filter { it.rule?.id in pot.reserveRuleIds }.map { it.title } +
                        pot.savingAccountIds.mapNotNull { data.savingAccounts[it] }
                EditableRow(stringResource(R.string.next_budget_saved_with), savedWith.joinToString().ifEmpty { none }, { dialog = EditorDialog.Savings })
            }

            PotKind.PROJECT -> DetailCard(Modifier.padding(top = 12.dp)) {
                EditableRow(
                    stringResource(R.string.next_budget_project_start),
                    pot.start?.let(dates::format) ?: stringResource(R.string.next_budget_no_date),
                    { dialog = EditorDialog.Start },
                    isFirst = true
                )
                EditableRow(
                    stringResource(R.string.next_budget_project_end),
                    pot.end?.let(dates::format) ?: stringResource(R.string.next_budget_no_date),
                    { dialog = EditorDialog.End }
                )
            }

            else -> {}
        }

        if (onDelete != null) {
            Row(Modifier.padding(top = 12.dp)) {
                DestructiveTextButton(stringResource(R.string.next_budget_delete), { dialog = EditorDialog.Delete }, icon = Icons.Outlined.Delete)
            }
        }
    }

    val close = { dialog = null }
    when (dialog) {
        EditorDialog.Name -> NameDialog(
            title = stringResource(R.string.next_budget_name),
            initialName = pot.name,
            onConfirm = { onChange(pot.copy(name = it.trim())); close() },
            onDismiss = close
        )

        EditorDialog.Icon -> CatalogIconPicker(
            title = stringResource(R.string.next_icons_title, pot.name.ifBlank { none }),
            selected = pot.icon,
            suggestions = IconCatalog.suggest(
                pot.name, *data.categories.filter { it.id in pot.categoryIds }.map { it.label }.toTypedArray()
            ),
            onSelect = { onChange(pot.copy(icon = it)); close() },
            onDismiss = close,
            automatic = { PotIconTile(pot.copy(icon = null)) }
        )

        EditorDialog.Kind -> SingleChoiceDialog(
            title = stringResource(R.string.next_budget_kind),
            options = data.kinds,
            selected = pot.kind,
            label = { stringResource(it.labelRes) },
            onSelect = { onChange(pot.copy(kind = it)); close() },
            onDismiss = close
        )

        EditorDialog.Amount -> {
            val invalid = stringResource(R.string.next_budget_amount_invalid)
            TextDialog(
                title = stringResource(pot.kind.amountLabelRes),
                initialText = if (pot.amount > 0) BigDecimal.valueOf(pot.amount, currency.fractionDigits).toPlainString() else "",
                onConfirm = { text -> parseAmount(text, currency)?.let { onChange(pot.copy(amount = it)) }; close() },
                onDismiss = close,
                keyboardType = KeyboardType.Decimal,
                error = { text -> invalid.takeIf { text.isNotBlank() && parseAmount(text, currency) == null } }
            )
        }

        EditorDialog.Categories -> IdsSheet(
            title = stringResource(R.string.next_budget_categories),
            options = data.categories.map { it.id },
            initial = pot.categoryIds,
            label = { id -> data.categories.first { it.id == id }.path },
            onConfirm = { onChange(pot.copy(categoryIds = it)); close() },
            onDismiss = close
        )

        EditorDialog.Tags -> IdsSheet(
            title = stringResource(R.string.next_budget_groups),
            options = data.tags.map { it.id },
            initial = pot.tagIds,
            label = { id -> data.tags.first { it.id == id }.label },
            onConfirm = { onChange(pot.copy(tagIds = it)); close() },
            onDismiss = close
        )

        EditorDialog.Contracts -> IdsSheet(
            title = stringResource(R.string.next_budget_contracts_label),
            options = data.contracts.map { it.rule!!.id },
            initial = pot.contractRuleIds,
            label = { id -> data.contracts.first { it.rule!!.id == id }.title },
            supportingText = { id -> amount(data.contracts.first { it.rule!!.id == id }.monthlyAmount) },
            onConfirm = { onChange(pot.copy(contractRuleIds = it)); close() },
            onDismiss = close
        )

        EditorDialog.Savings -> IdsSheet(
            title = stringResource(R.string.next_budget_saved_with),
            options = data.reserves.map { "r:" + it.rule!!.id } + data.savingAccounts.keys.map { "a:$it" },
            initial = pot.reserveRuleIds.mapTo(HashSet()) { "r:$it" } + pot.savingAccountIds.map { "a:$it" },
            label = { key ->
                if (key.startsWith("r:")) data.reserves.first { it.rule!!.id == key.substring(2) }.title
                else data.savingAccounts.getValue(key.substring(2).toLong())
            },
            supportingText = { key ->
                stringResource(if (key.startsWith("r:")) R.string.next_budget_reserve else R.string.next_budget_account)
            },
            intro = stringResource(R.string.next_budget_saved_with_intro),
            onConfirm = { keys ->
                onChange(
                    pot.copy(
                        reserveRuleIds = keys.filter { it.startsWith("r:") }.mapTo(HashSet()) { it.substring(2) },
                        savingAccountIds = keys.filter { it.startsWith("a:") }.mapTo(HashSet()) { it.substring(2).toLong() }
                    )
                )
                close()
            },
            onDismiss = close
        )

        EditorDialog.Start -> DateDialog(
            initial = pot.start,
            onConfirm = { onChange(pot.copy(start = it)); close() },
            onDismiss = close,
            onClear = { onChange(pot.copy(start = null)); close() },
            clearLabel = stringResource(R.string.next_budget_no_date)
        )

        EditorDialog.End -> DateDialog(
            initial = pot.end,
            onConfirm = { onChange(pot.copy(end = it)); close() },
            onDismiss = close,
            onClear = { onChange(pot.copy(end = null)); close() },
            clearLabel = stringResource(R.string.next_budget_no_date)
        )

        EditorDialog.ItemGroups -> IdsSheet(
            title = stringResource(R.string.next_budget_item_groups),
            options = ItemGroup.entries.filter { it != ItemGroup.DEPOSIT }.map { it.id },
            initial = pot.itemGroups,
            label = { id -> stringResource(ItemGroup.ofId(id)!!.labelRes) },
            onConfirm = { onChange(pot.copy(itemGroups = it)); close() },
            onDismiss = close
        )

        EditorDialog.MaxShare -> {
            val invalid = stringResource(R.string.next_budget_share_invalid)
            TextDialog(
                title = stringResource(R.string.next_budget_max_share_label),
                initialText = pot.maxSharePercent?.toString() ?: "",
                onConfirm = { text -> text.trim().toIntOrNull()?.let { onChange(pot.copy(maxSharePercent = it)) }; close() },
                onDismiss = close,
                keyboardType = KeyboardType.Number,
                error = { text -> invalid.takeIf { text.isNotBlank() && text.trim().toIntOrNull()?.takeIf { it in 1..100 } == null } }
            )
        }

        EditorDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.next_budget_delete),
            text = stringResource(R.string.next_budget_delete_text, pot.name),
            confirmLabel = stringResource(R.string.next_budget_delete),
            onConfirm = { close(); onDelete?.invoke() },
            onDismiss = close
        )

        null -> {}
    }
}

/** A [MultiChoiceSheet] whose choice is kept until it is confirmed */
@Composable
private fun <T> IdsSheet(
    title: String,
    options: List<T>,
    initial: Set<T>,
    label: @Composable (T) -> String,
    onConfirm: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
    supportingText: @Composable (T) -> String? = { null },
    intro: String? = null,
) {
    var selected by remember { mutableStateOf(initial.toList()) }
    MultiChoiceSheet(
        title = title,
        options = options,
        selected = selected.toSet(),
        onToggle = { option -> selected = if (option in selected) selected - option else selected + option },
        label = label,
        onConfirm = { onConfirm(selected.toSet()) },
        onDismiss = onDismiss,
        supportingText = supportingText,
        intro = intro
    )
}

/** [text] as typed, e.g. "12,50", in minor units of [currency], null if it is no amount above 0 */
internal fun parseAmount(text: String, currency: CurrencyInfo): Long? =
    text.trim().replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 }
        ?.movePointRight(currency.fractionDigits)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.toLong()

private val PotKind.amountLabelRes: Int
    get() = when (this) {
        PotKind.RUNNING -> R.string.next_budget_amount_running
        PotKind.YEARLY -> R.string.next_budget_amount_yearly
        PotKind.GOAL -> R.string.next_budget_amount_goal
        PotKind.PROJECT, PotKind.ITEM_GROUP -> R.string.next_budget_amount_project
    }

/** A new pot from [template], or an empty one of its own without template */
fun newPot(template: PotTemplate?, name: String, categories: List<CategoryInfo>, today: LocalDate) = Pot(
    name = name,
    kind = template?.kind ?: PotKind.RUNNING,
    icon = template?.icon,
    categoryIds = template?.categoriesIn(categories).orEmpty(),
    start = when (template?.kind) {
        PotKind.PROJECT -> null
        else -> today
    }
)

private val previewCategories = listOf(
    CategoryInfo(1, "Lebensmittel", "Lebensmittel"),
    CategoryInfo(2, "Essen gehen", "Essen gehen"),
    CategoryInfo(3, "Freizeit & Hobbys", "Freizeit & Hobbys"),
    CategoryInfo(4, "Fitnessstudio & Sport", "Freizeit & Hobbys > Fitnessstudio & Sport", mainId = 3, parentId = 3),
)

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PotTemplateScreenPreview() {
    NextPreviewTheme {
        PotTemplateScreen(previewCategories, {}, {})
    }
}
