package org.totschnig.myexpenses.next.feature.merchants

import androidx.lifecycle.viewModelScope
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.ui.merchants.PayeeImage
import org.totschnig.myexpenses.next.data.merchants.MerchantLogoConsent
import org.totschnig.myexpenses.next.data.merchants.MerchantLogoStore
import org.totschnig.myexpenses.next.data.merchants.MerchantLogoStore.Fetched
import org.totschnig.myexpenses.next.data.merchants.MerchantLogoStore.Lookup
import org.totschnig.myexpenses.next.data.merchants.PayeeDomainStore
import org.totschnig.myexpenses.next.data.merchants.PayeeIconStore
import org.totschnig.myexpenses.next.data.merchants.RawPayee

/**
 * The state and actions of [MerchantLogos] with the real stores. No logo is ever found: decoding
 * one uses BitmapFactory, which only has stubs on the JVM, so [Lookup.Found] is not covered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MerchantLogosActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val directory = createTempDirectory("logos").toFile()

    private val rewe = RawPayee(1, "REWE Markt GmbH", null, null)

    private val stadtwerke = RawPayee(2, "Stadtwerke", null, null)

    private val cart = PayeeImage.Icon("cart-shopping")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        directory.deleteRecursively()
    }

    private class Fixture(
        val logos: MerchantLogos,
        val payees: MutableStateFlow<List<RawPayee>>,
        val consent: MerchantLogoConsent,
        val domains: PayeeDomainStore,
        val icons: PayeeIconStore,
        /** The domains asked of the icon service, in order */
        val fetched: MutableStateFlow<List<String>>
    )

    private fun withLogos(
        payees: List<RawPayee>,
        consentGiven: Boolean = false,
        fetch: (String) -> Fetched = { Fetched.Missing },
        prepare: (ByteArray) -> Lookup = { Lookup.Unreadable },
        block: suspend TestScope.(Fixture) -> Unit
    ) = withTestDataStore { dataStore ->
        runTest {
            val consent = MerchantLogoConsent(dataStore)
            consent.set(consentGiven)
            val fetched = MutableStateFlow<List<String>>(emptyList())
            val store = MerchantLogoStore(
                directory,
                fetch = { domain ->
                    fetched.update { it + domain }
                    fetch(domain)
                },
                prepare = prepare
            )
            val payeeFlow = MutableStateFlow(payees)
            val domains = PayeeDomainStore(dataStore)
            val icons = PayeeIconStore(dataStore)
            val logos = MerchantLogos(payeeFlow, consent, domains, icons, store, dispatcher)
            try {
                block(Fixture(logos, payeeFlow, consent, domains, icons, fetched))
            } finally {
                // Its coroutines must not outlive the test dispatcher set as Main
                logos.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    /** The first value of [flow] that matches, failing after a few seconds of real time */
    private suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { flow.first(predicate) } }

    /** Lets a request on the IO threads, if one was started, reach the icon service */
    private suspend fun settle() = withContext(Dispatchers.Default) { delay(200) }

    private suspend fun enter(logos: MerchantLogos, payeeId: Long, address: String): Lookup {
        val result = CompletableDeferred<Lookup>()
        logos.enterDomain(payeeId, address) { result.complete(it) }
        return withContext(Dispatchers.Default) { withTimeout(5_000) { result.await() } }
    }

    @Test
    fun enteredDomainsAndChosenIconsFollowTheStores() = withLogos(listOf(rewe, stadtwerke)) {
        it.domains.set(2, "stadtwerke.example")
        it.icons.set(1, "bolt")
        assertEquals(
            mapOf(2L to "stadtwerke.example"),
            awaitValue(it.logos.enteredDomains) { domains -> domains.isNotEmpty() }
        )
        assertEquals(
            mapOf(1L to "bolt"),
            awaitValue(it.logos.chosenIcons) { icons -> icons.isNotEmpty() }
        )
    }

    @Test
    fun merchantShowsItsIconWithoutConsentAndAsksNothing() = withLogos(listOf(rewe)) {
        assertEquals(cart, awaitValue(it.logos.images) { images -> images.isNotEmpty() }[1])
        settle()
        assertEquals(emptyList<String>(), it.fetched.value)
    }

    @Test
    fun merchantLogoIsAskedOnceWithConsent() = withLogos(
        listOf(rewe, RawPayee(3, "REWE City", null, null)),
        consentGiven = true
    ) {
        backgroundScope.launch { it.logos.images.collect {} }
        awaitValue(it.fetched) { fetched -> fetched.isNotEmpty() }
        // A new list of payees does not ask again for the same merchant
        it.payees.value = it.payees.value + stadtwerke
        settle()
        assertEquals(listOf("rewe.de"), it.fetched.value)
        // Without logo the monogram stays, no icon in its place
        assertEquals(emptyMap<Long, PayeeImage>(), it.logos.images.value)
    }

    @Test
    fun enteredDomainIsAskedAlsoWithoutConsent() = withLogos(listOf(rewe, stadtwerke)) {
        it.domains.set(2, "stadtwerke.example")
        backgroundScope.launch { it.logos.images.collect {} }
        awaitValue(it.fetched) { fetched -> fetched.isNotEmpty() }
        settle()
        assertEquals(listOf("stadtwerke.example"), it.fetched.value)
        assertEquals(
            mapOf(1L to cart),
            awaitValue(it.logos.images) { images -> images.isNotEmpty() }
        )
    }

    @Test
    fun childPayeeShowsTheIconOfItsParent() = withLogos(
        listOf(stadtwerke, RawPayee(4, "Stadtwerke Strom", null, 2))
    ) {
        it.logos.setIcon(2, "bolt")
        val bolt = PayeeImage.Icon("bolt")
        assertEquals(
            mapOf(2L to bolt, 4L to bolt),
            awaitValue(it.logos.images) { images -> images.size == 2 }
        )
    }

    @Test
    fun chosenIconWinsOverMerchantLogo() = withLogos(listOf(rewe), consentGiven = true) {
        it.logos.setIcon(1, "bolt")
        assertEquals(
            PayeeImage.Icon("bolt"),
            awaitValue(it.logos.images) { images -> images.isNotEmpty() }[1]
        )
    }

    @Test
    fun withdrawnConsentShowsTheMerchantIconAgain() = withLogos(listOf(rewe), consentGiven = true) {
        backgroundScope.launch { it.logos.images.collect {} }
        awaitValue(it.fetched) { fetched -> fetched.isNotEmpty() }
        it.consent.set(false)
        assertEquals(cart, awaitValue(it.logos.images) { images -> images.isNotEmpty() }[1])
    }

    @Test
    fun enterDomainNormalizesTheAddressAndAsksAgainAfterMissing() = withLogos(listOf(stadtwerke)) {
        assertEquals(Lookup.Missing, enter(it.logos, 2, "https://www.Stadtwerke.example/kontakt"))
        // Asked recently without logo, the user asking again still reaches the icon service
        assertEquals(Lookup.Missing, enter(it.logos, 2, "stadtwerke.example"))
        assertEquals(listOf("stadtwerke.example", "stadtwerke.example"), it.fetched.value)
    }

    @Test
    fun enterDomainFailedKeepsWhatWasChosen() = withLogos(
        listOf(stadtwerke),
        fetch = { Fetched.Failed }
    ) {
        it.icons.set(2, "bolt")
        assertEquals(Lookup.Failed, enter(it.logos, 2, "stadtwerke.example"))
        assertEquals(emptyMap<Long, String>(), it.domains.domains.first())
        assertEquals(mapOf(2L to "bolt"), it.icons.values.first())
    }

    @Test
    fun enterDomainUnreadableKeepsNothing() = withLogos(
        listOf(stadtwerke),
        fetch = { Fetched.Icon(byteArrayOf(1, 2, 3)) },
        prepare = { Lookup.Unreadable }
    ) {
        assertEquals(Lookup.Unreadable, enter(it.logos, 2, "stadtwerke.example"))
        assertEquals(emptyMap<Long, String>(), it.domains.domains.first())
    }

    @Test
    fun enterDomainTooSmallKeepsNothing() = withLogos(
        listOf(stadtwerke),
        fetch = { Fetched.Icon(byteArrayOf(1, 2, 3)) },
        prepare = { Lookup.TooSmall }
    ) {
        assertEquals(Lookup.TooSmall, enter(it.logos, 2, "stadtwerke.example"))
        assertEquals(emptyMap<Long, String>(), it.domains.domains.first())
    }

    @Test
    fun enterDomainWithoutLogoKeepsThePreviousAddress() = withLogos(listOf(stadtwerke)) {
        it.domains.set(2, "old.example")
        assertEquals(Lookup.Missing, enter(it.logos, 2, "new.example"))
        assertEquals(mapOf(2L to "old.example"), it.domains.domains.first())
    }

    @Test
    fun removeDomainShowsTheMerchantAgain() = withLogos(listOf(rewe)) {
        it.domains.set(1, "rewe-city.example")
        backgroundScope.launch { it.logos.images.collect {} }
        awaitValue(it.fetched) { fetched -> fetched.isNotEmpty() }
        assertNull(it.logos.images.value[1])
        it.logos.removeDomain(1)
        assertEquals(cart, awaitValue(it.logos.images) { images -> images.isNotEmpty() }[1])
        awaitValue(it.logos.enteredDomains) { domains -> domains.isEmpty() }
    }

    @Test
    fun setIconClearsTheEnteredDomain() = withLogos(listOf(stadtwerke)) {
        it.domains.set(2, "stadtwerke.example")
        it.logos.setIcon(2, "bolt")
        awaitValue(it.domains.domains) { domains -> domains.isEmpty() }
        assertEquals(mapOf(2L to "bolt"), it.icons.values.first())
    }

    @Test
    fun setIconNullKeepsTheEnteredDomain() = withLogos(listOf(stadtwerke)) {
        it.domains.set(2, "stadtwerke.example")
        it.icons.set(2, "bolt")
        it.logos.setIcon(2, null)
        awaitValue(it.icons.values) { icons -> icons.isEmpty() }
        assertEquals(mapOf(2L to "stadtwerke.example"), it.domains.domains.first())
    }

    @Test
    fun useAutomaticForgetsIconAndDomainOfThatPayee() = withLogos(listOf(rewe, stadtwerke)) {
        it.icons.set(1, "bolt")
        it.domains.set(1, "rewe-city.example")
        it.icons.set(2, "bolt")
        it.logos.useAutomatic(1)
        awaitValue(it.domains.domains) { domains -> domains.isEmpty() }
        assertEquals(mapOf(2L to "bolt"), it.icons.values.first())
    }
}
