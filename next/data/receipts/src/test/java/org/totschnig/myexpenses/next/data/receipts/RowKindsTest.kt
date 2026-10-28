package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Rows as recognized on the receipts of the fixtures */
class RowKindsTest {

    private val kinds = RowKinds(ReceiptVocabulary.GERMAN)

    private fun price(text: String) = kinds.price(kinds.tokens(text))?.let { "${it.cents} ${it.taxClass} ${text.substring(0, it.start).trim()}" }

    @Test
    fun price() {
        assertEquals("222 B KARTOFFELN", price("KARTOFFELN 2, 22 B"))
        assertEquals("198 A Knusperstange 2 x €0,99", price("Knusperstange 2 x €0,99 €1,98 A"))
        assertEquals("450 null Burek Käse", price("Burek Käse 4,50 €"))
        assertEquals("379 A 309172 1 3,79", price("309172 1 3,79 3,79A"))
        assertEquals("315 1 Mivolis Wundspray", price("Mivolis Wundspray 3,15 1 1"))
        assertEquals("1099 8 GEIG.PRISECCO0,75L", price("GEIG.PRISECCO0,75L 10,998"))
        assertEquals("-105 null Staffelrabatt", price("Staffelrabatt -1,05"))
        assertNull(price("0,536 kg x 3,49 EUR/kg"))
        assertNull(price("PAPRIKA ROT"))
    }

    @Test
    fun quantityRow() {
        val weight = kinds.quantity("0, 750 kg x 1,79 EUR/kg")!!
        assertEquals(BigDecimal("0.750"), weight.quantity)
        assertEquals(ReceiptUnit.KG, weight.unit)
        assertEquals(179L, weight.unitPrice)
        assertEquals(119L, kinds.quantity("2 Stk x x 1, 19")!!.unitPrice)
        assertEquals(179L, kinds.quantity("0,942 kg x 1,79 EUR/kg X")!!.unitPrice)
        assertEquals(129L, kinds.quantity("0,764 kg x 1,29 EUR / kg")!!.unitPrice)
        assertNull(kinds.quantity("BANANE 0,99 B"))
        // Without the sign only with unit and price per unit
        val withoutSign = kinds.quantity("0,764 kg 1,29 EUR/kg")!!
        assertEquals(BigDecimal("0.764"), withoutSign.quantity)
        assertEquals(ReceiptUnit.KG, withoutSign.unit)
        assertEquals(129L, withoutSign.unitPrice)
        assertEquals(129L, kinds.quantity("0,764 k9 1,29 €/kg")!!.unitPrice)
        assertNull(kinds.quantity("2 Stk 0,59"))
        assertNull(kinds.quantity("2 0,59 €/kg"))
    }

    @Test
    fun inlineQuantity() {
        fun inline(name: String) = kinds.inlineQuantity(kinds.tokens(name))?.let {
            "${name.substring(0, it.nameEnd).trim()} ${it.count} ${it.unitPrice}"
        }
        assertEquals("MEGGLE BAGUET 3 132", inline("MEGGLE BAGUET 1,32 € x X 3"))
        assertEquals("G&G HASELNUSS 2 299", inline("G&G HASELNUSS 2,99 € x2"))
        assertEquals("FRISCHE FEIGE 8 39", inline("FRISCHE FEIGE 0,39 € Lx 8"))
        assertEquals("BAGUETTE 2 119", inline("BAGUETTE 2 Stk x 1,19"))
        assertNull(inline("6x 0,75 Sportness NatEnergy Be"))
    }

    @Test
    fun detail() {
        val bakery = kinds.detail("1,00 Stück x 3,65")!!
        assertEquals(BigDecimal("1.00"), bakery.quantity)
        assertEquals(365L, bakery.unitPrice)
        assertEquals(379L, kinds.detail("309172 1 3,79")!!.unitPrice)
        assertEquals(1490L, kinds.detail("1.640 14.90")!!.unitPrice)
        assertNull(kinds.detail("")!!.quantity)
        assertNull(kinds.detail("Bez."))
    }

    @Test
    fun itemCount() {
        assertEquals(7, kinds.itemCount("7 Artikel"))
        assertEquals(9, kinds.itemCount("Posten: 9 1,19 A"))
        assertEquals(8, kinds.itemCount("Artikel 8"))
        assertEquals(5, kinds.itemCount("Total 5 Pos 50.47"))
        assertEquals(5, kinds.itemCount("SUMME [5] EUR 36,70"))
        assertNull(kinds.itemCount("Artikelname"))
        assertNull(kinds.itemCount("Posten:I"))
    }

    @Test
    fun articleNumbers() {
        assertTrue(kinds.isArticleNumberOnly("2945139"))
        assertFalse(kinds.isArticleNumberOnly("2945139 PHILIPS"))
        assertEquals("BBQ Grillkäse", kinds.withoutArticleNumber("655809 BBQ Grillkäse"))
        assertEquals("HD Rhabarber 11", kinds.withoutArticleNumber("HD Rhabarber 11"))
    }

    @Test
    fun columnSegments() {
        assertTrue(kinds.isPrice("1,10 A"))
        assertTrue(kinds.isPrice("€1,98 A"))
        assertFalse(kinds.isPrice("Burek Käse 4,50 €"))
        assertTrue(kinds.isAmountOnly("EUR 12, 15"))
        assertTrue(kinds.isCurrencyOnly("EUR"))
        assertTrue(kinds.isLoneSign("x"))
    }
}
