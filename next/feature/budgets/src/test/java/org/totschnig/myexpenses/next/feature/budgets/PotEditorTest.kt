package org.totschnig.myexpenses.next.feature.budgets

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.data.budgets.PotKind
import org.totschnig.myexpenses.next.data.budgets.PotTemplate

class PotEditorTest {

    private val euro = CurrencyInfo("EUR", "€", 2)
    private val yen = CurrencyInfo("JPY", "¥", 0)

    @Test
    fun parsesAmountsAsTyped() {
        assertEquals(1250L, parseAmount("12,50", euro))
        assertEquals(1250L, parseAmount(" 12.5 ", euro))
        assertEquals(1200L, parseAmount("1200", yen))
        assertNull(parseAmount("0", euro))
        assertNull(parseAmount("-5", euro))
        assertNull(parseAmount("abc", euro))
    }

    @Test
    fun newPotTakesKindAndCategoriesOfTheTemplate() {
        val today = LocalDate.of(2026, 10, 13)
        val categories = listOf(CategoryInfo(1, "Geschenke & Spenden", "Geschenke & Spenden"))
        val gifts = newPot(PotTemplate.GIFTS, "Geschenke", categories, today)
        assertEquals(PotKind.YEARLY, gifts.kind)
        assertEquals(setOf(1L), gifts.categoryIds)
        assertEquals(today, gifts.start)
        assertNull(newPot(PotTemplate.PROJECT, "Italien", categories, today).start)
        assertEquals(PotKind.RUNNING, newPot(null, "", categories, today).kind)
    }
}
