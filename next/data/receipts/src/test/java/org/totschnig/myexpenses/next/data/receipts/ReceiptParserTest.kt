package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.RecognizedBlock
import org.totschnig.myexpenses.feature.RecognizedLine
import org.totschnig.myexpenses.feature.RecognizedText
import org.totschnig.myexpenses.feature.recognizedTextFromJson
import java.math.BigDecimal

class ReceiptParserTest {

    private fun fixture(name: String) = ReceiptParser.parse(
        recognizedTextFromJson(ReceiptFixtures.text(name))
    )

    private fun ParsedReceipt.summary() = items.map { Triple(it.name, it.amount, it.quantity.toPlainString()) }

    /** Name column and price column as separate blocks, one line per row of [rows] */
    private fun receipt(vararg rows: Pair<String, String?>) = RecognizedText(
        listOf(
            RecognizedBlock(rows.mapIndexed { i, (name, _) -> line(name, 0, i) }),
            RecognizedBlock(rows.mapIndexedNotNull { i, (_, price) -> price?.let { line(it, 800, i) } })
        )
    )

    private fun line(text: String, left: Int, row: Int) =
        RecognizedLine(text, Box(left, row * 100, left + 200, row * 100 + 80), emptyList())

    @Test
    fun rewe() {
        val receipt = fixture("rewe")
        assertEquals("REWE", receipt.merchant)
        assertEquals(
            listOf(
                Triple("KARTOFFELN", 222L, "1"),
                Triple("PAPRIKA ROT", 159L, "1"),
                Triple("SCHOKO MUESLI", 249L, "1"),
                Triple("HAFERFL. KERNIG", 79L, "1"),
                Triple("SONNENBLUMENOEL", 219L, "1"),
                Triple("KICHERERBSEN", 177L, "3"),
                Triple("JA FRISCHHALTE", 110L, "1")
            ),
            receipt.summary()
        )
        assertEquals(59L, receipt.items[5].unitPrice)
        assertEquals("A", receipt.items[6].taxClass)
        assertEquals(1215L, receipt.total)
        assertTrue(ReceiptCheck(receipt, 1215).isConsistent)
    }

    @Test
    fun aldi() {
        val receipt = fixture("aldi")
        assertEquals("Aldi Süd", receipt.merchant)
        assertEquals(
            listOf(
                Triple("BBQ Grillkäse", 179L, "1"),
                Triple("Brat u.Grilikaese", 229L, "1"),
                Triple("Hummus 200 g", 99L, "1"),
                Triple("Spitzpapr ika Mix", 179L, "1"),
                Triple("Laugenzopf m. Käse", 59L, "1"),
                // quantity row printed above the item
                Triple("Körner-Krusti", 178L, "2"),
            ),
            receipt.summary()
        )
        assertEquals(923L, receipt.total)
        assertEquals(7, receipt.itemCount)
        assertTrue(ReceiptCheck(receipt, 923).isConsistent)
    }

    @Test
    fun edeka() {
        val receipt = fixture("edeka")
        assertEquals("EDEKA Kampmann", receipt.merchant)
        assertEquals(
            listOf(
                Triple("MEGGLE BAGUET", 396L, "3"),
                Triple("G&G MARMORKUCHEN", 169L, "1"),
                Triple("G&G STRACCI.KUCHEN", 169L, "1"),
                Triple("ALPENHAIN 0BAZDA", 259L, "1"),
                Triple("GAZI H.GRILLKAESE", 299L, "1"),
                Triple("GAZI GRILLKAESE", 299L, "1"),
                Triple("ROUGETT.GRILLKAESE", 399L, "1")
            ),
            receipt.summary()
        )
        assertEquals("AP", receipt.items[0].taxClass)
        assertEquals(132L, receipt.items[0].unitPrice)
        assertEquals(1990L, receipt.total)
        assertEquals(9, receipt.itemCount)
        assertTrue(ReceiptCheck(receipt, 1990).isConsistent)
    }

    @Test
    fun edekaWithWeighedItem() {
        val receipt = fixture("edeka-hummus")
        // photo cut to the items
        assertNull(receipt.merchant)
        assertEquals(
            listOf(
                Triple("Herz.Hummus", 179L, "1"),
                Triple("Milr.Grilltal", 658L, "2"),
                Triple("Eiswürfel", 279L, "1"),
                Triple("Paprika", 137L, "0.344"),
                Triple("Prebake erm.", 250L, "1"),
                Triple("HD Rhabarber 11", 269L, "1"),
                Triple("HD Drachenfr.11", 269L, "1"),
                Triple("Weih.Kakao", 119L, "1")
            ),
            receipt.summary()
        )
        // "AW" read as "A#"
        assertEquals("kg", receipt.items[3].unit)
        assertEquals(399L, receipt.items[3].unitPrice)
        assertEquals(2160L, receipt.total)
        assertTrue(ReceiptCheck(receipt, 2160).isConsistent)
    }

    @Test
    fun bakeryWithQuantityBelowName() {
        val receipt = fixture("baecker")
        assertEquals(
            listOf(
                Triple("Käse-Ei Brötchen", 365L, "1"),
                Triple("Knusperhähnchen", 485L, "1"),
                Triple("Streuse lbrötchen", 175L, "1")
            ),
            receipt.summary()
        )
        assertEquals("B", receipt.items[1].taxClass)
        assertEquals(1025L, receipt.total)
        assertTrue(ReceiptCheck(receipt, 1025).isConsistent)
    }

    @Test
    fun diyStoreWithArticleNumberBelowName() {
        val receipt = fixture("baumarkt")
        assertEquals(
            listOf(Triple("EinschlagMutt. M10", 379L, "1"), Triple("EinschlagMutt. M10", 379L, "1")),
            receipt.summary()
        )
        assertEquals("A", receipt.items[0].taxClass)
        assertEquals(758L, receipt.total)
        assertTrue(ReceiptCheck(receipt, 758).isConsistent)
    }

    @Test
    fun takeAwayWithTotalAfterCount() {
        val receipt = fixture("takeaway")
        assertEquals(
            listOf(Triple("Burek Käse", 450L, "1"), Triple("Strudelmit Kirschen", 300L, "1")),
            receipt.summary()
        )
        assertEquals(2, receipt.itemCount)
        // recognition read the total 7,50 as 1,50, the check shows the difference
        assertEquals(150L, receipt.total)
        assertEquals(600L, ReceiptCheck(receipt, 750).itemsDifference)
    }

    @Test
    fun dmWithDigitTaxClassAndUnreadableTotal() {
        val receipt = fixture("dm")
        assertEquals(11, receipt.items.size)
        assertEquals("Mivolis Wundspray", receipt.items[6].name)
        assertEquals("2", receipt.items[0].taxClass)
        assertEquals("1", receipt.items[6].taxClass)
        // "SUMME" read as "SSMME", the total is taken from the payment
        assertEquals(3230L, receipt.total)
        assertTrue(ReceiptCheck(receipt, 3230).isConsistent)
    }

    @Test
    fun edekaWithSubtotalTiltAndDiscountAfterTotal() {
        val receipt = fixture("edeka-gross")
        val items = receipt.summary()
        assertEquals(30, items.size)
        // the right column is lower than the names at the top of the photo
        assertEquals(
            listOf(
                Triple("SCHW.H-MILCH", 1790L, "10"),
                Triple("G&G SCHLAGSAH", 890L, "10"),
                Triple("G&G TOILETTENPAP.", 745L, "1"),
                Triple("EXQUISA D.SAHNIGE", 289L, "1")
            ),
            items.take(4)
        )
        // "Zw.Summe" and "neuer Preis" are no items
        assertEquals(Triple("SCHW.FRISCHMILCH", 358L, "2"), items[7])
        assertEquals(Triple("Preisänderung", -178L, "1"), items[8])
        // price unreadable, amount from "1,69 € x 2"
        assertEquals(Triple("U.H.BUTTER M2", 338L, "2"), items[17])
        assertEquals(Triple("BIO ALNA .HASELNUŚS", 899L, "1"), items[23])
        assertEquals(Triple("FRISCHE FEIGE", 312L, "8"), items[26])
        // discount printed after the total
        assertEquals(Triple("Feigen 1 Euro", -112L, "1"), items.last())
        assertEquals("A", receipt.items[1].taxClass)
        assertEquals(17419L, receipt.total)
        assertEquals(63, receipt.itemCount)
        assertEquals(0L, ReceiptCheck(receipt, 17419).itemsDifference)
    }

    @Test
    fun butcherWithWeightsBelowName() {
        val receipt = fixture("metzger")
        assertEquals(
            listOf(
                Triple("Rinderbeinfleisch", 2444L, "1.64"),
                Triple("BlukAurst frisch", 464L, "0.258"),
                Triple("Leberurst frisch", 659L, "0.364"),
                Triple("Leberkurst 200g", 740L, "2"),
                Triple("Leberurst 20g", 740L, "2")
            ),
            receipt.summary()
        )
        assertEquals("kg", receipt.items[0].unit)
        assertEquals(1490L, receipt.items[0].unitPrice)
        assertNull(receipt.items[3].unit)
        assertEquals(5047L, receipt.total)
        // "5 Pos" counts rows, not pieces
        assertTrue(ReceiptCheck(receipt, 5047).isConsistent)
    }

    @Test
    fun weighedItem() {
        val receipt = ReceiptParser.parse(
            receipt("BANANEN" to "1,53 B", "0,512 kg x 2,99 EUR/kg" to null, "SUMME" to "1,53")
        )
        val item = receipt.items.single()
        assertEquals(0, BigDecimal("0.512").compareTo(item.quantity))
        assertEquals("kg", item.unit)
        assertEquals(299L, item.unitPrice)
        assertEquals(1, receipt.pieces)
    }

    @Test
    fun weighedItemWithoutSign() {
        listOf(
            // price on the row of the name
            receipt("BANANE" to "0,99 B", "0,764 kg 1,29 EUR/kg" to null, "SUMME" to "0,99"),
            // price on the row of the weight
            receipt("BANANE" to null, "0,764 kg 1,29 EUR/kg" to "0,99 B", "SUMME" to "0,99")
        ).forEach { text ->
            val item = ReceiptParser.parse(text).items.single()
            assertEquals("BANANE", item.name)
            assertEquals(0, BigDecimal("0.764").compareTo(item.quantity))
            assertEquals("kg", item.unit)
            assertEquals(129L, item.unitPrice)
        }
    }

    @Test
    fun discountAndDeposit() {
        val receipt = ReceiptParser.parse(
            receipt(
                "MILCH" to "1,19 A", "RABATT" to "-0,30 A", "PFAND" to "0,25 A", "LEERGUT" to "0,25- A",
                "SUMME" to "0,89"
            )
        )
        assertEquals(listOf(119L, -30L, 25L, -25L), receipt.items.map { it.amount })
        assertEquals(89L, receipt.total)
    }

    @Test
    fun quantityWithoutMatchingItemIsIgnored() {
        val receipt = ReceiptParser.parse(receipt("BROT" to "2,49 A", "2 x 0,89" to null, "SUMME" to "2,49"))
        assertEquals(BigDecimal.ONE, receipt.items.single().quantity)
        assertNull(receipt.items.single().unitPrice)
    }

    @Test
    fun rowsAfterTotalAreNoItems() {
        val receipt = ReceiptParser.parse(
            receipt("BROT" to "2,49 A", "SUMME" to "2,49", "EC-Cash" to "2,49", "A 7%" to "0,16")
        )
        assertEquals(1, receipt.items.size)
    }

    @Test
    fun checkReportsDifferences() {
        val receipt = ReceiptParser.parse(receipt("BROT" to "2,49 A", "MILCH" to "1,19 A", "SUMME" to "3,98"))
        val check = ReceiptCheck(receipt, 400)
        assertEquals(-30L, check.itemsDifference)
        assertEquals(-2L, check.totalDifference)
        assertFalse(check.isConsistent)
    }

    @Test
    fun withoutTotalItemsAreComparedWithTransaction() {
        val receipt = ReceiptParser.parse(receipt("BROT" to "2,49 A"))
        assertNull(receipt.total)
        assertEquals(0L, ReceiptCheck(receipt, 249).itemsDifference)
    }

    @Test
    fun dateAndCardAmountWithCashBack() {
        val receipt = ReceiptParser.parse(
            receipt(
                "REWE" to null,
                "BANANE" to "1,34 B",
                "BROT" to "2,49 B",
                "SUMME" to "3,83",
                "Bargeld Auszahlung" to "20,00",
                "Geg. EC-Cash" to "23,83",
                "11.10.2026 18:42 Bon-Nr. 4711" to null
            )
        )
        assertEquals(383L, receipt.total)
        assertEquals(2383L, receipt.cardAmount)
        assertEquals(false, receipt.cash)
        assertEquals(java.time.LocalDate.of(2026, 10, 11), receipt.date)
    }

    @Test
    fun cardAmountBelowTheCardRow() {
        val receipt = ReceiptParser.parse(
            receipt("BROT" to "2,49 B", "SUMME" to "2,49", "Kartenzahlung" to null, "EUR 2,49" to null)
        )
        assertEquals(249L, receipt.cardAmount)
    }

    @Test
    fun cash() {
        val receipt = ReceiptParser.parse(
            receipt("BROT" to "2,49 B", "SUMME" to "2,49", "BAR" to "5,00", "Rückgeld" to "2,51")
        )
        assertNull(receipt.cardAmount)
        assertEquals(true, receipt.cash)
        assertNull(receipt.date)
    }

    @Test
    fun rowsNoItemWasReadFromAreLeftOver() {
        val receipt = ReceiptParser.parse(
            receipt(
                "REWE" to null,
                "BANANE" to "1,34 B",
                "Mo-Sa 7-22 Uhr" to null,
                "3 Stk x 0,59" to null,
                "KICHERERBSEN" to "1,77 B",
                "Zw.Summe" to "3,11",
                "SUMME" to "3,11"
            )
        )
        assertEquals(listOf("BANANE", "KICHERERBSEN"), receipt.items.map { it.name })
        assertEquals(listOf("Mo-Sa 7-22 Uhr"), receipt.leftoverRows.map { it.text })
    }

    @Test
    fun itemOfLeftoverRows() {
        fun row(text: String, index: Int) = LeftoverRow(text, Box(0, index * 100, 1000, index * 100 + 80))
        val single = ReceiptParser.itemOf(listOf(row("KARTOFFELN 2,22 B", 0)))
        assertEquals("KARTOFFELN", single.name)
        assertEquals(222L, single.amount)
        assertEquals("B", single.taxClass)

        val nameAndPrice = ReceiptParser.itemOf(listOf(row("BIO HAFER", 1), row("DRINK", 2), row("1,99 A", 3)))
        assertEquals("BIO HAFER DRINK", nameAndPrice.name)
        assertEquals(199L, nameAndPrice.amount)
        assertEquals(Box(0, 100, 1000, 380), nameAndPrice.box)

        val withQuantity = ReceiptParser.itemOf(listOf(row("BROETCHEN", 4), row("4 x 0,35", 5), row("1,40 B", 6)))
        assertEquals(140L, withQuantity.amount)
        assertEquals(35L, withQuantity.unitPrice)

        val nameOnly = ReceiptParser.itemOf(listOf(row("SONDERPOSTEN", 7)))
        assertEquals("SONDERPOSTEN", nameOnly.name)
        assertEquals(0L, nameOnly.amount)
    }
}
