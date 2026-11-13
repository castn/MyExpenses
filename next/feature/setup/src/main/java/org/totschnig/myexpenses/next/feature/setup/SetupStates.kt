package org.totschnig.myexpenses.next.feature.setup

import java.io.Serializable
import org.totschnig.myexpenses.next.data.categories.CategoryAnalysis
import org.totschnig.myexpenses.next.data.setup.CategoryCoverage
import org.totschnig.myexpenses.next.data.setup.CategoryQueue
import org.totschnig.myexpenses.next.data.setup.Pace
import org.totschnig.myexpenses.next.data.setup.SetupCash
import org.totschnig.myexpenses.next.data.setup.SetupContracts
import org.totschnig.myexpenses.next.data.setup.SetupDecision
import org.totschnig.myexpenses.next.data.setup.SetupPayee
import org.totschnig.myexpenses.next.data.setup.SetupProgress
import org.totschnig.myexpenses.next.data.setup.SetupStep
import org.totschnig.myexpenses.next.data.setup.TARGET_SHARE
import org.totschnig.myexpenses.next.data.setup.suggestionFor

/** Where a step of the setup is */
enum class StepState { OPEN, CURRENT, DONE, SKIPPED }

/**
 * A step in the overview of the setup
 *
 * @param decisions still to make, e.g. payees up to the target
 * @param seconds the [decisions] take at the pace of the user, see [Pace]
 */
data class StepInfo(
    val step: SetupStep,
    val state: StepState,
    val decisions: Int,
    val seconds: Long
)

/**
 * The overview of the setup
 *
 * @param next the step to go on with, null when all are done
 * @param seconds all open steps take
 */
data class SetupOverview(val steps: List<StepInfo>, val next: SetupStep?, val seconds: Long) {
    /** A step was gone through or skipped, so the setup is resumed rather than started */
    val hasStarted: Boolean
        get() = steps.any { it.state == StepState.DONE || it.state == StepState.SKIPPED }
}

/**
 * The steps there are with what they take
 *
 * @param available the steps there are, see
 * [org.totschnig.myexpenses.next.data.setup.SetupRepository.steps]
 */
fun setupOverview(
    available: List<SetupStep>,
    progress: SetupProgress,
    queue: CategoryQueue,
    contracts: SetupContracts
): SetupOverview {
    val next = progress.next(available)
    val steps = available.map { step ->
        val decisions = when (step) {
            SetupStep.INCOME -> (contracts as? SetupContracts.Available)?.incomeDecisions ?: 0
            SetupStep.CATEGORIES -> queue.required.size + if (queue.isCashRequired) 1 else 0
            SetupStep.CONTRACTS -> (contracts as? SetupContracts.Available)?.suggestions ?: 0
        }
        val state = when {
            step in progress.finished -> StepState.DONE
            step == next -> StepState.CURRENT
            step in progress.skipped -> StepState.SKIPPED
            else -> StepState.OPEN
        }
        val open = if (state == StepState.DONE) 0 else decisions
        StepInfo(step, state, open, progress.pace.secondsFor(open))
    }
    return SetupOverview(steps, next, steps.sumOf { it.seconds })
}

/** What the user answers about the debits of a payee, before confirming it with "Next" */
enum class PayeeAnswer {
    /** They all get one category */
    CATEGORY,

    /** They are for different things and get their categories one by one ("Unterschiedlich") */
    DIFFERS,

    /** The user knows no category for the payee ("Weiß ich nicht") */
    UNCLEAR
}

/**
 * The answer chosen on the card of the payee [key], not confirmed yet. [Serializable] for the
 * saved state.
 *
 * @param categoryId picked or taken from a suggestion; null keeps what the card fills in
 */
data class PayeeDraft(
    val key: String,
    val answer: PayeeAnswer = PayeeAnswer.CATEGORY,
    val categoryId: Long? = null
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * A payee to decide on: the answer chosen and, for [PayeeAnswer.CATEGORY], the category that "Next"
 * gives its debits
 *
 * @param categoryId picked by the user, chosen before for a payee the user went back to, or a
 * certain suggestion; null if none, then "Next" waits for one
 * @param isSuggested [categoryId] is the certain suggestion, not changed by the user
 * @param offer an uncertain suggestion, only offered to take while there is no [categoryId]
 */
data class PayeeCard(
    val payee: SetupPayee,
    val answer: PayeeAnswer = PayeeAnswer.CATEGORY,
    val categoryId: Long? = null,
    val categoryPath: String? = null,
    val isSuggested: Boolean = false,
    val offer: Long? = null,
    val offerPath: String? = null
) {
    val key: String get() = payee.counterpart.key

    /** "Next" can confirm the answer: any but [PayeeAnswer.CATEGORY] needs nothing more */
    val canConfirm: Boolean get() = answer != PayeeAnswer.CATEGORY || categoryId != null
}

/**
 * The cash withdrawals to decide on, see [SetupCash]
 *
 * @param categoryId for the withdrawals so far: the one the user picked, else the one chosen before
 * for a decision the user went back to, else the one suggested; null if none, with its path
 * @param canOpenAccount a cash account can be created for them, see
 * [org.totschnig.myexpenses.next.data.setup.SetupRepository.canOpenCashAccount]
 */
data class CashCard(
    val cash: SetupCash,
    val categoryId: Long?,
    val categoryPath: String?,
    val canOpenAccount: Boolean
)

/**
 * What the user did in this visit of the categories step. Payees decided on ([decided]) are left
 * out at once and their money counted in, since the ledger knows their categories only a moment
 * later; payees put off ([skipped]) come last. The cash withdrawals take part under
 * [SetupCash.KEY].
 *
 * @param unclear of [decided], the payees the user knows no category for: their money no longer
 * counts
 * @param beyondTarget the user wants to go on after the target
 * @param revisit the payee the user went back to, shown first
 * @param last the decision to go back to, null if there is none
 * @param cashCategory the category the user picked for the cash withdrawals, not yet decided on
 * @param draft the answer chosen on the card shown, not confirmed yet
 */
data class SetupVisit(
    val decided: Set<String> = emptySet(),
    val skipped: Set<String> = emptySet(),
    val unclear: Set<String> = emptySet(),
    val beyondTarget: Boolean = false,
    val revisit: Revisit? = null,
    val last: SetupDecision? = null,
    val cashCategory: Long? = null,
    val draft: PayeeDraft? = null
)

/**
 * A payee the user went back to
 *
 * @param categoryId the category chosen before, filled in again, null if none was chosen
 * @param isUnclear the user knew no category for it before
 *
 * [Serializable] for the saved state
 */
data class Revisit(val key: String, val categoryId: Long?, val isUnclear: Boolean = false) :
    Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * The categories step
 *
 * @param share of the money with a category, decisions already made counted in
 * @param card the payee to decide on, null when none is left for now or [cashCard] is shown
 * @param cashCard the cash withdrawals to decide on instead of a payee, see [CashCard]
 * @param left payees still to decide on up to the target, the cash withdrawals counted as one
 * @param seconds [left] take at the pace of the user
 * @param isTargetReached [share] reached [TARGET_SHARE]
 * @param hasSkipped payees the user put off in this visit
 * @param hasMore payees beyond the target the user may still decide on
 * @param backlog transactions that can be categorized for sure, asked before the first card; 0 if
 * none
 * @param last what was decided last, to tell it and to go back to it, null if nothing
 */
data class CategoriesStep(
    val share: Double,
    val card: PayeeCard?,
    val left: Int,
    val seconds: Long,
    val isTargetReached: Boolean,
    val hasSkipped: Boolean,
    val hasMore: Boolean,
    val backlog: Int,
    val last: LastDecision? = null,
    val cashCard: CashCard? = null
) {
    val canGoBack: Boolean get() = last != null
}

/** What a decision of the setup was, for the line "Last: …" */
enum class LastKind { CATEGORY, SOME, UNCLEAR, LATER, CASH, CASH_ACCOUNT }

/**
 * The decision last made in this visit, see [SetupVisit.last]
 *
 * @param name of the payee, null for the cash withdrawals or if it is no longer known
 * @param categoryPath the category it gave, null if none
 */
data class LastDecision(val kind: LastKind, val name: String?, val categoryPath: String?)

/**
 * What the categories step shows after what the user did in this [visit]. The cash withdrawals come
 * where their money puts them among the payees.
 *
 * @param canOpenCashAccount see [CashCard.canOpenAccount]
 */
fun categoriesStep(
    queue: CategoryQueue,
    analysis: CategoryAnalysis?,
    visit: SetupVisit,
    pace: Pace,
    backlog: Int,
    canOpenCashAccount: Boolean = false
): CategoriesStep = with(visit) {
    val share =
        coverageShare(queue.coverage, queue.amountOf(decided - unclear), queue.amountOf(unclear))
    val isTargetReached = share >= TARGET_SHARE
    val open = { payees: List<SetupPayee> -> payees.filter { it.counterpart.key !in decided } }
    val openCash = queue.cash?.takeIf { SetupCash.KEY !in decided }
    val left = if (isTargetReached) 0 else queue.requiredLeft(decided)
    val isClosed = isTargetReached && !beyondTarget
    val candidates = if (isClosed) emptyList() else open(queue.all)
    val revisited = revisit?.let { revisit ->
        open(queue.all).find { it.counterpart.key == revisit.key }
    }
    val next = revisited ?: candidates.firstOrNull { it.counterpart.key !in skipped }
    val isCashNext = isCashNext(openCash, isClosed, revisited, next)
    val cashCandidates = listOfNotNull(openCash?.takeIf { !isClosed }).map { SetupCash.KEY }
    CategoriesStep(
        share = share,
        card = next?.takeIf { !isCashNext }?.let { payeeCard(it, it == revisited, analysis) },
        left = left,
        seconds = pace.secondsFor(left),
        isTargetReached = isTargetReached,
        hasSkipped = (candidates.map { it.counterpart.key } + cashCandidates).any { it in skipped },
        hasMore = !beyondTarget && queue.hasOptionalLeft(decided),
        backlog = backlog,
        last = lastDecision(queue, analysis),
        cashCard = openCash?.takeIf { isCashNext }?.let {
            cashCard(it, analysis, canOpenCashAccount)
        }
    )
}

/** The payees up to the target not decided on, the cash withdrawals counted as one */
private fun CategoryQueue.requiredLeft(decided: Set<String>): Int =
    required.count { it.counterpart.key !in decided } +
        if (isCashRequired && cash != null && SetupCash.KEY !in decided) 1 else 0

/** Whether payees or cash withdrawals beyond the target are not decided on */
private fun CategoryQueue.hasOptionalLeft(decided: Set<String>): Boolean =
    optional.any { it.counterpart.key !in decided } ||
        (cash != null && !isCashRequired && SetupCash.KEY !in decided)

/** The money of the payees of [keys], and of the cash withdrawals with [SetupCash.KEY] */
private fun CategoryQueue.amountOf(keys: Set<String>): Long =
    all.filter { it.counterpart.key in keys }.sumOf { it.amount } +
        (cash?.takeIf { SetupCash.KEY in keys }?.amount ?: 0)

/**
 * Whether the open cash withdrawals [cash] come before the payee [next]: when gone back to, else
 * if they are not put off and their money is more
 */
private fun SetupVisit.isCashNext(
    cash: SetupCash?,
    isClosed: Boolean,
    revisited: SetupPayee?,
    next: SetupPayee?
): Boolean = when {
    cash == null -> false
    revisit?.key == SetupCash.KEY -> true
    revisited != null || isClosed || SetupCash.KEY in skipped -> false
    else -> cash.amount >= (next?.amount ?: 0)
}

/**
 * The card of [payee] as the user left it ([SetupVisit.draft]), else filled in: with the category
 * chosen before for a payee gone back to, else with a certain suggestion, while an uncertain one
 * is only offered (variant C)
 */
private fun SetupVisit.payeeCard(
    payee: SetupPayee,
    isRevisited: Boolean,
    analysis: CategoryAnalysis?
): PayeeCard {
    val key = payee.counterpart.key
    val draft = draft?.takeIf { it.key == key }
    val before = revisit?.takeIf { isRevisited }
    val suggestion = analysis?.let { suggestionFor(payee, it.plan.suggestions) }
    val filledIn = before?.categoryId ?: suggestion?.takeIf { it.isCertain }?.categoryId
    val categoryId = draft?.categoryId ?: filledIn
    val offer = suggestion?.categoryId?.takeIf { categoryId == null }
    val path = { id: Long? -> id?.let { analysis?.paths?.get(it) } }
    return PayeeCard(
        payee = payee,
        answer = draft?.answer
            ?: if (before?.isUnclear == true) PayeeAnswer.UNCLEAR else PayeeAnswer.CATEGORY,
        categoryId = categoryId,
        categoryPath = path(categoryId),
        isSuggested = before == null && draft?.categoryId == null && categoryId != null,
        offer = offer,
        offerPath = path(offer)
    )
}

/** What [SetupVisit.last] was, with the name of its payee and the path of its category */
private fun SetupVisit.lastDecision(
    queue: CategoryQueue,
    analysis: CategoryAnalysis?
): LastDecision? {
    val decision = last ?: return null
    val path = { id: Long -> analysis?.paths?.get(id) }
    return when (decision) {
        is SetupDecision.Categorized -> decision.categorization.let {
            LastDecision(LastKind.CATEGORY, it.counterpart.name, path(it.categoryId))
        }

        is SetupDecision.Assigned -> decision.categorization.let {
            LastDecision(LastKind.SOME, it.counterpart.name, path(it.categoryId))
        }

        is SetupDecision.Unclear ->
            LastDecision(LastKind.UNCLEAR, decision.counterpart.name, null)

        is SetupDecision.Later -> LastDecision(
            if (decision.key == SetupCash.KEY) LastKind.CASH else LastKind.LATER,
            queue.all.find { it.counterpart.key == decision.key }?.counterpart?.name,
            null
        )

        is SetupDecision.Cash -> LastDecision(
            if (decision.hasAccount) LastKind.CASH_ACCOUNT else LastKind.CASH,
            null,
            path(decision.categorization.categoryId)
        )
    }
}

private fun SetupVisit.cashCard(
    cash: SetupCash,
    analysis: CategoryAnalysis?,
    canOpenAccount: Boolean
): CashCard {
    val categoryId = cashCategory
        ?: revisit?.takeIf { it.key == SetupCash.KEY }?.categoryId
        ?: analysis?.let { suggestionFor(cash, it.plan.suggestions, it.categories)?.categoryId }
    return CashCard(cash, categoryId, categoryId?.let { analysis?.paths?.get(it) }, canOpenAccount)
}

private fun coverageShare(coverage: CategoryCoverage, decided: Long, unclear: Long): Double {
    val countable = coverage.countable - unclear
    return if (countable <= 0) {
        1.0
    } else {
        ((coverage.categorized + decided).toDouble() / countable).coerceAtMost(1.0)
    }
}

/** The decisions in the steps not done yet, 0 while loading */
fun openDecisions(overview: SetupOverview?): Int =
    overview?.steps.orEmpty().filter { it.state != StepState.DONE }.sumOf { it.decisions }
