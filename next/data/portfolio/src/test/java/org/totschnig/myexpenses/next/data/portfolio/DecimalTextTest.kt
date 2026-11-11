package org.totschnig.myexpenses.next.data.portfolio

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecimalTextTest {

    @Test
    fun aSingleSeparatorIsTheDecimalOne() {
        assertEquals(BigDecimal("0.0125"), parseDecimalText("0,0125"))
        assertEquals(BigDecimal("1.5"), parseDecimalText("1.5"))
        assertEquals(BigDecimal("1.234"), parseDecimalText("1.234"))
    }

    @Test
    fun withBothSeparatorsTheLastIsTheDecimalOne() {
        assertEquals(BigDecimal("1234.5"), parseDecimalText("1.234,5"))
        assertEquals(BigDecimal("1234.5"), parseDecimalText("1,234.50"))
    }

    @Test
    fun aRepeatedSeparatorGroupsThousands() {
        assertEquals(BigDecimal("1234567"), parseDecimalText("1.234.567"))
        assertEquals(BigDecimal("60000"), parseDecimalText("60000"))
    }

    @Test
    fun noNumber() {
        assertNull(parseDecimalText(""))
        assertNull(parseDecimalText(","))
        assertNull(parseDecimalText("12a"))
        assertNull(parseDecimalText("-3"))
    }

    @Test
    fun formatsWithoutTrailingZeros() {
        assertEquals("180,5", formatDecimalText(BigDecimal("180.500"), ','))
        assertEquals("60000", formatDecimalText(BigDecimal("6E+4"), ','))
        assertEquals("0,015", formatMinorText(1_500_000, 8, ','))
        assertEquals("4", formatMinorText(4, 0, ','))
    }
}
