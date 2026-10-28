package org.totschnig.myexpenses.next.feature.tags

import org.totschnig.myexpenses.next.core.common.ledger.Memo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.data.categories.counterpart
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.tags.TagDatabase
import org.totschnig.myexpenses.next.data.tags.TagInput
import org.totschnig.myexpenses.next.data.tags.TagLearner
import org.totschnig.myexpenses.next.data.tags.TagMeaning
import org.totschnig.myexpenses.next.data.tags.TagRepository
import org.totschnig.myexpenses.next.data.tags.TagRule
import org.totschnig.myexpenses.next.data.tags.TagStore
import org.totschnig.myexpenses.next.data.tags.TagSuggestion
import org.totschnig.myexpenses.next.data.tags.addTag
import org.totschnig.myexpenses.next.data.tags.isLearnable

/**
 * Tags suggested by what was given to the other transactions of the same counterpart, and rules
 * that tag new transactions automatically, see [TagLearner]. Needs the consent to analyse
 * transactions.
 *
 * The rules are applied by [TagRepository.appliedRules], also without this view model.
 */
class TagLearningViewModel(
    private val repository: TagRepository,
    private val tagDatabase: TagDatabase,
    /** Where the database is read and written */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val store: TagStore
        get() = repository.store

    /** See [TagRepository.tagInputs] */
    private val tagInputs: Flow<List<TagInput>?>
        get() = repository.tagInputs

    private data class LearningInput(val inputs: List<TagInput>, val meanings: Map<Long, TagMeaning>, val rejected: Set<String>)

    private val suggestionMemo = Memo<LearningInput, Map<Long, TagSuggestion>>("tags: learning suggestions") { (inputs, meanings, rejected) ->
        TagLearner.suggest(inputs, { tagId -> meanings[tagId].isLearnable }, rejected)
    }

    /** By transaction */
    val suggestions: StateFlow<Map<Long, TagSuggestion>> =
        combine(tagInputs, store.meanings, store.rejected(TagStore.Rejected.SUGGESTIONS)) { inputs, meanings, rejected ->
            inputs?.let { suggestionMemo.of(LearningInput(it, meanings, rejected)) }.orEmpty()
        }
            .flowOn(Dispatchers.Default)
            .stateWhileShown(viewModelScope, emptyMap())

    private val _justAccepted = MutableStateFlow<Map<Long, TagSuggestion>>(emptyMap())

    /**
     * Suggestions just taken, by transaction, to offer them for the other transactions of the
     * counterpart and for the future, until the details are closed
     */
    val justAccepted: StateFlow<Map<Long, TagSuggestion>> = _justAccepted

    fun acceptSuggestion(suggestion: TagSuggestion) {
        _justAccepted.value += suggestion.transactionId to suggestion
        addTag(listOf(suggestion.transactionId), suggestion.tagId)
    }

    fun clearAccepted(transactionId: Long) {
        _justAccepted.value -= transactionId
    }

    /** Gives [tagId] to [transactionIds], e.g. to the other transactions of a counterpart */
    fun addTag(transactionIds: List<Long>, tagId: Long) {
        viewModelScope.launch(io) { tagDatabase.addTag(transactionIds, tagId) }
    }

    /** The suggestion is not made again */
    fun rejectSuggestion(suggestion: TagSuggestion) {
        viewModelScope.launch { store.reject(TagStore.Rejected.SUGGESTIONS, suggestion.key) }
    }

    /** New transactions of [counterpart] get [tagId] automatically from now on */
    fun addRule(counterpart: String, tagId: Long) {
        viewModelScope.launch(io) {
            // Transactions known now were offered with the suggestion, only later ones are tagged
            val afterId = tagInputs.filterNotNull().first().maxOfOrNull { it.id } ?: 0L
            store.addRule(TagRule(counterpart, tagId, afterId))
        }
    }
}
