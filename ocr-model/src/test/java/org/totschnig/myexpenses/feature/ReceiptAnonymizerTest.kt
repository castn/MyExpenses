package org.totschnig.myexpenses.feature

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReceiptAnonymizerTest {

    private fun line(text: String, left: Int, row: Int, vararg words: String) = RecognizedLine(
        text,
        Box(left, row * 100, left + 400, row * 100 + 80),
        words.mapIndexed { i, word -> RecognizedElement(word, Box(left + i * 100, row * 100, left + i * 100 + 90, row * 100 + 80)) }
    )

    private val names = RecognizedBlock(
        listOf(
            line("EDEKA Kampmann", 0, 0, "EDEKA", "Kampmann"),
            line("Lindenstraße 16", 0, 1),
            line("D-50667 Köln", 0, 2),
            line("Tel. 0221 / 987654", 0, 3),
            line("USt-IdNr. DE812345678", 0, 4),
            line("Inh. Petra Kampmann", 0, 5),
            line("Kasse 2 Bon 4711 03.10.2026 14:32", 0, 6, "Kasse", "2", "Bon", "4711", "03.10.2026", "14:32"),
            line("EUR", 800, 7),
            line("655809 BBQ Grillkäse", 0, 8),
            line("Bonus 1234567890", 0, 9, "Bonus", "1234567890"),
            line("SUMME", 0, 10),
            line("Kartenzahlung", 0, 11),
            line("3 Artikel", 0, 12),
            line("Karte ************1234", 0, 13),
            line("TSE-Signatur 8823477712", 0, 14),
            line("03.10.2026 14:32", 0, 15)
        )
    )
    private val prices = RecognizedBlock(
        listOf(line("1,79 A", 800, 8), line("1,79", 800, 10), line("1,79", 800, 11))
    )
    private val text = RecognizedText(
        listOf(names, prices, RecognizedBlock(listOf(RecognizedLine("Hans Meier", null, emptyList()))))
    )
    private val expected = ExpectedReceipt("EDEKA Kampmann", listOf(ExpectedItem("BBQ Grillkäse", 179)), 179, 3)

    private val result = ReceiptAnonymizer.anonymize(text, expected)
    private val rows = result.text.rows().map { it.rowText() }

    @Test
    fun headKeepsChainAndReplacesAddress() {
        assertThat(rows.take(8)).containsExactly(
            "EDEKA",
            ReceiptAnonymizer.STREET,
            ReceiptAnonymizer.TOWN,
            ReceiptAnonymizer.PHONE,
            ReceiptAnonymizer.TAX_NUMBER,
            ReceiptAnonymizer.OWNER,
            "Kasse 2 Bon 4711 00.00.0000 00:00",
            "EUR"
        ).inOrder()
        assertThat(result.merchant).isEqualTo("EDEKA")
    }

    @Test
    fun itemsStayAndNumbersBetweenAreMasked() {
        assertThat(rows).containsAtLeast("655809 BBQ Grillkäse 1,79 A", "Bonus 0000000000", "SUMME 1,79").inOrder()
    }

    @Test
    fun footIsRemovedExceptArticleCount() {
        assertThat(rows.takeLast(2)).containsExactly("Kartenzahlung 1,79", "3 Artikel").inOrder()
    }

    @Test
    fun positionsStay() {
        val before = text.lines.associate { it.text to it.box }
        val kept = result.text.lines.first { it.text == "655809 BBQ Grillkäse" }
        assertThat(kept.box).isEqualTo(before["655809 BBQ Grillkäse"])
        assertThat(result.text.lines.first { it.text == ReceiptAnonymizer.STREET }.box).isEqualTo(before["Lindenstraße 16"])
    }

    @Test
    fun nothingPersonalIsLeft() {
        val all = result.text.lines.flatMap { line -> listOf(line.text) + line.elements.map { it.text } }.joinToString("\n")
        listOf("Kampmann", "Lindenstraße", "50667", "Köln", "987654", "DE812345678", "Petra", "1234567890",
            "8823477712", "03.10.2026", "14:32", "Hans Meier", "*1234").forEach {
            assertThat(all).doesNotContain(it)
        }
    }

    @Test
    fun redactionsCoverChangedText() {
        val boxes = result.redactions
        // Owner's name in the merchant row, address, phone, tax number, owner, date, time, bonus number, foot
        assertThat(boxes).contains(names.lines[0].elements[1].box)
        assertThat(boxes).contains(names.lines[1].box)
        assertThat(boxes).contains(names.lines[6].elements[4].box)
        assertThat(boxes).contains(names.lines[9].elements[1].box)
        assertThat(boxes).contains(names.lines[13].box)
        assertThat(boxes).doesNotContain(names.lines[8].box)
    }

    @Test
    fun mask() {
        assertThat(ReceiptAnonymizer.mask("Beleg 12345 vom 3.10.26 um 9:05:12")).isEqualTo("Beleg 00000 vom 0.00.00 um 0:00:00")
        assertThat(ReceiptAnonymizer.mask("1,79 A")).isEqualTo("1,79 A")
        assertThat(ReceiptAnonymizer.mask("06 Aug. 2026 14:42 Chef")).isEqualTo("00 Aug. 0000 00:00 Chef")
    }

    @Test
    fun chain() {
        assertThat(ReceiptAnonymizer.chain("EDEKA Kampmann")).isEqualTo("EDEKA")
        assertThat(ReceiptAnonymizer.chain("Aldi Süd")).isEqualTo("Aldi")
        assertThat(ReceiptAnonymizer.chain("Bäckerei Schmidt")).isEqualTo("Bäckerei")
        assertThat(ReceiptAnonymizer.chain("TAKE AWAY")).isEqualTo("TAKE AWAY")
        assertThat(ReceiptAnonymizer.chain("dm-drogerie markt")).isEqualTo("dm-drogerie markt")
        assertThat(ReceiptAnonymizer.chain("REWE")).isEqualTo("REWE")
        // Recognition reads l as 1
        assertThat(ReceiptAnonymizer.chain("EDEKA K1ink")).isEqualTo("EDEKA")
    }

    /** As recognized on a real receipt: owner in the web address, paid amount a row below the payment */
    @Test
    fun webAddressAndPaidAmountBelowPayment() {
        val text = RecognizedText(
            listOf(
                RecognizedBlock(
                    listOf(
                        line("EDEKA K1ink", 0, 0, "EDEKA", "K1ink"),
                        line("WWw.edeka-muster.de", 0, 1),
                        line("Herz.Hummus", 0, 2),
                        line("SUMME", 0, 3),
                        line("EC-Cash", 0, 4),
                        line("21,6", 800, 5),
                        line("Beleg-Nr. 4711", 0, 6)
                    )
                ),
                RecognizedBlock(listOf(line("1,79 A", 800, 2), line("21,60", 800, 3)))
            )
        )
        val result = ReceiptAnonymizer.anonymize(
            text, ExpectedReceipt("EDEKA K1ink", listOf(ExpectedItem("Herz.Hummus", 179)), 2160)
        )
        assertThat(result.merchant).isEqualTo("EDEKA")
        assertThat(result.text.rows().map { it.rowText() }).containsExactly(
            "EDEKA", ReceiptAnonymizer.WEB, "Herz.Hummus 1,79 A", "SUMME 21,60", "EC-Cash", "21,6"
        ).inOrder()
    }

    /** As on a real receipt: the owner in a logo above and the merchant typed differently */
    @Test
    fun ownerIsRemovedFromWholeHead() {
        val text = RecognizedText(
            listOf(
                RecognizedBlock(
                    listOf(
                        line("EDEKA Kampmann", 0, 0, "EDEKA", "Kampmann"),
                        line("EDEKA Kampmann", 0, 1, "EDEKA", "Kampmann"),
                        line("Herz.Hummus", 0, 2)
                    )
                ),
                RecognizedBlock(listOf(line("1,79 A", 800, 2)))
            )
        )
        val result = ReceiptAnonymizer.anonymize(
            text, ExpectedReceipt("EDEA Kampmann", listOf(ExpectedItem("Herz.Hummus", 179)))
        )
        assertThat(result.merchant).isEqualTo("EDEA")
        assertThat(result.text.rows().map { it.rowText() }).containsExactly("EDEKA", "EDEKA", "Herz.Hummus 1,79 A").inOrder()
        assertThat(result.text.lines.flatMap { it.elements }.map { it.text }).doesNotContain("Kampmann")
    }

    /** Misread as on real receipts */
    @Test
    fun garbledHead() {
        val text = RecognizedText(
            listOf(
                RecognizedBlock(
                    listOf(
                        line("Backshop Muster", 0, 0),
                        line("Fr. Bljerta C0CAJ", 0, 1),
                        line("Schafer Dein GabH", 0, 2),
                        line("Philipp-Bert-St r, la", 0, 3),
                        line("D-64405 Fischbachtal", 0, 4),
                        line("Burek Käse", 0, 5)
                    )
                ),
                RecognizedBlock(listOf(line("4,50 €", 800, 5)))
            )
        )
        val result = ReceiptAnonymizer.anonymize(text, ExpectedReceipt("Backshop Muster", listOf(ExpectedItem("Burek Käse", 450))))
        assertThat(result.text.rows().map { it.rowText() }).containsExactly(
            "Backshop",
            ReceiptAnonymizer.OWNER,
            ReceiptAnonymizer.OWNER,
            ReceiptAnonymizer.STREET,
            ReceiptAnonymizer.TOWN,
            "Burek Käse 4,50 €"
        ).inOrder()
    }

    @Test
    fun markedLinesAreHidden() {
        val marked = setOf(names.lines[6].box!!, names.lines[9].box!!)
        val result = ReceiptAnonymizer.anonymize(text, expected, marked)
        val rows = result.text.rows().map { it.rowText() }
        assertThat(rows).contains("XXXXX 0 XXX 0000 00.00.0000 00:00")
        assertThat(rows).contains("XXXXX 0000000000")
        assertThat(result.redactions).containsAtLeastElementsIn(marked)
        assertThat(result.text.lines.first { it.text == "XXXXX 0000000000" }.elements.map { it.text })
            .containsExactly("XXXXX", "0000000000").inOrder()
    }
}
