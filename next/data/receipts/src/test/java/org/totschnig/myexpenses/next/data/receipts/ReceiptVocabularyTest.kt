package org.totschnig.myexpenses.next.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptVocabularyTest {

    private val german = ReceiptVocabulary.GERMAN

    @Test
    fun totalAsPrintedAndMisread() {
        listOf("SUMME EUR 12,15", "Zu zahlen 3,48", "2 Artikel TOTAL 7,50 €", "GesamtsUmMe: 10,25", "SUNME [7] 8,91", "SUMAE 8,91", "Sunne €6, 12")
            .forEach { assertTrue(it, german.isTotal(it)) }
        listOf("Zw.Summe 43,10", "SAHNE 1,09", "SUPPE 2,49", "KARTOFFELN 2,22").forEach { assertFalse(it, german.isTotal(it)) }
    }

    @Test
    fun subtotalAndPayment() {
        assertTrue(german.isSubtotal("Zwischensumme 9,23"))
        assertTrue(german.isPayment("Geg. EC-Cash EUR 12,15"))
        assertTrue(german.isPayment("Kartenzahlung EUR 9,23"))
        assertFalse(german.isPayment("BANANE 1,34 B"))
    }

    @Test
    fun cardAndCash() {
        listOf("Geg. EC-Cash EUR 12,15", "Geg. FC-Cash", "Gegeben EC Karte:", "ARTENZAHLUNG EUR", "girocard kontaktlos", "Visa")
            .forEach { assertTrue(it, german.isCard(it)) }
        listOf("BAR 20,00", "Bargeld", "Rückgeld 7,85", "Zurück").forEach { assertTrue(it, german.isCash(it)) }
        listOf("Balea Med Ultra Sen.Haut Bar.C", "BARILLA 1,99").forEach { assertFalse(it, german.isCash(it) || german.isCard(it)) }
    }

    @Test
    fun months() {
        assertEquals(10, german.monthOf("Okt."))
        assertEquals(3, german.monthOf("März"))
        assertEquals(3, german.monthOf("Mar"))
        assertNull(german.monthOf("Au"))
        assertNull(german.monthOf("Artikel"))
    }

    @Test
    fun units() {
        assertEquals(ReceiptUnit.KG, german.unitOf("k9"))
        assertEquals(ReceiptUnit.PIECE, german.unitOf("Stk."))
        assertEquals(ReceiptUnit.PIECE, german.unitOf("Gtúck"))
        assertNull(german.unitOf("g"))
    }
}
