package org.totschnig.myexpenses.export.qif

import com.google.common.truth.Truth
import org.junit.Test
import java.util.*

class QifDateFormatTest {

    @Test
    fun testDefaultFormats() {
        Truth.assertThat(QifDateFormat.defaultForLocale(Locale.US)).isEqualTo(QifDateFormat.US)
        Truth.assertThat(QifDateFormat.defaultForLocale(Locale.GERMANY)).isEqualTo(QifDateFormat.EU)
        Truth.assertThat(QifDateFormat.defaultForLocale(Locale.CHINA)).isEqualTo(QifDateFormat.YMD)
    }

    // Names are stored in preferences, and the order and the labels are those of the spinner
    @Test
    fun testStoredNamesOrderAndLabels() {
        Truth.assertThat(QifDateFormat.entries.map { it.name to it.toString() })
            .containsExactly(
                "US" to "MM/dd/yyyy, MM.dd.yy, …",
                "EU" to "dd/MM/yyyy, dd.MM.yy, …",
                "YMD" to "yyyy/MM/dd, yy.MM.dd, …"
            ).inOrder()
    }
}
