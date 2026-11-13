package org.totschnig.myexpenses.next.feature.setup

import java.io.Serializable
import org.totschnig.myexpenses.next.data.categories.CategoryAnalysis
import org.totschnig.myexpenses.next.data.setup.CategoryCoverage
import org.totschnig.myexpenses.next.data.setup.CategoryQueue
import org.totschnig.myexpenses.next.data.setup.Pace
import org.totschnig.myexpenses.next.data.setup.SetupContracts
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
            SetupStep.CATEGORIES -> queue.required.size
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

/**
 * A payee to decide on
 *
 * @param suggestion category suggested for most of its debits, with its path, null if none; for a
 * payee the user went back to, the category chosen before
 */
data class PayeeCard(val payee: SetupPayee, val suggestion: Long?, val suggestionPath: String?) {
    val key: String get() = payee.counterpart.key
}

/**
 * What the user did in this visit of the categories step. Payees decided on ([decided]) are left
 * out at once and their money counted in, since the ledger knows their categories only a moment
 * later; payees put off ([skipped]) come last.
 *
 * @param unclear of [decided], the payees the user knows no category for: their money no longer
 * counts
 * @param beyondTarget the user wants to go on after the target
 * @param revisit the payee the user went back to, shown first
 * @param canGoBack there is a decision to go back to
 */
data class SetupVisit(
    val decided: Set<String> = emptySet(),
    val skipped: Set<String> = emptySet(),
    val unclear: Set<String> = emptySet(),
    val beyondTarget: Boolean = false,
    val revisit: Revisit? = null,
    val canGoBack: Boolean = false
)

/**
 * A payee the user went back to
 *
 * @param categoryId the category chosen before, offered again, null if none was chosen
 *
 * [Serializable] for the saved state
 */
data class Revisit(val key: String, val categoryId: Long?) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * The categories step
 *
 * @param share of the money with a category, decisions already made counted in
 * @param card the payee to decide on, null when none is left for now
 * @param left payees still to decide on up to the target
 * @param seconds [left] take at the pace of the user
 * @param isTargetReached [share] reached [TARGET_SHARE]
 * @param hasSkipped payees the user put off in this visit
 * @param hasMore payees beyond the target the user may still decide on
 * @param backlog transactions that can be categorized for sure, asked before the first card; 0 if
 * none
 * @param canGoBack see [SetupVisit.canGoBack]
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
    val canGoBack: Boolean = false
)

/** What the categories step shows after what the user did in this [visit] */
fun categoriesStep(
    queue: CategoryQueue,
    analysis: CategoryAnalysis?,
    visit: SetupVisit,
    pace: Pace,
    backlog: Int
): CategoriesStep = with(visit) {
    val amountOf = { keys: Set<String> ->
        queue.all.filter { it.counterpart.key in keys }.sumOf { it.amount }
    }
    val unclearAmount = amountOf(unclear)
    val decidedAmount = amountOf(decided - unclear)
    val coverage = queue.coverage
    val share = coverageShare(coverage, decidedAmount, unclearAmount)
    val isTargetReached = share >= TARGET_SHARE
    val open = { payees: List<SetupPayee> -> payees.filter { it.counterpart.key !in decided } }
    val required = open(queue.required)
    val left = if (isTargetReached) 0 else required.size
    val candidates = if (isTargetReached && !beyondTarget) emptyList() else open(queue.all)
    val revisited = revisit?.let { revisit ->
        open(queue.all).find { it.counterpart.key == revisit.key }
    }
    val next = revisited ?: candidates.firstOrNull { it.counterpart.key !in skipped }
    CategoriesStep(
        share = share,
        card = next?.let { payee ->
            val suggestion = revisited?.let { revisit?.categoryId }
                ?: analysis?.let { suggestionFor(payee, it.plan.suggestions) }
            PayeeCard(payee, suggestion, suggestion?.let { analysis?.paths?.get(it) })
        },
        left = left,
        seconds = pace.secondsFor(left),
        isTargetReached = isTargetReached,
        hasSkipped = candidates.any { it.counterpart.key in skipped },
        hasMore = !beyondTarget && open(queue.optional).isNotEmpty(),
        backlog = backlog,
        canGoBack = canGoBack
    )
}

private fun coverageShare(coverage: CategoryCoverage, decided: Long, unclear: Long): Double {
    val countable = coverage.countable - unclear
    return if (countable <= 0) {
        1.0
    } else {
        ((coverage.categorized + decided).toDouble() / countable).coerceAtMost(1.0)
    }
}
