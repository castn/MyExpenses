package org.totschnig.myexpenses.next.data.tags

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore

class TagStoreTest {

    @Test
    fun storesMeaningsRulesAndRejections() = withTestDataStore { dataStore ->
        val store = TagStore(dataStore)
        store.setMeaning(1, TagMeaning.TRIP)
        store.setMeaning(2, TagMeaning.ONE_OFF)
        store.setMeaning(2, null)
        store.addRule(TagRule("p:1", 5, 100))
        store.addRule(TagRule("p:1", 5, 200))
        store.reject(TagStore.Rejected.TRIPS, "t1")
        store.reject(TagStore.Rejected.TRIPS, "t2")
        assertEquals(mapOf(1L to TagMeaning.TRIP), store.meanings.first())
        // A rule for the same counterpart and tag replaces the one before
        assertEquals(listOf(TagRule("p:1", 5, 200)), store.rules.first())
        assertEquals(setOf("t1", "t2"), store.rejected(TagStore.Rejected.TRIPS).first())
        assertEquals(emptySet<String>(), store.rejected(TagStore.Rejected.REFUNDS).first())
    }

    @Test
    fun removesRulesAndForgetsRejections() = withTestDataStore { dataStore ->
        val store = TagStore(dataStore)
        store.addRule(TagRule("p:1", 5, 100))
        store.addRule(TagRule("m:zalando", 6, 100))
        store.reject(TagStore.Rejected.TRIPS, "t1")
        store.reject(TagStore.Rejected.SUGGESTIONS, "s1")
        store.removeRule(TagRule("p:1", 5, 100))
        store.clearRejected()
        assertEquals(listOf(TagRule("m:zalando", 6, 100)), store.rules.first())
        TagStore.Rejected.entries.forEach { assertEquals(emptySet<String>(), store.rejected(it).first()) }
    }
}
