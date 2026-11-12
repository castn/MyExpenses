package org.totschnig.myexpenses.next.transactions

import android.content.Context
import android.database.Cursor
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.RepositoryTemplate
import org.totschnig.myexpenses.db2.createTemplate
import org.totschnig.myexpenses.db2.deleteTemplate
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.db2.getCurrencyUnitForAccount
import org.totschnig.myexpenses.db2.getPartyName
import org.totschnig.myexpenses.db2.instantiateTemplate
import org.totschnig.myexpenses.db2.loadTemplate
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.requireParty
import org.totschnig.myexpenses.db2.templateUsed
import org.totschnig.myexpenses.db2.updateTemplate
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.next.data.transactions.EditableTemplate
import org.totschnig.myexpenses.next.data.transactions.TemplateApplied
import org.totschnig.myexpenses.next.data.transactions.TemplateKind
import org.totschnig.myexpenses.next.data.transactions.TemplateStore
import org.totschnig.myexpenses.next.data.transactions.TemplateSummary
import org.totschnig.myexpenses.next.data.transactions.TemplateValues
import org.totschnig.myexpenses.next.toInfo
import org.totschnig.myexpenses.provider.KEY_ACCOUNT_LABEL
import org.totschnig.myexpenses.provider.KEY_LAST_USED
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_TITLE
import org.totschnig.myexpenses.provider.KEY_USAGES
import org.totschnig.myexpenses.provider.TransactionProvider.TEMPLATES_URI
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.util.ExchangeRateHandler
import org.totschnig.myexpenses.util.ShortcutHelper
import org.totschnig.myexpenses.viewmodel.AccountSealedException

/**
 * [TemplateStore] on the database of the app. Saving at once is `instantiateTemplate` of the app,
 * which also handles splits and transfers; a change of whether a template saves at once is passed
 * on to its pinned shortcut.
 */
class RepositoryTemplateStore(
    private val context: Context,
    private val repository: Repository,
    private val currencyContext: CurrencyContext,
    private val exchangeRateHandler: ExchangeRateHandler
) : TemplateStore {

    override fun templates(): Flow<List<TemplateSummary>> = repository.contentResolver.observeQuery(
        TEMPLATES_URI,
        selection = "$KEY_PARENTID IS NULL",
        sortOrder = "$KEY_USAGES DESC, $KEY_LAST_USED DESC, $KEY_TITLE COLLATE NOCASE",
        notifyForDescendants = true
    ).mapToList { it.toSummary() }

    override suspend fun load(id: Long): EditableTemplate? {
        val template = repository.loadTemplate(id, require = false, withTags = true)?.data
            ?: return null
        if (template.isSplit || template.isTransfer) return null
        return EditableTemplate(
            id = id,
            values = TemplateValues(
                title = template.title,
                accountId = template.accountId,
                amount = template.amount,
                payee = template.payeeName ?: template.payeeId?.let { repository.getPartyName(it) },
                categoryId = template.categoryId,
                tagIds = template.tagList,
                methodId = template.methodId,
                comment = template.comment,
                saveDirectly = template.defaultAction == Template.Action.SAVE
            ),
            categoryPath = template.categoryPath,
            fractionDigits =
                repository.getCurrencyUnitForAccount(template.accountId)?.fractionDigits ?: 2
        )
    }

    override suspend fun create(values: TemplateValues): Long = repository.createTemplate(
        Template(title = values.title, accountId = values.accountId, uuid = generateUuid())
            .with(values)
    ).id

    override suspend fun save(id: Long, values: TemplateValues) {
        val template = requireNotNull(repository.loadTemplate(id, require = false)?.data) {
            "Template not found"
        }
        val changed = template.with(values)
        repository.updateTemplate(changed)
        if (changed.defaultAction != template.defaultAction) {
            ShortcutHelper.updatePinnedTemplateShortcut(context, id, changed.defaultAction)
        }
    }

    override suspend fun delete(id: Long) = repository.deleteTemplate(id)

    override suspend fun apply(id: Long): TemplateApplied =
        repository.instantiateTemplate(exchangeRateHandler, id, currencyContext).fold(
            onSuccess = { TemplateApplied.Saved(it.id) },
            onFailure = {
                if (it is AccountSealedException) {
                    TemplateApplied.AccountClosed
                } else {
                    TemplateApplied.Failed
                }
            }
        )

    override suspend fun used(id: Long) = repository.templateUsed(id)

    override suspend fun createFrom(transactionId: Long): Long {
        val transaction = repository.loadTransaction(transactionId, withTags = true)
        check(!transaction.data.sealed) { "Account closed" }
        val data = transaction.data
        val title = data.payeeName ?: data.payeeId?.let { repository.getPartyName(it) }
            ?: data.categoryPath.orEmpty()
        return repository.createTemplate(
            RepositoryTemplate.fromTransaction(
                transaction.copy(
                    data = data.copy(
                        tagList = transaction.tags.orEmpty().map {
                            it.id
                        }
                    )
                ),
                title
            )
        ).id
    }

    private fun Template.with(values: TemplateValues) = copy(
        title = values.title,
        accountId = values.accountId,
        amount = values.amount,
        payeeId = values.payee?.let { repository.requireParty(it) },
        categoryId = values.categoryId,
        tagList = values.tagIds,
        methodId = values.methodId,
        comment = values.comment,
        defaultAction = if (values.saveDirectly) Template.Action.SAVE else Template.Action.EDIT
    )

    private fun Cursor.toSummary(): TemplateSummary {
        val template = Template.fromCursor(this)
        return TemplateSummary(
            id = template.id,
            title = template.title,
            kind = when {
                template.isSplit -> TemplateKind.SPLIT
                template.isTransfer -> TemplateKind.TRANSFER
                else -> TemplateKind.ENTRY
            },
            amount = template.amount,
            currency = currencyContext[requireNotNull(template.currency)].toInfo(),
            accountLabel = getString(KEY_ACCOUNT_LABEL),
            payee = template.payeeName,
            categoryPath = template.categoryPath?.takeIf { !template.isSplit },
            comment = template.comment,
            isClosed = template.sealed,
            saveDirectly = template.defaultAction == Template.Action.SAVE
        )
    }
}
