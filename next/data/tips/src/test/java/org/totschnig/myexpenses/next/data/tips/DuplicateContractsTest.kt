package org.totschnig.myexpenses.next.data.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import java.time.LocalDate

class DuplicateContractsTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 29)
    private var nextId = 1L

    private fun contract(
        payee: String,
        amount: Long = 999,
        comment: String? = null,
        category: String? = null,
        customName: String? = null,
        interval: ContractInterval = ContractInterval.MONTHLY,
        cancelledOn: LocalDate? = null,
        reserve: Boolean? = null,
    ) = Contract(
        signature = "r:${nextId}",
        name = payee,
        customName = customName,
        interval = interval,
        transactions = listOf(
            ContractTransaction(nextId++, today.minusDays(3), -amount, 1, payeeName = payee, comment = comment, categoryPath = category)
        ),
        nextExpectedDate = today.plusDays(27),
        isActive = true,
        isConfirmed = true,
        cancelledOn = cancelledOn,
        reserveChoice = reserve
    )

    private fun kind(payee: String, comment: String? = null, category: String? = null, customName: String? = null) =
        ContractKind.of(contract(payee, comment = comment, category = category, customName = customName))

    @Test
    fun recognizesKinds() {
        assertEquals(ContractKind.VIDEO_STREAMING, kind("NETFLIX.COM"))
        assertEquals(ContractKind.VIDEO_STREAMING, kind("Disney Plus"))
        assertEquals(ContractKind.VIDEO_STREAMING, kind("RTL+ Premium"))
        assertEquals(ContractKind.MUSIC_STREAMING, kind("Spotify AB"))
        assertEquals(ContractKind.MOBILE, kind("congstar GmbH"))
        assertEquals(ContractKind.MOBILE, kind("Telekom", category = "Kommunikation > Handy"))
        assertEquals(ContractKind.INTERNET, kind("Vodafone", comment = "Kabel Internet 250"))
        assertEquals(ContractKind.ELECTRICITY, kind("Stadtwerke", comment = "Abschlag Strom"))
        assertEquals(ContractKind.GAS, kind("Stadtwerke", comment = "Abschlag Gas"))
        assertEquals(ContractKind.LIABILITY_INSURANCE, kind("HUK-COBURG", comment = "Privathaftpflicht"))
        assertEquals(ContractKind.CONTENTS_INSURANCE, kind("Allianz", comment = "Hausratversicherung"))
        assertEquals(ContractKind.GYM, kind("McFit GmbH"))
        assertEquals(ContractKind.CLOUD_STORAGE, kind("Apple", customName = "iCloud+"))
    }

    @Test
    fun ambiguousOrOtherContractsHaveNoKind() {
        assertNull(kind("Telekom Deutschland GmbH"))
        assertNull(kind("Stadtwerke München"))
        assertNull(kind("Gasthaus zur Post"))
        assertNull(kind("Gymnasium am Markt", comment = "Schulgeld"))
        assertNull(kind("Stadtwerke", comment = "Dienstleistung Wasser"))
        assertEquals(ContractKind.LIABILITY_INSURANCE, kind("HUK", comment = "Haftpflicht, SEPA Dienstleistung"))
    }

    @Test
    fun carOrPetLiabilityIsNoPrivateLiability() {
        assertEquals(ContractKind.CAR_INSURANCE, kind("HUK-COBURG", comment = "Kfz-Haftpflicht"))
        assertNull(kind("Uelzener", comment = "Hundehaftpflicht"))
    }

    @Test
    fun phoneInsuranceIsNoMobileContract() {
        assertNull(kind("WERTGARANTIE", comment = "Handyversicherung"))
    }

    @Test
    fun groupsContractsOfTheSameKind() {
        val tips = duplicateContracts(
            listOf(
                contract("Netflix", 1799),
                contract("Disney Plus", 999),
                contract("Spotify", 1099),
                contract("HUK", 5500, comment = "Privathaftpflicht", interval = ContractInterval.YEARLY),
                contract("Allianz", 6000, comment = "Privathaftpflicht", interval = ContractInterval.YEARLY),
                contract("congstar", 1000)
            )
        ).associateBy { it.kind }
        assertEquals(setOf(ContractKind.VIDEO_STREAMING, ContractKind.LIABILITY_INSURANCE), tips.keys)
        val video = tips.getValue(ContractKind.VIDEO_STREAMING)
        assertEquals(listOf("Netflix", "Disney Plus"), video.contracts.map { it.name })
        assertEquals((1799L + 999L) * 12, video.yearlyAmount)
        assertEquals("DUPLICATES:VIDEO_STREAMING", video.id)
    }

    @Test
    fun cancelledReservesAndEndedContractsDoNotCount() {
        assertTrue(duplicateContracts(listOf(contract("Netflix"), contract("Disney+", cancelledOn = today))).isEmpty())
        assertTrue(duplicateContracts(listOf(contract("Netflix"), contract("Disney+").copy(isActive = false))).isEmpty())
        assertTrue(
            duplicateContracts(
                listOf(contract("Depot", comment = "Sparplan Strom-ETF", reserve = true), contract("Vattenfall"))
            ).isEmpty()
        )
    }

    @Test
    fun dismissedTipComesBackWithAnotherContract() {
        val netflix = contract("Netflix")
        val disney = contract("Disney Plus")
        val tip = duplicateContracts(listOf(netflix, disney)).single()
        val decisions = TipDecisions(dismissed = mapOf(tip.id to tip.facts))
        assertTrue(decisions.isDismissed(duplicateContracts(listOf(netflix, disney)).single()))
        assertFalse(decisions.isDismissed(duplicateContracts(listOf(netflix, disney, contract("DAZN"))).single()))
    }
}
