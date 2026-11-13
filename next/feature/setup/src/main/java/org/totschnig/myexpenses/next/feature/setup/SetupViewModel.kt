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
import org.totschnig.myexpenses.next.data.setup.SetupCash
import org.totschnig.myexpenses.next.data.setup.SetupDecision
import org.totschnig.myexpenses.next.data.setup.SetupOffer
import org.totschnig.myexpenses.next.data.setup.SetupPayee
import org.totschnig.myexpenses.next.data.setup.SetupReminder
import org.totschnig.myexpenses.next.data.setup.SetupRepository
import org.totschnig.myexpenses.next.data.setup.SetupStep
import org.totschnig.myexpenses.next.data.setup.offer

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
    SetupActions,
    PayeeCardActions,
    CashActions,
    SetupCardActions {

    private val decided = savedState.getStateFlow(KEY_DECIDED, ArrayList<String>())
    private val skipped = savedState.getStateFlow(KEY_SKIPPED, ArrayList<String>())
    private val unclear = savedState.getStateFlow(KEY_UNCLEAR, ArrayList<String>())
    private val beyondTarget = savedState.getStateFlow(KEY_BEYOND_TARGET, false)
    private val revisit = savedState.getStateFlow<Revisit?>(KEY_REVISIT, null)
    private val cashCategory = savedState.getStateFlow<Long?>(KEY_CASH_CATEGORY, null)
    private val draft = savedState.getStateFlow<PayeeDraft?>(KEY_DRAFT, null)
    private val cashDraft = savedState.getStateFlow<CashDraft?>(KEY_CASH_DRAFT, null)

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
     * How the overview offers the setup, see [offer]: not to users whose transactions are described
     * already, since only decisions in steps not done count
     */
    val offer: StateFlow<SetupOffer> = combine(
        repository.progress,
        overview,
        repository.today,
        repository.dismissedThisStart
    ) { progress, overview, today, dismissedThisStart ->
        progress.offer(openDecisions(overview), today, dismissedThisStart)
    }.stateWhileShown(viewModelScope, SetupOffer.HIDDEN)

    /** Putting the card away once more asks whether it should not come again, see [SetupReminder.asksToStop] */
    val asksToStop: StateFlow<Boolean> = repository.progress.map { it.reminder.asksToStop }
        .stateWhileShown(viewModelScope, false)

    private val visit = combine(
        combine(decided, skipped, unclear) { d, s, u -> Triple(d.toSet(), s.toSet(), u.toSet()) },
        beyondTarget,
        combine(revisit, draft, ::Pair),
        history,
        combine(cashCategory, cashDraft, ::Pair)
    ) { (d, s, u), beyond, (revisit, draft), history, (cashCategory, cashDraft) ->
        SetupVisit(d, s, u, beyond, revisit, history.lastOrNull(), cashCategory, draft, cashDraft)
    }

    /** Null while loading */
    val categories: StateFlow<CategoriesStep?> = combine(
        repository.categoryQueue.filterNotNull(),
        repository.analysis,
        visit,
        repository.progress.map { it.pace },
        combine(repository.backlog, repository.canOpenCashAccount, ::Pair)
    ) { queue, analysis, visit, pace, (backlog, canOpenCashAccount) ->
        categoriesStep(queue, analysis, visit, pace, backlog, canOpenCashAccount)
    }.stateWhileShown(viewModelScope, null)

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
        when {
            categoryId == null -> Unit

            // For the cash withdrawals and the card the choice only fills them in until the user
            // confirms it
            key == SetupCash.KEY -> savedState[KEY_CASH_CATEGORY] = categoryId

            ids == null -> savedState[KEY_DRAFT] = PayeeDraft(key, PayeeAnswer.CATEGORY, categoryId)

            else -> viewModelScope.launch {
                val queue = repository.categoryQueue.filterNotNull().first()
                val payee = queue.all.find { it.counterpart.key == key } ?: return@launch
                assign(payee, ids.toList(), categoryId)
            }
        }
    }

    override fun answer(card: PayeeCard, answer: PayeeAnswer) {
        val current = draft.value?.takeIf { it.key == card.key }
        savedState[KEY_DRAFT] = PayeeDraft(card.key, answer, current?.categoryId)
    }

    override fun takeSuggestion(card: PayeeCard) {
        card.offer?.let { savedState[KEY_DRAFT] = PayeeDraft(card.key, card.answer, it) }
    }

    override fun setAlways(always: Boolean) {
        savedState[KEY_ALWAYS] = always
    }

    override fun confirm(card: PayeeCard) {
        when (card.answer) {
            PayeeAnswer.CATEGORY -> card.categoryId?.let { categorize(card.payee, it) }

            PayeeAnswer.UNCLEAR -> {
                savedState[KEY_UNCLEAR] = ArrayList(unclear.value + card.key)
                decide(card.key) { repository.markUnclear(card.payee) }
            }

            // The page of its debits is opened, which decides on them one by one
            PayeeAnswer.DIFFERS -> Unit
        }
    }

    override fun later(card: PayeeCard) {
        putOff(card.key)
    }

    override fun pickCashCategory() {
        savedState[KEY_PENDING] = SetupCash.KEY
        savedState.remove<LongArray>(KEY_PENDING_IDS)
    }

    override fun keepCash(card: CashCard, balance: Long?) {
        val categoryId = card.categoryId ?: return
        savedState[KEY_CASH_CATEGORY] = null
        savedState[KEY_CASH_DRAFT] = null
        decide(SetupCash.KEY) { repository.keepCash(card.cash, categoryId, balance) }
    }

    override fun takeCashSuggestion(card: CashCard) {
        card.offer?.let { savedState[KEY_CASH_CATEGORY] = it }
    }

    override fun chooseCashAccount(toAccount: Boolean) {
        savedState[KEY_CASH_DRAFT] = (cashDraft.value ?: CashDraft()).copy(toAccount = toAccount)
    }

    override fun setCashBalance(text: String) {
        savedState[KEY_CASH_DRAFT] = (cashDraft.value ?: CashDraft()).copy(balanceText = text)
    }

    override fun leaveCash() {
        savedState[KEY_CASH_DRAFT] = null
        putOff(SetupCash.KEY)
    }

    /** [key] comes again after the others ("Später"), nothing is written */
    private fun putOff(key: String) {
        savedState[KEY_SKIPPED] = ArrayList(skipped.value + key)
        savedState[KEY_REVISIT] = null
        savedState[KEY_DRAFT] = null
        viewModelScope.launch {
            writes.withLock { remember(SetupDecision.Later(key)) }
        }
    }

    override fun back() {
        viewModelScope.launch {
            writes.withLock {
                val last = history.value.lastOrNull() ?: return@withLock
                val key = last.key
                val categoryId = when (last) {
                    is SetupDecision.Categorized -> last.categorization.categoryId
                    is SetupDecision.Cash -> last.categorization.categoryId
                    else -> null
                }
                // The card comes back with the decided payees, so all else is set before
                savedState[KEY_REVISIT] = Revisit(key, categoryId, last is SetupDecision.Unclear)
                savedState[KEY_DRAFT] = null
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

    override fun opened() {
        viewModelScope.launch { repository.store.markOpened() }
    }

    override fun dismissCard() {
        viewModelScope.launch { repository.dismiss(openDecisions(overview.value)) }
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
        savedState[KEY_REVISIT] = null
        savedState[KEY_DRAFT] = null
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
        const val KEY_CASH_CATEGORY = "setup_cash_category"
        const val KEY_DRAFT = "setup_draft"
        const val KEY_CASH_DRAFT = "setup_cash_draft"

        /** Decisions kept to go back to, so that the saved state stays small */
        const val MAX_HISTORY = 30
    }
}
