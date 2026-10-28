package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Test
import org.totschnig.myexpenses.feature.recognizedTextFromJson

/** Rows as recognized on the receipts of the fixtures */
class ReceiptTokensTest {

    /** Compact form: numbers by value, units and other signs by kind, words as text */
    private fun tokens(text: String) = ReceiptTokens.tokens(text).joinToString(" ") { token ->
        when (token) {
            is Token.Number -> token.value.toPlainString()
            is Token.Currency -> "€"
            is Token.Times -> "×"
            is Token.Unit -> token.unit.name
            is Token.PerUnit -> "/" + token.unit.name
            is Token.Word -> "'${token.text}'"
        }
    }

    @Test
    fun weightRowWithSpaceInNumber() {
        assertEquals("0.750 KG × 1.79 € /KG", tokens("0, 750 kg x 1,79 EUR/kg"))
    }

    @Test
    fun misreadUnitsAndDoubledSign() {
        assertEquals("2 PIECE × × 1.19", tokens("2 Stk x x 1, 19"))
        assertEquals("1.00 PIECE × 4.85 4.85 'B'", tokens("1,00 Gtúck x 4,85 4,85 B"))
        assertEquals("1.00 PIECE ×", tokens("1,00 5tuck x"))
        assertEquals("0.344 KG × 3.99 € /KG", tokens("0,344 k9 x 3,99 €/k9"))
        assertEquals("3 PIECE ×", tokens("3 ST X"))
    }

    @Test
    fun currencyBeforeAmount() {
        assertEquals("2 × € 0.99 € 1.98 'A'", tokens("2 x €0,99 €1,98 A"))
        assertEquals("'Sunne' € 6.12", tokens("Sunne €6, 12"))
        assertEquals("'Einkaufswert' € 6.12", tokens("Einkaufswert E6,12"))
    }

    @Test
    fun signsGluedToNumbers() {
        assertEquals("'G&G' 'HASELNUSS' 2.99 € × 2", tokens("G&G HASELNUSS 2,99 € x2"))
        assertEquals("'U.H.BUTTER' 'M2' 1.69 € × 2 '-A'", tokens("U.H.BUTTER M2 1,69 €X2-A"))
        assertEquals("6 × 0.75 'Sportness'", tokens("6x 0,75 Sportness"))
        assertEquals("309172 1 3.79 3.79 'A'", tokens("309172 1 3,79 3,79A"))
        assertEquals("10 17.90 'A'", tokens("10 17.90.A"))
    }

    @Test
    fun numbersInNamesStayWords() {
        assertEquals("'eelin' 'dick' '9St' 1.95", tokens("eelin dick 9St 1,95"))
        assertEquals("'LEIT2' 'PIN.N.0,75L' 9.99 'B'", tokens("LEIT2 PIN.N.0,75L 9,99 B"))
    }

    @Test
    fun negativeAmountsAndStrayMarks() {
        assertEquals("-0.30 'B'", tokens("0,30- B"))
        assertEquals("'Staffelrabatt' -1.05", tokens("Staffelrabatt -1,05"))
        assertEquals("9.99", tokens("9,99."))
    }

    @Test
    fun amounts() {
        val (weight, price) = ReceiptTokens.tokens("0,344 kg x 3,99").filterIsInstance<Token.Number>()
        assertEquals(false, weight.isAmount)
        assertEquals(true, price.isAmount)
        assertEquals(399L, price.cents)
    }

    @Test
    fun positionsPointIntoTheRow() {
        val text = "KARTOFFELN 2, 22 B"
        val tokens = ReceiptTokens.tokens(text)
        assertEquals("2, 22", text.substring(tokens[1].start, tokens[1].end))
        assertEquals("B", text.substring(tokens[2].start, tokens[2].end))
    }

    @Test
    fun everyRecognizedLineOfTheFixturesIsSplit() {
        ReceiptFixtures.dir().listFiles { file -> file.name.endsWith(".json") && !file.name.endsWith(".expected.json") }!!
            .flatMap { recognizedTextFromJson(it.readText()).lines }
            .forEach { line ->
                ReceiptTokens.tokens(line.text).forEach { token ->
                    assertEquals(line.text, token.text.replace(" ", ""), line.text.substring(token.start, token.end).replace(" ", ""))
                }
            }
    }
}
