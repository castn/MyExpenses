package org.totschnig.myexpenses.next.transactions

import org.totschnig.myexpenses.db2.FinTsAttribute
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.createTransaction
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.db2.getCurrencyUnitForAccount
import org.totschnig.myexpenses.db2.getPartyName
import org.totschnig.myexpenses.db2.loadAccount
import org.totschnig.myexpenses.db2.loadActiveTagsForAccount
import org.totschnig.myexpenses.db2.loadAttributes
import org.totschnig.myexpenses.db2.loadSplitParts
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.requireParty
import org.totschnig.myexpenses.db2.updateTransaction
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.PreDefinedPaymentMethod.Companion.translateIfPredefined
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.next.data.transactions.AccountOption
import org.totschnig.myexpenses.next.data.transactions.EditValues
import org.totschnig.myexpenses.next.data.transactions.EditableTransaction
import org.totschnig.myexpenses.next.data.transactions.MethodOption
import org.totschnig.myexpenses.next.data.transactions.SplitPart
import org.totschnig.myexpenses.next.data.transactions.TransactionStore
import org.totschnig.myexpenses.provider.KEY_IS_PORTFOLIO
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_LAST_USED
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_SEALED
import org.totschnig.myexpenses.provider.PORTFOLIO_NONE
import org.totschnig.myexpenses.provider.STATUS_ARCHIVE
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.viewmodel.data.Transaction2

/** [TransactionStore] on the database of the app */
class RepositoryTransactionStore(private val repository: Repository) : TransactionStore {

    override suspend fun load(id: Long): EditableTransaction? {
        val data =
            runCatching { repository.loadTransaction(id, withTransfer = false).data }.getOrNull()
                ?: return null
        if (!data.isEditableHere()) return null
        return EditableTransaction(
            id = id,
            values = data.toValues(),
            categoryPath = data.categoryPath,
            fractionDigits =
                repository.getCurrencyUnitForAccount(data.accountId)?.fractionDigits ?: 2,
            accountType = repository.loadAccount(data.accountId)?.type?.id,
            isAmountEditable = data.originalAmount == null && data.equivalentAmount == null,
            isImported = repository.loadAttributes(id).any { it.first is FinTsAttribute }
        )
    }

    override suspend fun methods(accountType: Long, isIncome: Boolean): List<MethodOption> =
        repository.contentResolver.query(
            TransactionProvider.METHODS_URI.buildUpon()
                .appendPath(TransactionProvider.URI_SEGMENT_TYPE_FILTER)
                .appendPath(if (isIncome) "1" else "-1")
                .appendQueryParameter(
                    TransactionProvider.QUERY_PARAMETER_ACCOUNT_TYPE_LIST,
                    accountType.toString()
                )
                .build(),
            null,
            null,
            null,
            null
        )?.useAndMapToList {
            MethodOption(
                it.getLong(KEY_ROWID),
                it.getString(KEY_LABEL).translateIfPredefined(repository.context)
            )
        }.orEmpty()

    // Like the classic form: open accounts that are no portfolio and no part of one, the one used last first
    override suspend fun accounts(): List<AccountOption> = repository.contentResolver.query(
        TransactionProvider.ACCOUNTS_URI,
        Account.PROJECTION,
        "$KEY_SEALED = 0 AND $KEY_IS_PORTFOLIO = $PORTFOLIO_NONE AND $KEY_PARENTID IS NULL",
        null,
        "$KEY_LAST_USED DESC"
    )?.useAndMapToList { cursor ->
        val account = Account.fromCursor(cursor)
        val currency = repository.currencyContext[account.currency]
        AccountOption(
            id = account.id,
            label = account.label,
            currency = account.currency,
            currencySymbol = currency.symbol,
            fractionDigits = currency.fractionDigits,
            type = account.type?.id
        )
    }.orEmpty()

    override suspend fun defaultTags(accountId: Long): List<Long> =
        repository.loadActiveTagsForAccount(accountId).map { it.id }

    override suspend fun splitParts(id: Long): List<SplitPart> =
        repository.loadSplitParts(id).map { part ->
            SplitPart(
                amount = part.amount,
                categoryPath = part.categoryPath,
                comment = part.comment,
                transferAccount = part.transferAccountId?.let { repository.loadAccount(it)?.label }
            )
        }

    override suspend fun save(id: Long, from: EditValues, to: EditValues) {
        val data = repository.loadTransaction(id, withTransfer = false).data
        fun <T> changed(value: (EditValues) -> T, current: T) = if (value(from) !=
            value(to)
        ) {
            value(to)
        } else {
            current
        }
        val payeeId = if (from.payee !=
            to.payee
        ) {
            to.payee?.let { repository.requireParty(it) }
        } else {
            data.payeeId
        }
        repository.updateTransaction(
            data.copy(
                accountId = changed(EditValues::accountId, data.accountId),
                amount = changed(EditValues::amount, data.amount),
                date = changed(EditValues::date, data.date),
                valueDate = changed(EditValues::valueDate, data.valueDate),
                payeeId = payeeId,
                categoryId = changed(EditValues::categoryId, data.categoryId),
                tagList = changed(EditValues::tagIds, data.tagList),
                methodId = changed(EditValues::methodId, data.methodId),
                comment = changed(EditValues::comment, data.comment)
            )
        )
    }

    override suspend fun create(values: EditValues): Long = repository.createTransaction(
        Transaction(
            accountId = values.accountId,
            amount = values.amount,
            date = values.date,
            valueDate = values.valueDate,
            payeeId = values.payee?.let { repository.requireParty(it) },
            categoryId = values.categoryId,
            tagList = values.tagIds,
            methodId = values.methodId,
            comment = values.comment,
            uuid = generateUuid()
        )
    ).id

    private fun Transaction.toValues() = EditValues(
        accountId = accountId,
        amount = amount,
        date = date,
        valueDate = valueDate,
        payee = payeeName ?: payeeId?.let { repository.getPartyName(it) },
        categoryId = categoryId,
        tagIds = tagList,
        methodId = methodId,
        comment = comment
    )
}

/**
 * Whether the details may be able to change this transaction, before [TransactionStore.load] tells
 * for sure; the others go to the classic form at once
 */
fun Transaction2.isEditableInDetails() =
    !isSplit && !isTransfer && !isSplitPart && !isArchive && crStatus != CrStatus.VOID

/** No split, transfer or part of a split, not cancelled, no archive and not in a closed account */
private fun Transaction.isEditableHere() = !isSplit && transferPeerId == null && parentId == null &&
    crStatus != CrStatus.VOID && status != STATUS_ARCHIVE && !sealed
