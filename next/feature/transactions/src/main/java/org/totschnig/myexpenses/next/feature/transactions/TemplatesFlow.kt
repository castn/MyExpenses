package org.totschnig.myexpenses.next.feature.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.totschnig.myexpenses.designsystem.ActionItemRow
import org.totschnig.myexpenses.designsystem.ActionSheet
import org.totschnig.myexpenses.designsystem.BadgeStyle
import org.totschnig.myexpenses.designsystem.Banner
import org.totschnig.myexpenses.designsystem.ConfirmDialog
import org.totschnig.myexpenses.designsystem.DetailCard
import org.totschnig.myexpenses.designsystem.EmptyState
import org.totschnig.myexpenses.designsystem.LoadingBlock
import org.totschnig.myexpenses.designsystem.PageTopBar
import org.totschnig.myexpenses.designsystem.SheetAction
import org.totschnig.myexpenses.designsystem.Spacing
import org.totschnig.myexpenses.designsystem.TopBarAction
import org.totschnig.myexpenses.next.core.common.R
import org.totschnig.myexpenses.next.core.ui.rememberAmountFormat
import org.totschnig.myexpenses.next.data.transactions.TemplateApplied
import org.totschnig.myexpenses.next.data.transactions.TemplateKind
import org.totschnig.myexpenses.next.data.transactions.TemplateSummary

/**
 * The templates: the list, where a tap saves a transaction at once or opens it in the form of a new
 * transaction, as the template says, and the form of a template, on a back stack of their own
 * ([TemplateKey])
 *
 * @param editor enters the templates, in the same form as transactions
 * @param onOpenForm opens the form of a new transaction filled with a template
 * @param fromTransaction starts with a new template of this transaction instead of the list, and
 * closes when it is saved
 */
@Composable
fun TemplatesFlow(
    templates: TemplatesViewModel,
    editor: TransactionEditViewModel,
    tagField: TagField?,
    onOpenForm: (templateId: Long) -> Unit,
    onClose: () -> Unit,
    fromTransaction: Long? = null
) {
    val list by templates.templates.collectAsStateWithLifecycle()
    val message by templates.message.collectAsStateWithLifecycle()
    val editState by editor.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(
        if (fromTransaction !=
            null
        ) {
            TemplateKey.Editor(fromTransaction = fromTransaction)
        } else {
            TemplateKey.List
        }
    )
    val snackbar = remember { SnackbarHostState() }
    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull() else onClose()
    }
    val current by rememberUpdatedState(
        TemplatesContent(list, editState, templates, editor, tagField, onOpenForm, ::back)
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .safeDrawingPadding()
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = ::back,
            entryProvider = entryProvider {
                entry<TemplateKey.List> { TemplateList(current, backStack, onClose) }
                entry<TemplateKey.Editor> { key -> TemplateEditor(current, key) }
            }
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    TemplateMessages(message, editState, templates, editor, snackbar) {
        // From the form back to the list, which shows the template saved
        if (backStack.lastOrNull() is TemplateKey.Editor) {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                backStack.add(0, TemplateKey.List)
                backStack.removeLastOrNull()
            }
        }
    }
}

/** What the pages on the back stack of [TemplatesFlow] need */
private class TemplatesContent(
    val list: List<TemplateSummary>?,
    val editState: TransactionEditState,
    val actions: TemplateListActions,
    val editor: TemplateEditActions,
    val tagField: TagField?,
    val onOpenForm: (Long) -> Unit,
    val back: () -> Unit
)

/**
 * Results of saving at once and of saving a template, in the snackbar
 *
 * @param onTemplateSaved leaves the form of the template saved
 */
@Composable
private fun TemplateMessages(
    message: TemplateMessage?,
    editState: TransactionEditState,
    actions: TemplateListActions,
    editor: TemplateEditActions,
    snackbar: SnackbarHostState,
    onTemplateSaved: () -> Unit
) {
    val text = message?.let {
        when (it.result) {
            is TemplateApplied.Saved -> stringResource(R.string.next_template_applied, it.title)
            TemplateApplied.AccountClosed -> stringResource(R.string.next_template_apply_closed)
            TemplateApplied.Failed -> stringResource(R.string.next_template_apply_failed)
        }
    }
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        try {
            snackbar.showSnackbar(text)
        } finally {
            actions.messageShown()
        }
    }
    val saved = stringResource(R.string.next_template_saved)
    LaunchedEffect(editState.savedTemplate) {
        if (editState.savedTemplate == null) return@LaunchedEffect
        editor.templateSavedShown()
        onTemplateSaved()
        snackbar.showSnackbar(saved)
    }
    val failed = stringResource(R.string.next_template_save_failed)
    LaunchedEffect(editState.isSaveFailed) {
        if (editState.isSaveFailed) snackbar.showSnackbar(failed)
    }
}

@Composable
private fun TemplateList(
    content: TemplatesContent,
    backStack: NavBackStack<NavKey>,
    onClose: () -> Unit
) {
    val actions = content.actions
    var actionsOf by remember { mutableStateOf<TemplateSummary?>(null) }
    var toDelete by remember { mutableStateOf<TemplateSummary?>(null) }
    fun open(template: TemplateSummary) {
        if (template.saveDirectly ||
            !template.isEntry
        ) {
            actions.apply(template)
        } else {
            content.onOpenForm(template.id)
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageTopBar(onBack = onClose, title = stringResource(R.string.next_templates_title)) {
            TopBarAction(
                Icons.Default.Add,
                stringResource(R.string.next_template_new_title),
                onClick = { backStack.add(TemplateKey.Editor()) }
            )
        }
        val list = content.list
        when {
            list == null -> LoadingBlock()

            list.isEmpty() -> EmptyState(
                title = stringResource(R.string.next_templates_empty),
                hint = stringResource(R.string.next_templates_empty_hint)
            )

            else -> Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = 8.dp)
            ) {
                DetailCard {
                    list.forEachIndexed { index, template ->
                        TemplateRow(
                            template,
                            isFirst = index == 0,
                            onClick = { open(template) },
                            onActions = { actionsOf = template }
                        )
                    }
                }
            }
        }
    }
    actionsOf?.let { template ->
        ActionSheet(
            actions = templateActions(template, actions, content.onOpenForm, onEdit = {
                backStack.add(TemplateKey.Editor(templateId = template.id))
            }, onDelete = { toDelete = template }),
            onDismiss = { actionsOf = null },
            title = template.title
        )
    }
    toDelete?.let { template ->
        ConfirmDialog(
            title = stringResource(R.string.next_template_delete_title),
            text = stringResource(R.string.next_template_delete_text, template.title),
            confirmLabel = stringResource(R.string.next_template_delete),
            onConfirm = {
                actions.delete(template)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

/** A closed account allows nothing but deleting; a split or a transfer can only be saved */
@Composable
private fun templateActions(
    template: TemplateSummary,
    actions: TemplateListActions,
    onOpenForm: (Long) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) = buildList {
    if (!template.isClosed) {
        add(SheetAction(stringResource(R.string.next_template_apply)) { actions.apply(template) })
        if (template.isEntry) {
            add(
                SheetAction(stringResource(R.string.next_template_open_form)) {
                    onOpenForm(template.id)
                }
            )
            add(SheetAction(stringResource(R.string.next_template_edit), onClick = onEdit))
        }
    }
    add(SheetAction(stringResource(R.string.next_template_delete), onClick = onDelete))
}

@Composable
private fun TemplateRow(
    template: TemplateSummary,
    isFirst: Boolean,
    onClick: () -> Unit,
    onActions: () -> Unit
) {
    val format = rememberAmountFormat(template.currency)
    val badge = when {
        template.isClosed -> stringResource(R.string.next_template_closed)
        template.kind == TemplateKind.SPLIT -> stringResource(R.string.next_template_split)
        template.kind == TemplateKind.TRANSFER -> stringResource(R.string.next_template_transfer)
        template.saveDirectly -> stringResource(R.string.next_template_directly)
        else -> null
    }
    val opensForm = template.isEntry && !template.saveDirectly
    ActionItemRow(
        title = template.title,
        value = format(template.amount),
        actionsLabel = stringResource(R.string.next_transaction_actions),
        onClick = if (template.isClosed) onActions else onClick,
        onActions = onActions,
        supportingText = listOfNotNull(
            template.payee,
            template.categoryPath,
            template.accountLabel
        ).joinToString(" · "),
        isFirst = isFirst,
        badge = badge,
        badgeStyle = if (template.isClosed) BadgeStyle.Missing else BadgeStyle.Outlined,
        onClickLabel = stringResource(
            if (opensForm) R.string.next_template_open_form else R.string.next_template_apply
        )
    )
}

@Composable
private fun TemplateEditor(content: TemplatesContent, key: TemplateKey.Editor) {
    val state = content.editState
    val editor = content.editor
    LaunchedEffect(key) {
        if (key.fromTransaction != null) {
            editor.startTemplateFrom(key.fromTransaction)
        } else {
            editor.startTemplate(key.templateId)
        }
    }
    fun cancel() {
        editor.cancelEditing()
        content.back()
    }
    BackHandler { cancel() }
    val draft = state.draft?.takeIf { it.template != null }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        PageTopBar(
            onBack = ::cancel,
            title = stringResource(
                if (key.templateId ==
                    null
                ) {
                    R.string.next_template_new_title
                } else {
                    R.string.next_template_edit_title
                }
            )
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                state.isTemplateGone -> Banner(
                    title = stringResource(R.string.next_template_gone),
                    messages = emptyList(),
                    modifier = Modifier.padding(Spacing.screen)
                )

                state.hasNoAccount -> Banner(
                    title = stringResource(R.string.next_transaction_no_account),
                    messages = listOf(stringResource(R.string.next_transaction_no_account_hint)),
                    modifier = Modifier.padding(Spacing.screen)
                )

                draft != null -> TransactionForm(
                    state = state,
                    draft = draft,
                    actions = editor,
                    currencySymbol = state.draftAccount?.currencySymbol.orEmpty(),
                    tagField = content.tagField
                )
            }
        }
        if (draft != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { editor.saveDraft() }) {
                    Text(stringResource(R.string.next_transaction_save))
                }
            }
        }
    }
}
