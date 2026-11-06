package org.totschnig.fixturecollector

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.ExpectedItem
import org.totschnig.myexpenses.feature.RecognizedBlock
import org.totschnig.myexpenses.feature.RecognizedLine
import org.totschnig.myexpenses.feature.RecognizedText

class SuggestionTest {

    private fun line(text: String, left: Int, row: Int, shift: Int = 0) = RecognizedLine(
        text,
        Box(left, row * 100 + shift, left + 300, row * 100 + 80 + shift),
        emptyList()
    )

    /** Names and prices in separate blocks, as recognition often returns them */
    private val text = RecognizedText(
        listOf(
            RecognizedBlock(
                listOf(
                    line("REWE", 0, 0),
                    line("KARTOFFELN", 0, 1),
                    line("PFAND", 0, 2),
                    line("ZWISCHENSUMME", 0, 3),
                    line("SUMME", 0, 4),
                    line("3 Artikel", 0, 5)
                )
            ),
            RecognizedBlock(
                listOf(
                    line("2,22 A", 800, 1, shift = 10),
                    line("0,25- B", 800, 2),
                    line("1,97", 800, 3),
                    line("EUR 1,97", 800, 4)
                )
            )
        )
    )

    @Test
    fun rowsJoinSegmentsSideBySide() {
        assertThat(
            Suggestion.rows(text).take(2)
        ).containsExactly("REWE", "KARTOFFELN 2,22 A").inOrder()
    }

    @Test
    fun suggestion() {
        val receipt = Suggestion.of(text, "16.0.1")
        assertThat(receipt.merchant).isEqualTo("REWE")
        assertThat(receipt.items).containsExactly(
            ExpectedItem("KARTOFFELN", 222, taxClass = "A"),
            ExpectedItem("PFAND", -25, taxClass = "B")
        ).inOrder()
        assertThat(receipt.total).isEqualTo(197)
        assertThat(receipt.itemCount).isEqualTo(3)
        assertThat(receipt.mlkitVersion).isEqualTo("16.0.1")
    }

    @Test
    fun combineNameAndPriceRows() {
        assertThat(
            Suggestion.combine(listOf("KARTOFFELN", "2,22 A"))
        ).isEqualTo(ExpectedItem("KARTOFFELN", 222, taxClass = "A"))
    }

    @Test
    fun combineNameOnTwoRows() {
        assertThat(Suggestion.combine(listOf("Laugenzopf", "m. Käse 0,59")))
            .isEqualTo(ExpectedItem("Laugenzopf m. Käse", 59))
    }

    @Test
    fun combineQuantityRow() {
        assertThat(Suggestion.combine(listOf("2 X 0,89", "Körner-Krusti 1,78 A")))
            .isEqualTo(
                ExpectedItem("Körner-Krusti", 178, quantity = "2", unitPrice = 89, taxClass = "A")
            )
    }

    @Test
    fun combineQuantityBeforePrice() {
        assertThat(Suggestion.combine(listOf("KICHERERBSEN", "3 Stk x 0,59 1,77")))
            .isEqualTo(ExpectedItem("KICHERERBSEN", 177, quantity = "3", unitPrice = 59))
    }

    @Test
    fun combineWeighedWithoutPrice() {
        assertThat(Suggestion.combine(listOf("BANANEN", "0,456 kg x 1,99 €/kg")))
            .isEqualTo(
                ExpectedItem("BANANEN", 91, quantity = "0.456", unit = "kg", unitPrice = 199)
            )
    }

    /** As recognized on real receipts */
    @Test
    fun combineWithRecognitionErrors() {
        assertThat(Suggestion.combine(listOf("BANANE 1,34 B", "0, 750 kg x 1,79 EUR/kg")))
            .isEqualTo(
                ExpectedItem(
                    "BANANE",
                    134,
                    quantity = "0.750",
                    unit = "kg",
                    unitPrice = 179,
                    taxClass = "B"
                )
            )
        assertThat(Suggestion.combine(listOf("BAGUETTE 2,38 B", "2 Stk x x 1, 19")))
            .isEqualTo(
                ExpectedItem("BAGUETTE", 238, quantity = "2", unitPrice = 119, taxClass = "B")
            )
        assertThat(Suggestion.combine(listOf("Knusperstange", "2 x €0,99 €1,98 A")))
            .isEqualTo(
                ExpectedItem("Knusperstange", 198, quantity = "2", unitPrice = 99, taxClass = "A")
            )
        assertThat(Suggestion.combine(listOf("Krusperhâhnchen", "1,00 Gtúck x 4,85 4,85 B")))
            .isEqualTo(
                ExpectedItem(
                    "Krusperhâhnchen",
                    485,
                    quantity = "1.00",
                    unitPrice = 485,
                    taxClass = "B"
                )
            )
    }

    @Test
    fun combineInlineQuantity() {
        assertThat(Suggestion.combine(listOf("G&G HASELNUSS 2,99 € x2 5,98 A")))
            .isEqualTo(
                ExpectedItem("G&G HASELNUSS", 598, quantity = "2", unitPrice = 299, taxClass = "A")
            )
        assertThat(Suggestion.combine(listOf("MEGGLE BAGUET 1,32 € x X 3 3,96 AP")))
            .isEqualTo(
                ExpectedItem("MEGGLE BAGUET", 396, quantity = "3", unitPrice = 132, taxClass = "AP")
            )
        assertThat(Suggestion.combine(listOf("Milr.Grilltal 3, 29 € X 2 X 6,58 A")))
            .isEqualTo(
                ExpectedItem("Milr.Grilltal", 658, quantity = "2", unitPrice = 329, taxClass = "A")
            )
    }

    @Test
    fun insertionKeepsOrderOfReceipt() {
        val rows =
            listOf(
                "REWE",
                "BANANE 1,34 B",
                "0, 750 kg x 1,79 EUR/kg",
                "GOUDA JUNG 3,79 B",
                "BAGUETTE 1,19"
            )
        val items = listOf(ExpectedItem("GOUDA JUNG", 379), ExpectedItem("BAGUETTE", 119))
        assertThat(Suggestion.insertionIndex(rows, items, 1)).isEqualTo(0)
        assertThat(Suggestion.insertionIndex(rows, items, 4)).isEqualTo(1)
        assertThat(Suggestion.insertionIndex(rows, items.take(1), 4)).isEqualTo(1)
    }
}
