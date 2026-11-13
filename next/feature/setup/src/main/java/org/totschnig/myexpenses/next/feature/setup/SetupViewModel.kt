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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.setup.AutomaticShare
import org.totschnig.myexpenses.next.data.setup.SetupDecision
import org.totschnig.myexpenses.next.data.setup.SetupPayee
import org.totschnig.myexpenses.next.data.setup.SetupRepository
import org.totschnig.myexpenses.next.data.setup.SetupStep

/**
 * The pages of the setup, see [SetupFlow]. What the user did in this visit, the payees decided on
 * and put off, the decisions to go back to and the payee waiting for the category picker, is kept
 * in [savedState].
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
    private val revisit = savedState.getStateFlow<Revisit?>(KEY_REVISIT, null)

    /** The decisions of this visit, the last one last, see [back] */
    private val history = savedState.getStateFlow(KEY_HISTORY, ArrayList<SetupDecision>())

    /** Writes and takes back decisions one after the other, so that [back] finds the last one */
    private val writes = Mutex()

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

    private val visit = combine(
        combine(decided, skipped, unclear) { d, s, u -> Triple(d.toSet(), s.toSet(), u.toSet()) },
        beyondTarget,
        revisit,
        history
    ) { (d, s, u), beyond, revisit, history ->
        SetupVisit(d, s, u, beyond, revisit, canGoBack = history.isNotEmpty())
    }

    /** Null while loading */
    val categories: StateFlow<CategoriesStep?> = combine(
        repository.categoryQueue.filterNotNull(),
        repository.analysis,
        visit,
        repository.progress.map { it.pace },
        repository.backlog,
        ::categoriesStep
    ).stateWhileShown(viewModelScope, null)

    /** The payees to decide on, to look up the transactions of one, null while loading */
    val payees: StateFlow<List<SetupPayee>?> =
        repository.categoryQueue.map { it?.all }.stateWhileShown(viewModelScope, null)

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

    override fun pickCategory(card: PayeeCard, transactionIds: List<Long>?) {
        savedState[KEY_PENDING] = card.key
        if (transactionIds != null) {
            savedState[KEY_PENDING_IDS] = transactionIds.toLongArray()
        } else {
            savedState.remove<LongArray>(KEY_PENDING_IDS)
        }
    }

    override fun picked(categoryId: Long?) {
        val key = savedState.get<String>(KEY_PENDING) ?: return
        val ids = savedState.get<LongArray>(KEY_PENDING_IDS)
        savedState.remove<String>(KEY_PENDING)
        savedState.remove<LongArray>(KEY_PENDING_IDS)
        if (categoryId == null) return
        viewModelScope.launch {
            val queue = repository.categoryQueue.filterNotNull().first()
            val payee = queue.all.find { it.counterpart.key == key } ?: return@launch
            if (ids == null) {
                categorize(payee, categoryId)
            } else {
                assign(payee, ids.toList(), categoryId)
            }
        }
    }

    override fun setAlways(always: Boolean) {
        savedState[KEY_ALWAYS] = always
    }

    override fun leave(card: PayeeCard, unclear: Boolean) {
        if (unclear) {
            savedState[KEY_UNCLEAR] = ArrayList(this.unclear.value + card.key)
            decide(card.key) { repository.markUnclear(card.payee) }
        } else {
            savedState[KEY_SKIPPED] = ArrayList(skipped.value + card.key)
            savedState.remove<Revisit>(KEY_REVISIT)
            viewModelScope.launch {
                writes.withLock { remember(SetupDecision.Later(card.key)) }
            }
        }
    }

    override fun back() {
        viewModelScope.launch {
            writes.withLock {
                val last = history.value.lastOrNull() ?: return@withLock
                val key = last.key
                val categoryId = (last as? SetupDecision.Categorized)?.categorization?.categoryId
                // The card comes back with the decided payees, so all else is set before
                savedState[KEY_REVISIT] = Revisit(key, categoryId)
                savedState[KEY_HISTORY] = ArrayList(history.value.dropLast(1))
                savedState[KEY_SKIPPED] = ArrayList(skipped.value - key)
                savedState[KEY_UNCLEAR] = ArrayList(unclear.value - key)
                savedState[KEY_DECIDED] = ArrayList(decided.value - key)
                repository.takeBack(last)
            }
        }
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

    /**
     * The open debits of [ids] get [categoryId], without a rule; the card of the payee stays for
     * the others. No time is counted, it is not a decision on the whole payee.
     */
    private fun assign(payee: SetupPayee, ids: List<Long>, categoryId: Long) {
        val open = ids.filter { it in payee.transactionIds }
        if (open.isEmpty()) return
        viewModelScope.launch {
            writes.withLock { remember(repository.assign(payee, open, categoryId)) }
        }
    }

    /**
     * Hides the card of [key] at once, writes the decision and counts its time, unless the user
     * went back to the payee and decides a second time
     */
    private fun decide(key: String, write: suspend () -> SetupDecision) {
        savedState[KEY_DECIDED] = ArrayList(decided.value + key)
        savedState[KEY_SKIPPED] = ArrayList(skipped.value - key)
        val isRevisit = revisit.value?.key == key
        savedState.remove<Revisit>(KEY_REVISIT)
        val millis = shown?.takeIf { it.first == key && !isRevisit }?.let { clock() - it.second }
        viewModelScope.launch {
            writes.withLock { remember(write()) }
            millis?.let { repository.store.recordDecision(it) }
        }
    }

    private fun remember(decision: SetupDecision) {
        savedState[KEY_HISTORY] = ArrayList(history.value.takeLast(MAX_HISTORY - 1) + decision)
    }

    private companion object {
        const val KEY_DECIDED = "setup_decided"
        const val KEY_SKIPPED = "setup_skipped"
        const val KEY_UNCLEAR = "setup_unclear"
        const val KEY_BEYOND_TARGET = "setup_beyond_target"
        const val KEY_REVISIT = "setup_revisit"
        const val KEY_HISTORY = "setup_history"
        const val KEY_ALWAYS = "setup_always"
        const val KEY_PENDING = "setup_pending"
        const val KEY_PENDING_IDS = "setup_pending_ids"

        /** Decisions kept to go back to, so that the saved state stays small */
        const val MAX_HISTORY = 30
    }
}
