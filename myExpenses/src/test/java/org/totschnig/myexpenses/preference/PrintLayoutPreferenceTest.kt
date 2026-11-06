package org.totschnig.myexpenses.preference

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.viewmodel.Account
import org.totschnig.myexpenses.viewmodel.Amount
import org.totschnig.myexpenses.viewmodel.Category
import org.totschnig.myexpenses.viewmodel.ColumnFeed
import org.totschnig.myexpenses.viewmodel.CombinedField
import org.totschnig.myexpenses.viewmodel.Date
import org.totschnig.myexpenses.viewmodel.Notes
import org.totschnig.myexpenses.viewmodel.OriginalAmount
import org.totschnig.myexpenses.viewmodel.Payee
import org.totschnig.myexpenses.viewmodel.ReferenceNumber
import org.totschnig.myexpenses.viewmodel.SimpleField
import org.totschnig.myexpenses.viewmodel.Tags
import org.totschnig.myexpenses.viewmodel.values

/**
 * Pins how the print layout is stored. Layouts on devices and in backups have this format, and it
 * contains the fully qualified names of the classes in org.totschnig.myexpenses.viewmodel.
 */
@RunWith(AndroidJUnit4::class)
class PrintLayoutPreferenceTest : BaseTestWithRepository() {

    private val storedLayout = "[" +
        """{"type":"org.totschnig.myexpenses.viewmodel.Date"},""" +
        """{"type":"org.totschnig.myexpenses.viewmodel.ColumnFeed"},""" +
        """{"type":"org.totschnig.myexpenses.viewmodel.CombinedField","fields":[""" +
        """{"type":"org.totschnig.myexpenses.viewmodel.ReferenceNumber"},""" +
        """{"type":"org.totschnig.myexpenses.viewmodel.Notes"}]}""" +
        "]"

    private val layout = listOf(
        Date,
        ColumnFeed,
        CombinedField(listOf(ReferenceNumber, Notes))
    )

    @Test
    fun withoutStoredValuesTheDefaultsApply() {
        assertThat(prefHandler.printLayout).containsExactly(
            Date,
            ColumnFeed,
            Account,
            Category,
            Tags,
            ColumnFeed,
            Payee,
            CombinedField(listOf(ReferenceNumber, Notes)),
            ColumnFeed,
            OriginalAmount,
            Amount
        ).inOrder()
        assertThat(prefHandler.printLayoutColumnWidths)
            .containsExactly(150, 350, 350, 200).inOrder()
    }

    @Test
    fun layoutIsStoredAsJsonWithClassNames() {
        prefHandler.printLayout = layout
        assertThat(prefHandler.getString(PrefKey.PRINT_LAYOUT, null)).isEqualTo(storedLayout)
    }

    @Test
    fun layoutStoredByAnEarlierVersionIsRead() {
        prefHandler.putString(PrefKey.PRINT_LAYOUT, storedLayout)
        assertThat(prefHandler.printLayout).containsExactlyElementsIn(layout).inOrder()
    }

    @Test
    fun allFieldsSurviveARoundTrip() {
        val all = SimpleField.values + ColumnFeed
        prefHandler.printLayout = all
        assertThat(prefHandler.printLayout).containsExactlyElementsIn(all).inOrder()
    }

    @Test
    fun unreadableLayoutThrowsInsteadOfFallingBackToTheDefault() {
        // Looks like a bug: the getter has no fallback, so a layout that cannot be read makes the
        // print and the layout screen fail until the preference is removed.
        prefHandler.putString(PrefKey.PRINT_LAYOUT, "not json")
        assertThrows(SerializationException::class.java) { prefHandler.printLayout }
        prefHandler.putString(PrefKey.PRINT_LAYOUT, """[{"type":"somewhere.else.Date"}]""")
        assertThrows(SerializationException::class.java) { prefHandler.printLayout }
    }

    @Test
    fun widthsAreStoredCommaSeparatedAndUnreadableEntriesAreSkipped() {
        prefHandler.printLayoutColumnWidths = listOf(10, 20)
        assertThat(prefHandler.getString(PrefKey.PRINT_LAYOUT_COLUMN_WIDTH, null))
            .isEqualTo("10,20")
        prefHandler.putString(PrefKey.PRINT_LAYOUT_COLUMN_WIDTH, "10,x,30")
        assertThat(prefHandler.printLayoutColumnWidths).containsExactly(10, 30).inOrder()
    }

    @Test
    fun fieldsHaveTheirTitlesAndCombinedFieldsJoinThem() {
        assertThat(SimpleField.values.map { it.toString(application, null) }).containsExactly(
            "Date",
            "Category",
            "Tags",
            "Payee",
            "Notes",
            "Amount",
            "Reference Number",
            "Account",
            "Original amount"
        )
        assertThat(CombinedField(listOf(Payee, Notes)).toString(application, null))
            .isEqualTo("Payee / Notes")
    }

    @Test
    fun dateIsCalledTimeWhenTheExtraSaysSo() {
        val extra = Bundle().apply { putBoolean(Date.KEY_IS_TIME_FIELD, true) }
        assertThat(Date.toString(application, extra)).isEqualTo("Time")
        assertThat(Date.toString(application, Bundle())).isEqualTo("Date")
        assertThat(Date.toString(application, null)).isEqualTo("Date")
    }
}
