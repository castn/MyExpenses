package org.totschnig.myexpenses.next.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import java.text.Normalizer
import org.totschnig.myexpenses.next.core.model.ledger.TagInfo
import org.totschnig.myexpenses.next.core.model.transactions.ShownTransaction
import org.totschnig.myexpenses.next.data.tags.TagDatabase
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.data.tags.TagRepository
import org.totschnig.myexpenses.next.data.tags.TagStore
import org.totschnig.myexpenses.next.data.tags.addMeaningTag
import org.totschnig.myexpenses.next.data.tags.removeTags

/**
 * The tags of the user and their meanings: shown and changed in the details of transactions. The
 * facts other features read come from [TagRepository]. Suggestions of tags are in
 * [TagLearningViewModel], trips and refunds in [TagDetectionsViewModel].
 */
class TagsViewModel(
    private val repository: TagRepository,
    private val tagDatabase: TagDatabase,
    /** Where the database is read and written */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val ledgerSource: LedgerSource
        get() = repository.ledgerSource

    private val store: TagStore
        get() = repository.store

    /** All tags, sorted by label */
    val tags: StateFlow<List<TagInfo>> =
        ledgerSource.catalog.map { it.tags }
            .stateWhileShown(viewModelScope, emptyList())

    /** Meanings of the tags by id, see [TagMeaning] */
    val meanings: StateFlow<Map<Long, TagMeaning>> = store.meanings.stateWhileShown(viewModelScope, emptyMap())

    fun setTags(transactionId: Long, tagIds: Collection<Long>) {
        viewModelScope.launch(io) {
            tagDatabase.setTags(transactionId, tagIds.toList())
        }
    }

    /**
     * Creates the tag with [label] if there is none, also when the screen asking is closed before.
     *
     * @param onCreated gets its id
     */
    fun createTag(label: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(withContext(io) { tagDatabase.tagIdOf(label) })
        }
    }

    /** Label of a category named like [label], ignoring case and accents, or null if there is none */
    suspend fun categoryNamed(label: String): String? {
        val normalized = withoutAccents(label)
        return ledgerSource.catalog.first().categories.firstOrNull { withoutAccents(it.label) == normalized }?.label
    }

    /**
     * Marks [transaction] with [meaning], see [addMeaningTag]. Unmarking removes all tags with this
     * meaning from the transaction.
     *
     * @param label of the tag created when there is none with this meaning yet
     */
    fun setMarked(transaction: ShownTransaction, meaning: TagMeaning, marked: Boolean, label: String) {
        viewModelScope.launch(io) {
            if (marked) {
                val tagIds = ledgerSource.catalog.first().tags.mapTo(HashSet()) { it.id }
                tagDatabase.addMeaningTag(store, tagIds, listOf(transaction.id), meaning, label)
            } else {
                val meanings = store.meanings.first()
                tagDatabase.removeTags(transaction.id) { meanings[it] == meaning }
            }
        }
    }

    /** @param meaning null for a theme */
    fun setMeaning(tagId: Long, meaning: TagMeaning?) {
        viewModelScope.launch { store.setMeaning(tagId, meaning) }
    }
}

/** Lower case without accents, e.g. "Café" becomes "cafe" */
private fun withoutAccents(text: String) =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")

private val ACCENTS = Regex("\\p{M}")
