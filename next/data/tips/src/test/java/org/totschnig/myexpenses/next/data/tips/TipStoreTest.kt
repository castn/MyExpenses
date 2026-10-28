package org.totschnig.myexpenses.next.data.tips

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore

class TipStoreTest {

    private val tip = SmallPurchases(count = 60, yearlyAmount = 30000, payees = emptyList())

    @Test
    fun dismissesRestoresAndMarksSeen() = withTestDataStore { dataStore ->
        val store = TipStore(dataStore)
        assertTrue(store.decisions.first().isNew(tip))
        store.markSeen(listOf(tip))
        store.dismiss(tip)
        assertFalse(store.decisions.first().isNew(tip))
        assertTrue(store.decisions.first().isDismissed(tip))
        store.restore(tip)
        assertFalse(store.decisions.first().isDismissed(tip))
    }
}
