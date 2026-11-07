package org.totschnig.myexpenses.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class ImportDateFormatTest {

    @Test
    fun testDefaultFormats() {
        assertThat(ImportDateFormat.defaultForLocale(Locale.US))
            .isEqualTo(ImportDateFormat.US)
        assertThat(ImportDateFormat.defaultForLocale(Locale.GERMANY))
            .isEqualTo(ImportDateFormat.EU)
        assertThat(ImportDateFormat.defaultForLocale(Locale.CHINA))
            .isEqualTo(ImportDateFormat.YMD)
    }

    // Names are stored in preferences, and the order and the labels are those of the spinner
    @Test
    fun testStoredNamesOrderAndLabels() {
        assertThat(ImportDateFormat.entries.map { it.name to it.toString() })
            .containsExactly(
                "US" to "MM/dd/yyyy, MM.dd.yy, …",
                "EU" to "dd/MM/yyyy, dd.MM.yy, …",
                "YMD" to "yyyy/MM/dd, yy.MM.dd, …"
            ).inOrder()
    }
}
