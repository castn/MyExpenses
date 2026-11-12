package org.totschnig.myexpenses.next.feature.transactions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.transactions.EditableTemplate
import org.totschnig.myexpenses.next.data.transactions.TemplateApplied
import org.totschnig.myexpenses.next.data.transactions.TemplateKind
import org.totschnig.myexpenses.next.data.transactions.TemplateStore
import org.totschnig.myexpenses.next.data.transactions.TemplateSummary
import org.totschnig.myexpenses.next.data.transactions.TemplateValues

/** Templates in memory; [splits] and [closed] hold ids of templates that are listed but not editable */
internal class FakeTemplateStore(
    templates: Map<Long, TemplateValues> = emptyMap(),
    private val splits: Set<Long> = emptySet(),
    private val closed: Set<Long> = emptySet()
) : TemplateStore {
    val stored = MutableStateFlow(templates)
    val used = mutableListOf<Long>()
    val applied = mutableListOf<Long>()
    val createdFrom = mutableListOf<Long>()
    var failSave = false

    override fun templates() = stored.map { all -> all.map { (id, values) -> summary(id, values) } }

    private fun summary(id: Long, values: TemplateValues) = TemplateSummary(
        id = id,
        title = values.title,
        kind = if (id in splits) TemplateKind.SPLIT else TemplateKind.ENTRY,
        amount = values.amount,
        currency = CurrencyInfo("EUR", "€", 2),
        accountLabel = "Giro",
        payee = values.payee,
        categoryPath = null,
        comment = values.comment,
        isClosed = id in closed,
        saveDirectly = values.saveDirectly
    )

    override suspend fun load(id: Long) = stored.value[id]?.takeIf { id !in splits }?.let {
        EditableTemplate(
            id,
            it,
            categoryPath = it.categoryId?.let {
                "Kategorie $it"
            },
            fractionDigits = 2
        )
    }

    override suspend fun create(values: TemplateValues): Long {
        check(!failSave) { "failed" }
        val id = 50L + stored.value.size
        stored.update { it + (id to values) }
        return id
    }

    override suspend fun save(id: Long, values: TemplateValues) {
        check(!failSave) { "failed" }
        stored.update { it + (id to values) }
    }

    override suspend fun delete(id: Long) = stored.update { it - id }

    override suspend fun apply(id: Long): TemplateApplied {
        applied += id
        return when {
            id in closed -> TemplateApplied.AccountClosed
            id in stored.value -> TemplateApplied.Saved(200L + applied.size)
            else -> TemplateApplied.Failed
        }
    }

    override suspend fun used(id: Long) {
        used += id
    }

    override suspend fun createFrom(transactionId: Long): Long {
        createdFrom += transactionId
        return 90L
    }
}

internal fun templateValues(
    title: String = "Miete",
    accountId: Long = 2,
    amount: Long = -85000,
    saveDirectly: Boolean = false
) = TemplateValues(
    title = title,
    accountId = accountId,
    amount = amount,
    payee = "Vermieter",
    categoryId = 4,
    tagIds = listOf(3),
    methodId = 1,
    comment = "bar",
    saveDirectly = saveDirectly
)
