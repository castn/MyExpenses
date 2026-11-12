package org.totschnig.myexpenses.next.data.setup

import kotlin.math.ceil

/**
 * How fast the user decides in the guided setup, to estimate the time a step takes. Until
 * [MIN_DECISIONS] decisions are measured, [DEFAULT_SECONDS] stands for a decision.
 *
 * @param decisions measured so far
 * @param millis taken by them together
 */
data class Pace(val decisions: Int = 0, val millis: Long = 0) {

    /** Seconds a decision takes, measured or [DEFAULT_SECONDS] */
    val secondsPerDecision: Double
        get() = if (decisions >= MIN_DECISIONS) {
            millis / MILLIS_PER_SECOND / decisions
        } else {
            DEFAULT_SECONDS
        }

    /** Seconds [count] decisions take, rounded up */
    fun secondsFor(count: Int): Long = ceil(count * secondsPerDecision).toLong()

    /**
     * With one more decision that took [millis]. A decision counts at most [MAX_MILLIS], the user may
     * have put the phone aside.
     */
    fun with(millis: Long) = Pace(decisions + 1, this.millis + millis.coerceIn(0, MAX_MILLIS))

    companion object {
        /** Placeholder until the user measured how long a decision takes by hand */
        const val DEFAULT_SECONDS = 8.0

        /** From this many decisions on, the measured pace counts */
        const val MIN_DECISIONS = 3

        /** A decision counts at most this long */
        const val MAX_MILLIS = 60_000L

        private const val MILLIS_PER_SECOND = 1000.0
    }
}
