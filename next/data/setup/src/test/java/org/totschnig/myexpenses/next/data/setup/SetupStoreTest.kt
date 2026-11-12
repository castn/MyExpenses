package org.totschnig.myexpenses.next.data.setup

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore

class SetupStoreTest {

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
        val all = SetupStep.entries
        assertEquals(SetupStep.CATEGORIES, SetupProgress().next(all))
        val skipped = SetupProgress(skipped = setOf(SetupStep.CATEGORIES))
        assertEquals(SetupStep.CONTRACTS, skipped.next(all))
        // Without contracts the skipped step is offered again
        assertEquals(SetupStep.CATEGORIES, skipped.next(listOf(SetupStep.CATEGORIES)))
        val done = SetupProgress(finished = all.toSet())
        assertEquals(null, done.next(all))
        assertEquals(emptyList<SetupStep>(), done.open(all))
    }
}
