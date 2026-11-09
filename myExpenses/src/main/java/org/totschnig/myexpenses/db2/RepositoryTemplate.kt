package org.totschnig.myexpenses.db2

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import androidx.annotation.VisibleForTesting
import org.totschnig.myexpenses.db2.Repository.Companion.RECORD_SEPARATOR
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.db2.entities.Template.Action
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_DEBT_ID
import org.totschnig.myexpenses.provider.KEY_DEFAULT_ACTION
import org.totschnig.myexpenses.provider.KEY_METHODID
import org.totschnig.myexpenses.provider.KEY_ORIGINAL_AMOUNT
import org.totschnig.myexpenses.provider.KEY_ORIGINAL_CURRENCY
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PLANID
import org.totschnig.myexpenses.provider.KEY_PLAN_EXECUTION
import org.totschnig.myexpenses.provider.KEY_PLAN_EXECUTION_ADVANCE
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TAGLIST
import org.totschnig.myexpenses.provider.KEY_TITLE
import org.totschnig.myexpenses.provider.KEY_TRANSFER_ACCOUNT
import org.totschnig.myexpenses.provider.KEY_UUID
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.TransactionProvider.TEMPLATES_URI
import org.totschnig.myexpenses.provider.useAndMapToList
import org.totschnig.myexpenses.util.ExchangeRateHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.licence.LicenceHandler
import org.totschnig.myexpenses.viewmodel.AccountSealedException
import org.totschnig.myexpenses.viewmodel.data.Tag
import java.lang.UnsupportedOperationException
import java.time.LocalDate
import kotlin.math.roundToLong

fun Template.asContentValues(forInsert: Boolean): ContentValues {
    return ContentValues().apply {
        put(KEY_TITLE, title)
        put(KEY_AMOUNT, amount)
        // Storing currency is not in TEMPLATE_CREATE, but it is in `fromCursor`.
        // We assume currency comes from the account.
        put(KEY_COMMENT, comment)
        put(KEY_ACCOUNTID, accountId)
        put(KEY_CATID, categoryId)
        put(KEY_PAYEEID, payeeId)
        put(KEY_METHODID, methodId)
        put(KEY_TRANSFER_ACCOUNT, transferAccountId)
        put(KEY_PARENTID, parentId)
        put(KEY_PLANID, planId)
        put(KEY_PLAN_EXECUTION, if (planExecutionAutomatic) 1 else 0)
        put(KEY_PLAN_EXECUTION_ADVANCE, planExecutionAdvance)
        put(KEY_DEFAULT_ACTION, defaultAction.name)
        put(KEY_ORIGINAL_AMOUNT, originalAmount)
        put(KEY_ORIGINAL_CURRENCY, originalCurrency)
        put(KEY_DEBT_ID, debtId)
        if (forInsert) {
            require(uuid.isNotBlank())
            put(KEY_UUID, uuid)
        }
        put(KEY_TAGLIST, tagList.joinToString("$RECORD_SEPARATOR"))
    }
}

data class RepositoryTemplate(
    val data: Template,
    val splitParts: List<RepositoryTemplate>? = null,
    val tags: List<Tag>? = null,
) {
    val id: Long
        get() = data.id
    val title: String
        get() = data.title
    val isTransfer: Boolean
        get() = data.isTransfer
    val isSplit: Boolean
        get() = splitParts != null

    suspend fun instantiate(
        currencyContext: CurrencyContext,
        exchangeRateHandler: ExchangeRateHandler,
    ): RepositoryTransaction {
        val uuid = generateUuid()
        val instanceData = data.instantiate(uuid)
        return RepositoryTransaction(
            data = instanceData,
            transferPeer = if (data.isTransfer) {
                Transaction(
                    accountId = data.transferAccountId!!,
                    transferAccountId = data.accountId,
                    amount = if (data.currency == data.transferAccountCurrency) -data.amount else {
                        try {
                            val currency = currencyContext[data.currency!!]
                            val transferCurrency = currencyContext[data.transferAccountCurrency!!]
                            val amount = Money(currency, data.amount)
                            val rate = exchangeRateHandler.loadExchangeRate(
                                currency,
                                transferCurrency,
                                LocalDate.now()
                            )
                            Money.buildWithMajor(transferCurrency, -amount.amountMajor.multiply(rate))
                                .getOrThrow().amountMinor
                        } catch (e: Exception) {
                            if (e !is UnsupportedOperationException) {
                                CrashHandler.report(e)
                            }
                            0
                        }
                    },
                    categoryId = data.categoryId,
                    comment = data.comment,
                    categoryPath = data.categoryPath,
                    currency = data.currency,
                    date = instanceData.date,
                    uuid = uuid,
                    tagList = data.tagList
                )
            } else null,
            splitParts = splitParts?.map {
                it.instantiate(currencyContext, exchangeRateHandler)
            },
            tags = tags
        )
    }

    companion object {
        fun fromTransaction(
            t: RepositoryTransaction,
            title: String = "",
            uuid: String = generateUuid(),
        ) = RepositoryTemplate(
            data = Template.deriveFrom(t.data, title, uuid),
            splitParts = t.splitParts?.map {
                RepositoryTemplate(Template.deriveFrom(it.data, "", generateUuid()))
            },
            tags = t.tags
        )
    }
}

fun Repository.getPayeeForTemplate(id: Long) = contentResolver.findBySelection(
    "$KEY_ROWID = ?",
    arrayOf(id.toString()),
    KEY_PAYEEID
)

private fun ContentResolver.findBySelection(
    selection: String,
    selectionArgs: Array<String>,
    column: String,
) =
    query(
        TEMPLATES_URI,
        arrayOf(column),
        selection,
        selectionArgs,
        null
    )?.use {
        if (it.moveToFirst()) it.getLong(0) else null
    } ?: -1


fun Repository.loadTemplate(
    templateId: Long? = null,
    selection: String? = "$KEY_ROWID = ?",
    selectionArgs: Array<String>? = arrayOf(requireNotNull(templateId).toString()),
    require: Boolean = true,
    withTags: Boolean = false,
) = contentResolver.query(
    TEMPLATES_URI,
    null,
    selection,
    selectionArgs,
    null
)!!.use { cursor ->
    when {
        cursor.moveToFirst() -> Template.fromCursor(cursor).let { template ->
            val tags = if (withTags) loadTagsForTemplate(template.id) else null
            RepositoryTemplate(
                data = template.copy(
                    tagList = tags?.map { it.id } ?: emptyList()
                ),
                splitParts = if (template.isSplit)
                    loadSplitPartsForTemplate(template.id).map {
                        val tags = if (withTags) loadTagsForTemplate(it.id) else null
                        RepositoryTemplate(
                            data = it.copy(tagList = tags?.map { it.id } ?: emptyList()),
                            tags = tags
                        )
                    }
                else null,
                tags = tags
            )
        }

        require -> throw IllegalArgumentException("Transaction not found")
        else -> null
    }
}

private fun Repository.loadSplitPartsForTemplate(templateId: Long) = contentResolver.query(
    TEMPLATES_URI, null, "$KEY_PARENTID = ?", arrayOf(templateId.toString()), null
)!!.useAndMapToList {
    Template.fromCursor(it)
}

fun Repository.updateTemplate(
    repositoryTransaction: RepositoryTemplate,
) = when {
    repositoryTransaction.isSplit -> updateSplitTemplate(
        repositoryTransaction.data,
        repositoryTransaction.splitParts!!.map { it.data }
    )

    else -> updateTemplate(repositoryTransaction.data)
}

fun Repository.updateSplitTemplate(
    parentTemplate: Template, splitParts: List<Template>,
): Boolean {
    // --- Validation ---
    require(parentTemplate.isSplit) { "Parent transaction must be a split." }
    require(splitParts.sumOf { it.amount } == parentTemplate.amount) { "Sum of splits must equal parent amount." }
    require(splitParts.all { it.accountId == parentTemplate.accountId }) { "All splits must be in the same account." }

    val operations = ArrayList<ContentProviderOperation>()

    // --- 1. Handle Deletions ---
    // Get IDs of parts that have a non-zero ID (i.e., they already exist in the DB).
    val keepIds = splitParts.mapNotNull { if (it.id != 0L) it.id else null }

    if (keepIds.isEmpty()) {
        // If no existing parts are being kept, delete all children of the parent.
        operations.add(
            ContentProviderOperation.newDelete(TEMPLATES_URI)
                .withSelection("$KEY_PARENTID = ?", arrayOf(parentTemplate.id.toString()))
                .build()
        )
    } else {
        // Otherwise, delete any children that are NOT in the list of IDs to keep.
        val placeholders = List(keepIds.size) { "?" }.joinToString(",")
        val selection = "$KEY_PARENTID = ? AND $KEY_ROWID NOT IN ($placeholders)"
        val selectionArgs = arrayOf(parentTemplate.id.toString()) + keepIds.map { it.toString() }

        operations.add(
            ContentProviderOperation.newDelete(TEMPLATES_URI)
                .withSelection(selection, selectionArgs)
                .build()
        )
    }


    // --- 2. Update Parent Transaction ---
    operations.add(
        ContentProviderOperation.newUpdate(
            ContentUris.withAppendedId(TEMPLATES_URI, parentTemplate.id)
        )
            .withValues(parentTemplate.asContentValues(false))
            .build()
    )

    // --- 3. Insert New Parts and Update Existing Parts ---
    for (template in splitParts) {
        val operation = if (template.id == 0L) {
            // NEW: This is a new split part, so insert it.
            ContentProviderOperation.newInsert(TEMPLATES_URI)
                .withValues(
                    template.asContentValues(true)
                        .apply {
                            put(KEY_PARENTID, parentTemplate.id)
                        })
        } else {
            // EXISTING: This part already exists, so update it.
            ContentProviderOperation.newUpdate(
                ContentUris.withAppendedId(TEMPLATES_URI, template.id)
            )
                .withValues(template.asContentValues(false))
        }
        operations.add(operation.build())
    }

    val results = contentResolver.applyBatch(TransactionProvider.AUTHORITY, operations)
    return results.mapIndexed { index, result ->
        //first result is delete, others either insert or update
        if (index == 0) true else result.uri != null || result.count == 1
    }.all { it }
}

fun Repository.updateTemplate(
    template: Template,
) = contentResolver.update(
    ContentUris.withAppendedId(TEMPLATES_URI, template.id),
    template.asContentValues(false),
    null, null
) == 1

fun Repository.createTemplate(template: RepositoryTemplate) =
    when {
        template.isSplit -> createSplitTemplate(
            template.data,
            template.splitParts!!.map { it.data })

        else -> createTemplate(template.data)
    }

@VisibleForTesting
fun Repository.createTemplate(template: Template): RepositoryTemplate {
    require(template.id == 0L) { "Use updateTemplate for existing templates" }
    require((template.originalAmount != null) == (template.originalCurrency != null)) {
        "originalAmount and originalCurrency must be set together"
    }
    val id = ContentUris.parseId(
        contentResolver.insert(
            TEMPLATES_URI,
            template.asContentValues(true)
        )!!
    )
    return RepositoryTemplate(template.copy(id = id))
}

@VisibleForTesting
fun Repository.createSplitTemplate(
    parentTemplate: Template,
    splits: List<Template>,
): RepositoryTemplate {
    // --- Validation ---
    require(parentTemplate.isSplit) { "Parent transaction must be a split." }
    require(splits.sumOf { it.amount } == parentTemplate.amount) { "Sum of splits must equal parent amount." }

    val operations = ArrayList<ContentProviderOperation>()

    // --- Operation 0: Insert the Parent Transaction ---
    operations.add(
        ContentProviderOperation.newInsert(TEMPLATES_URI)
            .withValues(parentTemplate.asContentValues(true))
            .build()
    )
    val parentBackRefIndex = 0

    // Prepare to build the complete return objects
    val finalSplitParts = mutableListOf<Template>()
    var opIndex = 1 // Start counting operations after the parent

    // --- Process each split part ---
    splits.forEach { splitPart ->
        // --- This is a REGULAR split part ---
        operations.add(
            ContentProviderOperation.newInsert(TEMPLATES_URI)
                .withValues(splitPart.asContentValues(true))
                .withValueBackReference(KEY_PARENTID, parentBackRefIndex)
                .build()
        )
        // Prepare the final object, ID will be filled in later
        finalSplitParts.add(splitPart)
        opIndex++
    }

    // --- Atomically execute all operations ---
    val results = contentResolver.applyBatch(TransactionProvider.AUTHORITY, operations)

    // --- Construct the final return object ---
    val parentId = ContentUris.parseId(results[0].uri!!)
    val finalParent = parentTemplate.copy(id = parentId)

    var resultIndex = 1 // Start processing results after the parent
    val enrichedSplitParts = finalSplitParts.map { splitPart ->
        // Regular split part
        val newId = ContentUris.parseId(results[resultIndex].uri!!)
        resultIndex++
        RepositoryTemplate(splitPart.copy(id = newId, parentId = parentId))
    }
    return RepositoryTemplate(finalParent, enrichedSplitParts)
}

suspend fun Repository.instantiateTemplate(
    exchangeRateHandler: ExchangeRateHandler,
    templateId: Long,
    currencyContext: CurrencyContext,
): Result<RepositoryTransaction> {
    val template = loadTemplate(templateId, withTags = true, require = false)
        ?: return Result.failure(Exception("Template not found"))

    val account = loadAccount(template.data.accountId)!!

    if (account.isSealed) return Result.failure(AccountSealedException())


    val t = createTransaction(
        template.instantiate(currencyContext, exchangeRateHandler)
            .let { transaction ->
                if (template.data.dynamic) {
                    val homeCurrency = currencyContext.homeCurrencyUnit

                    val currency = currencyContext[account.currency]
                    val amount = Money(currency, template.data.amount)
                    transaction.copy(
                        data = transaction.data.copy(
                            equivalentAmount = try {
                                val date = LocalDate.now()
                                val rate = exchangeRateHandler.loadExchangeRate(
                                    currency,
                                    homeCurrency,
                                    date
                                )
                                Money.buildWithMajor(homeCurrency, amount.amountMajor.multiply(rate))
                                    .getOrThrow().amountMinor
                            } catch (_: Exception) {
                                (amount.amountMinor * account.exchangeRate).roundToLong()
                            }
                        )
                    )
                } else transaction
            }
    )
    return Result.success(t)
}

fun Repository.updateNewSplitTemplateEnabled(licenceHandler: LicenceHandler) {
    var newSplitTemplateEnabled = true
    if (!licenceHandler.hasAccessTo(ContribFeature.SPLIT_TEMPLATE)) {
        if (count(
                TEMPLATES_URI,
                "$KEY_CATID = $SPLIT_CATID",
                null
            ) >= ContribFeature.FREE_SPLIT_TEMPLATES
        ) {
            newSplitTemplateEnabled = false
        }
    }
    prefHandler.putBoolean(PrefKey.NEW_SPLIT_TEMPLATE_ENABLED, newSplitTemplateEnabled)
}

fun Repository.deleteTemplate(id: Long) {
    contentResolver.delete(
        TEMPLATES_URI.buildUpon().appendPath(id.toString())
            .build(),
        null,
        null
    )
}

fun Repository.insertTemplate(
    title: String,
    accountId: Long,
    amount: Long = 0L,
    categoryId: Long? = null,
    transferAccountId: Long? = null,
    defaultAction: Action = Action.EDIT,
    payeeId: Long? = null,
    methodId: Long? = null,
    comment: String? = null,
    debtId: Long? = null,
    tagList: List<Long> = emptyList()
) = createTemplate(
    Template(
        title = title,
        accountId = accountId,
        amount = amount,
        defaultAction = defaultAction,
        categoryId = categoryId,
        transferAccountId = transferAccountId,
        uuid = generateUuid(),
        payeeId = payeeId,
        methodId = methodId,
        comment = comment,
        debtId = debtId,
        tagList = tagList
    )
)
