package org.totschnig.myexpenses.next.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AccountDraftTest {

    @Test
    fun emptyIsZero() {
        assertEquals(BigDecimal.ZERO, parseAmount(""))
        assertEquals(BigDecimal.ZERO, parseAmount("  "))
    }

    @Test
    fun commaAndPointMeanTheSame() {
        assertEquals(BigDecimal("12.50"), parseAmount("12,50"))
        assertEquals(BigDecimal("12.50"), parseAmount("12.50"))
    }

    @Test
    fun negativeWithEitherMinus() {
        assertEquals(BigDecimal("-3.2"), parseAmount("-3,2"))
        assertEquals(BigDecimal("-3.2"), parseAmount("−3,2"))
    }

    @Test
    fun thousandsSeparatorsAreNoNumber() {
        assertNull(parseAmount("1.234,56"))
        assertNull(parseAmount("abc"))
    }

    @Test
    fun minorUnitsRoundToTheCurrency() {
        assertEquals(1250L, toMinorUnits(BigDecimal("12.5"), 2))
        assertEquals(13L, toMinorUnits(BigDecimal("12.5"), 0))
        assertEquals(1235L, toMinorUnits(BigDecimal("1.2345"), 3))
    }

    @Test
    fun minorUnitsThatDoNotFitAreNull() {
        assertNull(toMinorUnits(BigDecimal("1e30"), 2))
    }
}
