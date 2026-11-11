package org.totschnig.myexpenses.next.data.transactions

import java.io.Serializable
import java.time.LocalDate
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo

/** What a template holds; only [ENTRY] can be changed and put into the form */
enum class TemplateKind {
    /** An expense or income of one account */
    ENTRY,

    /** Split into parts, from the classic form */
    SPLIT,

    /** A transfer between two accounts, from the classic form */
    TRANSFER
}

/**
 * A template as listed, the ones used most first
 *
 * @param amount in minor units of [currency], negative for an expense
 * @param isClosed its account is closed, nothing can be saved from it
 * @param saveDirectly a tap saves the transaction at once instead of opening the form
 */
data class TemplateSummary(
    val id: Long,
    val title: String,
    val kind: TemplateKind,
    val amount: Long,
    val currency: CurrencyInfo,
    val accountLabel: String,
    val payee: String?,
    val categoryPath: String?,
    val comment: String?,
    val isClosed: Boolean,
    val saveDirectly: Boolean
) {
    /** It can be put into the form, changed and offered as a chip there */
    val isEntry get() = kind == TemplateKind.ENTRY
}

/**
 * What can be entered of a template: the values of an expense or income without a day
 *
 * @param amount in minor units, negative for an expense, 0 if left empty
 */
data class TemplateValues(
    val title: String,
    val accountId: Long,
    val amount: Long,
    val payee: String?,
    val categoryId: Long?,
    val tagIds: List<Long>,
    val methodId: Long?,
    val comment: String?,
    val saveDirectly: Boolean
)

/**
 * A template as loaded for the form
 *
 * @param categoryPath of [TemplateValues.categoryId], to show it
 */
data class EditableTemplate(
    val id: Long,
    val values: TemplateValues,
    val categoryPath: String?,
    val fractionDigits: Int
)

/**
 * The part of a [TransactionDraft] that only a template has
 *
 * @param id of the template changed, [TransactionDraft.NEW] for a new one
 * @param saveDirectly see [TemplateSummary.saveDirectly]
 */
data class TemplateFields(val id: Long, val title: String, val saveDirectly: Boolean) :
    Serializable {
    val isNew get() = id == TransactionDraft.NEW

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** What saving a transaction from a template at once ended with */
sealed interface TemplateApplied {
    /** @param id of the transaction saved */
    data class Saved(val id: Long) : TemplateApplied

    /** The account of the template is closed */
    data object AccountClosed : TemplateApplied

    /** The template was deleted meanwhile, or the database rejected the transaction */
    data object Failed : TemplateApplied
}

/** The draft for changing [this] template; its day is not saved */
fun EditableTemplate.toDraft(decimalSeparator: Char, today: LocalDate) = TransactionDraft(
    id = TransactionDraft.NEW,
    accountId = values.accountId,
    amountText = values.amount.takeIf { it != 0L }
        ?.let { formatAmountText(it, fractionDigits, decimalSeparator) }.orEmpty(),
    isIncome = values.amount > 0,
    day = today,
    payee = values.payee.orEmpty(),
    categoryId = values.categoryId,
    categoryPath = categoryPath,
    tagIds = values.tagIds,
    methodId = values.methodId,
    comment = values.comment.orEmpty(),
    template = TemplateFields(id, values.title, values.saveDirectly)
)

/** The draft for a new template with what [this] transaction has, titled after its payee */
fun EditableTransaction.toTemplateDraft(decimalSeparator: Char, today: LocalDate) =
    EditableTemplate(
        id = TransactionDraft.NEW,
        values = TemplateValues(
            title = values.payee.orEmpty(),
            accountId = values.accountId,
            amount = values.amount,
            payee = values.payee,
            categoryId = values.categoryId,
            tagIds = values.tagIds,
            methodId = values.methodId,
            comment = values.comment,
            saveDirectly = false
        ),
        categoryPath = categoryPath,
        fractionDigits = fractionDigits
    ).toDraft(decimalSeparator, today)

/**
 * This draft of a new transaction filled with [template]: the day stays, the account only if
 * [hasAccount] says the account of the template cannot be chosen. Saving counts as a use of the
 * template.
 */
fun TransactionDraft.withTemplate(
    template: EditableTemplate,
    decimalSeparator: Char,
    hasAccount: (Long) -> Boolean
): TransactionDraft {
    val filled = template.toDraft(decimalSeparator, day)
    return filled.copy(
        id = id,
        accountId = if (hasAccount(filled.accountId)) filled.accountId else accountId,
        template = null,
        fromTemplateId = template.id
    )
}

/**
 * The values of this draft of a template, null if its amount cannot be read. An empty amount is
 * 0, so that the amount is typed when the template is used.
 */
fun TransactionDraft.toTemplateValues(fractionDigits: Int): TemplateValues? {
    val fields = requireNotNull(template) { "No template" }
    val typed = if (amountText.isBlank()) {
        0L
    } else {
        parseAmountText(amountText, fractionDigits)
            ?: return null
    }
    return TemplateValues(
        title = fields.title.trim(),
        accountId = accountId,
        amount = if (isIncome) typed else -typed,
        payee = payee.trim().ifEmpty { null },
        categoryId = categoryId,
        tagIds = tagIds,
        methodId = methodId,
        comment = comment.trim().ifEmpty { null },
        saveDirectly = fields.saveDirectly
    )
}

/** The templates offered as chips in the form of a new transaction */
fun List<TemplateSummary>.forChips(limit: Int = MAX_TEMPLATE_CHIPS) =
    filter { it.isEntry && !it.isClosed }.take(limit)

/** How many templates the form of a new transaction offers, the ones used most */
const val MAX_TEMPLATE_CHIPS = 6
