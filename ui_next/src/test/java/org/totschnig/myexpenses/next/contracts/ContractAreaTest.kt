package org.totschnig.myexpenses.next.contracts

import org.junit.Assert.assertEquals
import org.junit.Test

class ContractAreaTest {

    private fun assertSuggests(expected: BuiltInArea?, vararg paths: String?) {
        paths.forEach { assertEquals(it, expected, BuiltInArea.suggest(it)) }
    }

    @Test
    fun suggestsInsurance() {
        assertSuggests(
            BuiltInArea.INSURANCE,
            "Versicherung", "Versicherungen > Haftpflicht", "Auto > Kfz-Versicherung", "Insurance",
            "Wohnen > Haushaltsversicherung"
        )
    }

    @Test
    fun suggestsHousing() {
        assertSuggests(
            BuiltInArea.HOUSING,
            "Wohnen", "Wohnen > Miete", "Haushalt", "Nebenkosten > Strom", "Stromkosten", "Energie > Gas", "Housing > Rent"
        )
    }

    @Test
    fun mostSpecificPartWins() {
        assertSuggests(BuiltInArea.TELECOM, "Wohnen > Internet & Telefon", "Kommunikation > Handy")
        assertSuggests(BuiltInArea.STREAMING, "Freizeit > Streaming", "Unterhaltung > Pay-TV")
        assertSuggests(BuiltInArea.FITNESS, "Freizeit > Fitnessstudio", "Sport", "Sportverein")
        assertSuggests(BuiltInArea.MOBILITY, "Mobilität > Deutschlandticket", "Auto > Leasing")
        assertSuggests(BuiltInArea.FINANCE, "Finanzen > Kredit", "Bankgebühren")
        assertSuggests(BuiltInArea.SOFTWARE, "Computer > Cloud-Speicher", "Apps")
        assertSuggests(BuiltInArea.PRESS, "Medien > Zeitung")
        assertSuggests(BuiltInArea.MEMBERSHIP, "Vereine", "Mitgliedsbeiträge")
        assertSuggests(BuiltInArea.DONATIONS, "Spenden")
        assertSuggests(BuiltInArea.EDUCATION, "Kinder > Kita", "Musikschule")
        // The main category is used, if the sub category does not tell
        assertSuggests(BuiltInArea.HOUSING, "Wohnen > Sonstiges")
    }

    @Test
    fun suggestsNothingForOtherCategories() {
        assertSuggests(null, null, "", "Essen > Gastronomie", "Lebensmittel", "Kleidung")
    }
}
