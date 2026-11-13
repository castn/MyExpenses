package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate

/** How the overview offers the setup */
enum class SetupOffer {
    /** Not at all: closed, nothing to decide, or put away for now */
    HIDDEN,

    /** A small card above the rest of the overview, after the setup was opened once */
    COMPACT,

    /** The card that takes the overview over, until the setup was opened once */
    FULL
}

/**
 * What the user did with the card of the setup on the overview
 *
 * @param opened the setup was opened once, from then on its card is small
 * @param dismissals how often the small card was put away
 * @param hiddenUntil it stays away before this day, null if only until the next start of the app
 * @param decisionsWhenDismissed the decisions open when it was put away last: more of them, e.g.
 * after an import, bring it back earlier
 */
data class SetupReminder(
    val opened: Boolean = false,
    val dismissals: Int = 0,
    val hiddenUntil: LocalDate? = null,
    val decisionsWhenDismissed: Int = 0
) {
    /** Putting the card away once more asks whether it should not come again at all */
    val asksToStop: Boolean get() = dismissals >= START_DISMISSALS + PAUSE_DAYS.size

    /**
     * The card was put away on [today] with [decisions] open: the first [START_DISMISSALS] times
     * until the next start of the app, then for the days of [PAUSE_DAYS], the last one again after
     */
    fun dismissed(today: LocalDate, decisions: Int): SetupReminder {
        val count = dismissals + 1
        val pause = PAUSE_DAYS.getOrNull(count - START_DISMISSALS - 1) ?: PAUSE_DAYS.last()
        return copy(
            dismissals = count,
            hiddenUntil = if (count <= START_DISMISSALS) null else today.plusDays(pause),
            decisionsWhenDismissed = decisions
        )
    }

    companion object {
        /** Put away this often, the card comes again with the next start of the app */
        const val START_DISMISSALS = 2

        /** Then it stays away for these days, one after the other */
        val PAUSE_DAYS = listOf(3L, 7L, 14L)
    }
}

/**
 * How the overview offers the setup with [decisions] open in steps not done yet
 *
 * @param dismissedThisStart the card was put away since the app started
 */
fun SetupProgress.offer(decisions: Int, today: LocalDate, dismissedThisStart: Boolean): SetupOffer {
    val hasNews = decisions > reminder.decisionsWhenDismissed
    val isPaused = dismissedThisStart || reminder.hiddenUntil?.let { today.isBefore(it) } == true
    return when {
        closed || decisions == 0 -> SetupOffer.HIDDEN
        !reminder.opened -> SetupOffer.FULL
        isPaused && !hasNews -> SetupOffer.HIDDEN
        else -> SetupOffer.COMPACT
    }
}
