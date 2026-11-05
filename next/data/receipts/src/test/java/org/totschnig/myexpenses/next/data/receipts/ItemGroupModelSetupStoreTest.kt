package org.totschnig.myexpenses.next.data.receipts

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelSetupStore.Companion.UPDATE_INTERVAL
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelSetupStore.Source

class ItemGroupModelSetupStoreTest {

    @Test
    fun keepsDecline() = withTestDataStore { dataStore ->
        val store = ItemGroupModelSetupStore(dataStore)
        assertFalse(store.declined.first())
        store.decline()
        assertTrue(ItemGroupModelSetupStore(dataStore).declined.first())
    }

    @Test
    fun keepsSourceOfLastInstall() = withTestDataStore { dataStore ->
        val store = ItemGroupModelSetupStore(dataStore)
        assertNull(store.source.first())
        store.installed(Source.FILE, 1000)
        assertEquals(Source.FILE, store.source.first())
        store.installed(Source.DOWNLOAD, 2000)
        assertEquals(Source.DOWNLOAD, ItemGroupModelSetupStore(dataStore).source.first())
    }

    @Test
    fun checksDownloadedModelOncePerInterval() = withTestDataStore { dataStore ->
        val store = ItemGroupModelSetupStore(dataStore)
        assertFalse(store.claimUpdateCheck(UPDATE_INTERVAL))
        store.installed(Source.DOWNLOAD, 1000)
        assertFalse(store.claimUpdateCheck(1000 + UPDATE_INTERVAL - 1))
        assertTrue(store.claimUpdateCheck(1000 + UPDATE_INTERVAL))
        assertFalse(store.claimUpdateCheck(1000 + UPDATE_INTERVAL + 1))
    }

    @Test
    fun neverChecksModelFromFile() = withTestDataStore { dataStore ->
        val store = ItemGroupModelSetupStore(dataStore)
        store.installed(Source.FILE, 1000)
        assertFalse(store.claimUpdateCheck(1000 + 10 * UPDATE_INTERVAL))
    }
}
