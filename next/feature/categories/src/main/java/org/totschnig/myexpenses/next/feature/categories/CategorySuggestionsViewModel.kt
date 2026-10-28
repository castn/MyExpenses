package org.totschnig.myexpenses.next.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.categories.CategoryAnalysis
import org.totschnig.myexpenses.next.data.categories.CategoryDecisions
import org.totschnig.myexpenses.next.data.categories.CategoryRepository
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.data.categories.CategorySuggester
import org.totschnig.myexpenses.next.data.categories.CategorySuggestion
import org.totschnig.myexpenses.next.data.categories.CategoryWriting
import org.totschnig.myexpenses.next.data.categories.Counterpart
import org.totschnig.myexpenses.next.data.categories.counterpart
import org.totschnig.myexpenses.next.data.categories.rejecting
import org.totschnig.myexpenses.next.data.categories.withRule

/**
 * Suggests categories for transactions without one, see [CategorySuggester]. Certain suggestions for
 * new transactions are applied without asking, for older ones the user is asked once, see [backlog].
 * Needs the consent to analyse transactions, see [LedgerSource.consent].
 *
 * Certain suggestions are applied by [CategoryRepository.appliedAutomatically], also without this
 * view model.
 */
class CategorySuggestionsViewModel(private val repository: CategoryRepository) : ViewModel() {

    private val store: CategoryStore
        get() = repository.store

    private val writing: CategoryWriting
        get() = repository.writing

    private val analysis: SharedFlow<CategoryAnalysis?>
        get() = repository.analysis

    /**
     * Certain suggestions for transactions from before suggestions were started, and how many
     * transactions are without category. Null when there is nothing to ask.
     */
    data class Backlog(val certain: Int, val uncategorized: Int)

    val backlog: StateFlow<Backlog?> =
        analysis.map { analysis ->
            analysis?.plan?.backlog?.takeIf { it.isNotEmpty() }?.let { backlog ->
                Backlog(backlog.size, analysis.inputs.count { it.categoryId == null })
            }
        }.stateWhileShown(viewModelScope, null)

    /** Applies the certain suggestions for transactions from before */
    fun applyBacklog() {
        viewModelScope.launch {
            val analysis = analysis.replayCache.lastOrNull() ?: return@launch
            repository.applyAutomatically(analysis.plan.backlog)
            store.update { it.copy(backlogAnswered = true) }
        }
    }

    /** Transactions from before only get suggestions, which are shown in their details */
    fun declineBacklog() {
        update { it.copy(backlogAnswered = true) }
    }

    /**
     * What is known about the category of a transaction: a suggestion, or that it was applied without asking
     */
    sealed interface CategoryState {
        data object None : CategoryState

        /** @param path of the suggested category */
        data class Suggested(val suggestion: CategorySuggestion, val path: String) : CategoryState
        data class Automatic(val categoryId: Long, val path: String) : CategoryState
    }

    /**
     * @param currentCategoryId the category the transaction had when it was loaded. The transaction
     * is looked up in the analysis, so that the state follows changes made meanwhile.
     */
    fun stateOf(transactionId: Long, currentCategoryId: Long?): Flow<CategoryState> = analysis.map { analysis ->
        analysis ?: return@map CategoryState.None
        val categoryId = analysis.inputs.find { it.id == transactionId }?.let { it.categoryId } ?: currentCategoryId
        if (categoryId != null) {
            analysis.decisions.automatic[transactionId]?.takeIf { it == categoryId }
                ?.let { id -> analysis.paths[id]?.let { CategoryState.Automatic(id, it) } } ?: CategoryState.None
        } else {
            analysis.plan.suggestions[transactionId]
                ?.let { suggestion -> analysis.paths[suggestion.categoryId]?.let { CategoryState.Suggested(suggestion, it) } }
                ?: CategoryState.None
        }
    }

    /** Paths of the suggested categories by transaction, to show them in lists */
    val suggestedPaths: StateFlow<Map<Long, String>> =
        analysis.map { analysis ->
            analysis?.plan?.suggestions?.mapNotNull { (id, suggestion) -> analysis.paths[suggestion.categoryId]?.let { id to it } }
                ?.toMap().orEmpty()
        }.stateWhileShown(viewModelScope, emptyMap())

    /**
     * Who a transaction is with, and the other transactions with the same counterpart still without
     * category, e.g. to accept a suggestion for all of them
     */
    data class CounterpartInfo(val counterpart: Counterpart, val uncategorized: List<Long>)

    /** Null if the transaction has no counterpart, e.g. a purchase through Amazon, see [counterpart] */
    fun counterpartOf(transactionId: Long): Flow<CounterpartInfo?> = analysis.map { analysis ->
        analysis ?: return@map null
        val input = analysis.inputs.find { it.id == transactionId } ?: return@map null
        val counterpart = input.counterpart() ?: return@map null
        CounterpartInfo(
            counterpart,
            analysis.inputs.filter {
                it.id != transactionId && it.categoryId == null && (it.amount < 0) == (input.amount < 0) &&
                        it.counterpart()?.key == counterpart.key
            }.map { it.id }
        )
    }

    /** Sets the category of transactions that have none, never overwrites a category */
    fun accept(transactionIds: List<Long>, categoryId: Long) {
        viewModelScope.launch { writing.setMissingCategory(transactionIds, categoryId) }
    }

    private val _justApplied = MutableStateFlow<Map<Long, Pair<Long, String>>>(emptyMap())

    /**
     * Categories the user just gave transactions in their details, with their paths, by transaction:
     * the details then offer the category for the other transactions of the counterpart
     */
    val justApplied: StateFlow<Map<Long, Pair<Long, String>>> = _justApplied

    fun markApplied(transactionId: Long, categoryId: Long, path: String) {
        _justApplied.update { it + (transactionId to (categoryId to path)) }
    }

    /** When the details are closed */
    fun clearApplied(transactionId: Long) {
        _justApplied.update { it - transactionId }
    }

    /**
     * The user chose [categoryId] for a transaction, instead of a suggestion or of its category
     * [fromCategoryId]. A category applied without asking is not applied again.
     */
    fun change(transactionId: Long, fromCategoryId: Long?, categoryId: Long) {
        viewModelScope.launch {
            writing.changeCategory(transactionId, fromCategoryId, categoryId)
            if (fromCategoryId != null) store.update { decisions ->
                if (decisions.automatic[transactionId] == fromCategoryId) decisions.rejecting(transactionId, fromCategoryId)
                else decisions
            }
        }
    }

    /** The suggestion is not made again for this transaction */
    fun reject(suggestion: CategorySuggestion) {
        update { it.copy(rejected = it.rejected + (suggestion.transactionId to suggestion.categoryId)) }
    }

    /**
     * Takes back a category applied without asking, which is not applied again
     */
    fun undoAutomatic(transactionId: Long, categoryId: Long) {
        viewModelScope.launch {
            writing.removeCategory(transactionId, categoryId)
            store.update { it.rejecting(transactionId, categoryId) }
        }
    }

    /**
     * Transactions with [counterpart] always get [categoryId] ("Immer so machen"), replacing a rule
     * the counterpart had
     */
    fun addRule(counterpart: Counterpart, categoryId: Long) {
        if (counterpart.payeeId == null && counterpart.merchant == null) return
        update { it.withRule(counterpart, categoryId) { UUID.randomUUID().toString() } }
    }

    private fun update(change: (CategoryDecisions) -> CategoryDecisions) {
        viewModelScope.launch { store.update(change) }
    }
}
