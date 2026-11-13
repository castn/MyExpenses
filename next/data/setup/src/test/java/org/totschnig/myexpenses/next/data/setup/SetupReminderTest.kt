package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupReminderTest {

    private val today = LocalDate.of(2026, 11, 13)

    private fun SetupReminder.offer(
        decisions: Int = 5,
        on: LocalDate = today,
        dismissedThisStart: Boolean = false
    ) = SetupProgress(reminder = this).offer(decisions, on, dismissedThisStart)

    @Test
    fun takesTheOverviewOverUntilOpenedOnce() {
        assertEquals(SetupOffer.FULL, SetupReminder().offer())
        assertEquals(SetupOffer.COMPACT, SetupReminder(opened = true).offer())
        assertEquals(SetupOffer.HIDDEN, SetupReminder().offer(decisions = 0))
        val closed = SetupProgress(closed = true).offer(5, today, dismissedThisStart = false)
        assertEquals(SetupOffer.HIDDEN, closed)
    }

    @Test
    fun comesBackWithTheNextStartTwiceThenAfterSomeDays() {
        var reminder = SetupReminder(opened = true)
        repeat(SetupReminder.START_DISMISSALS) {
            reminder = reminder.dismissed(today, 5)
            assertEquals(null, reminder.hiddenUntil)
            assertEquals(SetupOffer.HIDDEN, reminder.offer(dismissedThisStart = true))
            assertEquals(SetupOffer.COMPACT, reminder.offer())
        }
        val pauses = listOf(3L, 7L, 14L, 14L).map { days ->
            reminder = reminder.dismissed(today, 5)
            assertEquals(SetupOffer.HIDDEN, reminder.offer(on = today.plusDays(days - 1)))
            assertEquals(SetupOffer.COMPACT, reminder.offer(on = today.plusDays(days)))
            reminder.hiddenUntil
        }
        assertEquals(listOf(3L, 7L, 14L, 14L).map { today.plusDays(it) }, pauses)
    }

    @Test
    fun asksToStopAfterTheLongestPause() {
        var reminder = SetupReminder(opened = true)
        repeat(4) { reminder = reminder.dismissed(today, 5) }
        assertFalse(reminder.asksToStop)
        reminder = reminder.dismissed(today, 5)
        assertTrue(reminder.asksToStop)
    }

    @Test
    fun comesBackEarlierWithMoreToDecide() {
        val reminder = SetupReminder(opened = true).dismissed(today, 5).dismissed(today, 5)
            .dismissed(today, 5)
        assertEquals(SetupOffer.HIDDEN, reminder.offer(decisions = 5))
        assertEquals(SetupOffer.HIDDEN, reminder.offer(decisions = 3))
        assertEquals(SetupOffer.COMPACT, reminder.offer(decisions = 6))
    }
}
