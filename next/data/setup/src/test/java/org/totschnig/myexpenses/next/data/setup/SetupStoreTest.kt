package org.totschnig.myexpenses.next.data.setup

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore

class SetupStoreTest {

    @Test
    fun storesWhatTheUserDidWithTheCard() = withTestDataStore { dataStore ->
        val store = SetupStore(dataStore)
        val today = LocalDate.of(2026, 11, 13)
        store.markOpened()
        repeat(3) { store.dismiss(today, decisions = 4) }
        val expected = SetupReminder(
            opened = true,
            dismissals = 3,
            hiddenUntil = today.plusDays(3),
            decisionsWhenDismissed = 4
        )
        assertEquals(expected, store.progress.first().reminder)
        store.reset()
        assertEquals(SetupReminder(), store.progress.first().reminder)
    }

    @Test
    fun storesTheProgress() = withTestDataStore { dataStore ->
        val store = SetupStore(dataStore)
        assertEquals(SetupProgress(), store.progress.first())
        store.skip(SetupStep.CATEGORIES)
        store.finish(SetupStep.CONTRACTS)
        store.recordDecision(5000)
        assertEquals(
            SetupProgress(
                finished = setOf(SetupStep.CONTRACTS),
                skipped = setOf(SetupStep.CATEGORIES),
                pace = Pace(1, 5000)
            ),
            store.progress.first()
        )
        // A skipped step that is finished later is no longer skipped
        store.finish(SetupStep.CATEGORIES)
        store.close()
        val progress = store.progress.first()
        assertEquals(emptySet<SetupStep>(), progress.skipped)
        assertEquals(true, progress.closed)
        store.reset()
        assertEquals(SetupProgress(pace = Pace(1, 5000)), store.progress.first())
    }

    @Test
    fun goesOnWithTheFirstStepNotSkipped() {
        val all = listOf(SetupStep.CATEGORIES, SetupStep.CONTRACTS)
        assertEquals(SetupStep.CATEGORIES, SetupProgress().next(all))
        // The income step comes first when there is one
        assertEquals(SetupStep.INCOME, SetupProgress().next(SetupStep.entries))
        val skipped = SetupProgress(skipped = setOf(SetupStep.CATEGORIES))
        assertEquals(SetupStep.CONTRACTS, skipped.next(all))
        // Without contracts the skipped step is offered again
        assertEquals(SetupStep.CATEGORIES, skipped.next(listOf(SetupStep.CATEGORIES)))
        val done = SetupProgress(finished = all.toSet())
        assertEquals(null, done.next(all))
        assertEquals(emptyList<SetupStep>(), done.open(all))
    }
}
