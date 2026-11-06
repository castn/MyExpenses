package org.totschnig.fixturecollector

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountsTest {

    @Test
    fun parse() {
        assertThat(parseCents("1,79")).isEqualTo(179)
        assertThat(parseCents("1.79")).isEqualTo(179)
        assertThat(parseCents("-0,30")).isEqualTo(-30)
        assertThat(parseCents("0,30-")).isEqualTo(-30)
        assertThat(parseCents("12")).isEqualTo(1200)
        assertThat(parseCents("2,5")).isEqualTo(250)
        assertThat(parseCents("abc")).isNull()
        assertThat(parseCents("")).isNull()
    }

    @Test
    fun format() {
        assertThat(formatCents(179)).isEqualTo("1,79")
        assertThat(formatCents(5)).isEqualTo("0,05")
        assertThat(formatCents(-30)).isEqualTo("-0,30")
    }
}
