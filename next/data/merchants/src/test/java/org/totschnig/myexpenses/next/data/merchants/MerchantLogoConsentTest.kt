package org.totschnig.myexpenses.next.data.merchants

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.data.merchants.MerchantLogoStore.Fetched
import org.totschnig.myexpenses.next.data.merchants.MerchantLogoStore.Lookup

class MerchantLogoConsentTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun withdrawingConsentRemovesAllButTheEnteredLogos() = withTestDataStore { dataStore ->
        val dir = folder.root.resolve("logos")
        val store = MerchantLogoStore(
            dir,
            fetch = { Fetched.Icon(byteArrayOf(1)) },
            prepare = { Lookup.Found(it) }
        )
        val consent = MerchantLogoConsent(dataStore)
        val domains = PayeeDomainStore(dataStore)
        consent.set(true)
        domains.set(1, "baecker-schulz.de")
        store.logo("rewe.de")
        store.logo("baecker-schulz.de")
        coroutineScope {
            val job = launch { keepLogosOnlyWithConsent(consent, domains, store) }
            consent.set(false)
            // The data store and the files are on other threads
            withTimeout(5_000) {
                while (dir.list()!!.size > 1) delay(10)
            }
            assertEquals(listOf("baecker-schulz.de.png"), dir.list()!!.toList())
            job.cancel()
        }
    }
}
