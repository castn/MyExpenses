package org.totschnig.myexpenses.next.feature.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.setup.AutomaticShare
import org.totschnig.myexpenses.next.data.setup.SetupPayee
import org.totschnig.myexpenses.next.data.setup.SetupRepository
import org.totschnig.myexpenses.next.data.setup.SetupStep

/**
 * The pages of the setup, see [SetupFlow]. What the user did in this visit, the payees decided on
 * and put off and the payee waiting for the category picker, is kept in [savedState].
 *
 * @param clock milliseconds, to measure how long a decision takes
 */
class SetupViewModel(
    private val repository: SetupRepository,
    private val savedState: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel(),
    SetupActions {

    private val decided = savedState.getStateFlow(KEY_DECIDED, ArrayList<String>())
    private val skipped = savedState.getStateFlow(KEY_SKIPPED, ArrayList<String>())
    private val unclear = savedState.getStateFlow(KEY_UNCLEAR, ArrayList<String>())
    private val beyondTarget = savedState.getStateFlow(KEY_BEYOND_TARGET, false)

    /** Whether a choice also applies to later transactions ("Immer so machen"), on at first */
    val always: StateFlow<Boolean> = savedState.getStateFlow(KEY_ALWAYS, true)

    /** Null while loading */
    val overview: StateFlow<SetupOverview?> = combine(
        repository.steps,
        repository.progress,
        repository.categoryQueue.filterNotNull(),
        repository.contracts,
        ::setupOverview
    ).stateWhileShown(viewModelScope, null)

    /**
     * Whether the overview offers the setup: it is not closed and an open step has something to
     * decide, so that it is not offered to users whose transactions are described already
     */
    val isOffered: StateFlow<Boolean> = combine(repository.progress, overview) { progress, steps ->
        !progress.closed &&
            steps?.steps.orEmpty().any { it.state != StepState.DONE && it.decisions > 0 }
    }.stateWhileShown(viewModelScope, false)

    private data class Visit(
        val decided: Set<String>,
        val skipped: Set<String>,
        val unclear: Set<String>,
        val beyond: Boolean
    )

    private val visit = combine(decided, skipped, unclear, beyondTarget) { d, s, u, beyond ->
        Visit(d.toSet(), s.toSet(), u.toSet(), beyond)
    }

    /** Null while loading */
    val categories: StateFlow<CategoriesStep?> = combine(
        repository.categoryQueue.filterNotNull(),
        repository.analysis,
        visit,
        repository.progress.map { it.pace },
        repository.backlog
    ) { queue, analysis, visit, pace, backlog ->
        with(visit) {
            categoriesStep(queue, analysis, decided, skipped, unclear, pace, backlog, beyond)
        }
    }.stateWhileShown(viewModelScope, null)

    /** What will be categorized without asking, null while loading */
    val automaticShare: StateFlow<AutomaticShare?> =
        repository.automaticShare.stateWhileShown(viewModelScope, null)

    /** The card shown and since when, to measure the time of a decision */
    private var shown: Pair<String, Long>? = null

    override fun cardShown(card: PayeeCard) {
        if (shown?.first != card.key) shown = card.key to clock()
    }

    override fun answerBacklog(accept: Boolean) {
        viewModelScope.launch { repository.answerBacklog(accept) }
    }

    override fun acceptSuggestion(card: PayeeCard) {
        card.suggestion?.let { categorize(card.payee, it) }
    }

    override fun pickCategory(card: PayeeCard) {
        savedState[KEY_PENDING] = card.key
    }

    override fun picked(categoryId: Long?) {
        val key = savedState.get<String>(KEY_PENDING) ?: return
        savedState.remove<String>(KEY_PENDING)
        if (categoryId == null) return
        viewModelScope.launch {
            val queue = repository.categoryQueue.filterNotNull().first()
            queue.all.find { it.counterpart.key == key }?.let { categorize(it, categoryId) }
        }
    }

    override fun setAlways(always: Boolean) {
        savedState[KEY_ALWAYS] = always
    }

    override fun markUnclear(card: PayeeCard) {
        savedState[KEY_UNCLEAR] = ArrayList(unclear.value + card.key)
        decide(card.key) { repository.markUnclear(card.payee) }
    }

    override fun later(card: PayeeCard) {
        savedState[KEY_SKIPPED] = ArrayList(skipped.value + card.key)
    }

    override fun goOn() {
        if (skipped.value.isNotEmpty()) {
            savedState[KEY_SKIPPED] = ArrayList<String>()
        } else {
            savedState[KEY_BEYOND_TARGET] = true
        }
    }

    override fun endStep(step: SetupStep, skipped: Boolean) {
        viewModelScope.launch {
            if (skipped) repository.store.skip(step) else repository.store.finish(step)
        }
    }

    override fun close() {
        viewModelScope.launch { repository.store.close() }
    }

    private fun categorize(payee: SetupPayee, categoryId: Long) {
        decide(payee.counterpart.key) { repository.categorize(payee, categoryId, always.value) }
    }

    /** Hides the card of [key] at once, writes the decision and counts its time */
    private fun decide(key: String, write: suspend () -> Unit) {
        savedState[KEY_DECIDED] = ArrayList(decided.value + key)
        savedState[KEY_SKIPPED] = ArrayList(skipped.value - key)
        val millis = shown?.takeIf { it.first == key }?.let { clock() - it.second }
        viewModelScope.launch {
            write()
            millis?.let { repository.store.recordDecision(it) }
        }
    }

    private companion object {
        const val KEY_DECIDED = "setup_decided"
        const val KEY_SKIPPED = "setup_skipped"
        const val KEY_UNCLEAR = "setup_unclear"
        const val KEY_BEYOND_TARGET = "setup_beyond_target"
        const val KEY_ALWAYS = "setup_always"
        const val KEY_PENDING = "setup_pending"
    }
}
