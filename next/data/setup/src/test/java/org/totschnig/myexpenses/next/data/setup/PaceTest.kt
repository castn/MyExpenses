package org.totschnig.myexpenses.next.data.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class PaceTest {

    @Test
    fun estimatesWithThePlaceholderUntilEnoughIsMeasured() {
        val pace = Pace().with(2000).with(2000)
        assertEquals(Pace.DEFAULT_SECONDS, pace.secondsPerDecision, 0.0)
        assertEquals(80L, pace.secondsFor(10))
    }

    @Test
    fun followsTheMeasuredPace() {
        val pace = Pace().with(3000).with(4000).with(5000)
        assertEquals(4.0, pace.secondsPerDecision, 0.0)
        assertEquals(40L, pace.secondsFor(10))
        assertEquals(0L, pace.secondsFor(0))
    }

    @Test
    fun aLongPauseCountsAsAMinuteAtMost() {
        val pace = Pace().with(Pace.MAX_MILLIS * 10).with(-5)
        assertEquals(Pace(decisions = 2, millis = Pace.MAX_MILLIS), pace)
    }
}
