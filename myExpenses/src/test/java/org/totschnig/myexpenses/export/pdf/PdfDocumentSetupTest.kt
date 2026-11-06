package org.totschnig.myexpenses.export.pdf

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.itextpdf.text.PageSize
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefKey

/**
 * Pins what the print preferences do to the document: paper, margins, font size, and the texts in
 * header and footer of each page.
 */
@RunWith(AndroidJUnit4::class)
class PdfDocumentSetupTest : BasePdfTest() {

    private fun document() = PdfPrinter.getDocument(application, prefHandler)

    private fun putFloat(key: PrefKey, value: Float) {
        application.settings.edit { putFloat(prefHandler.getKey(key), value) }
    }

    private val telephonyManager
        get() = shadowOf(
            application.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        )

    private fun printSomething(rows: Int = 1): PrintedPdf {
        val account = insertAccount("Cash")
        repeat(rows) { book(account, comment = "Row $it") }
        return print(pageAccount(account))
    }

    // Paper

    @Test
    fun defaultPaperIsA4Portrait() {
        val pageSize = document().pageSize
        assertThat(pageSize.width).isEqualTo(PageSize.A4.width)
        assertThat(pageSize.height).isEqualTo(PageSize.A4.height)
    }

    @Test
    fun countryOfTheNetworkDecidesOnLetter() {
        telephonyManager.setNetworkCountryIso("us")
        assertThat(PdfPrinter.defaultPaperSize(application)).isEqualTo("LETTER")
        assertThat(document().pageSize.height).isEqualTo(PageSize.LETTER.height)
        telephonyManager.setNetworkCountryIso("de")
        assertThat(PdfPrinter.defaultPaperSize(application)).isEqualTo("A4")
    }

    @Test
    fun countryOfTheSimIsTheFallback() {
        telephonyManager.setNetworkCountryIso("")
        telephonyManager.setSimCountryIso("ca")
        assertThat(PdfPrinter.defaultPaperSize(application)).isEqualTo("LETTER")
    }

    @Test
    fun allLetterCountries() {
        val letter = listOf(
            "ph", "us", "bz", "ca", "pr", "cl", "co", "cr", "gt", "mx", "ni", "pa", "sv", "ve"
        )
        letter.forEach {
            telephonyManager.setNetworkCountryIso(it)
            assertThat(PdfPrinter.defaultPaperSize(application)).isEqualTo("LETTER")
        }
    }

    @Test
    fun storedPaperFormatWins() {
        telephonyManager.setNetworkCountryIso("us")
        prefHandler.putString(PrefKey.PRINT_PAPER_FORMAT, "A5")
        assertThat(document().pageSize.height).isEqualTo(PageSize.A5.height)
    }

    @Test
    fun everyOfferedPaperFormatExists() {
        application.resources.getStringArray(R.array.pref_print_paper_size_entries).forEach {
            prefHandler.putString(PrefKey.PRINT_PAPER_FORMAT, it)
            assertThat(document().pageSize.width).isGreaterThan(0f)
        }
    }

    @Test
    fun unknownPaperFormatThrows() {
        prefHandler.putString(PrefKey.PRINT_PAPER_FORMAT, "A99")
        assertThrows(NoSuchFieldException::class.java) { document() }
    }

    @Test
    fun landscapeRotatesThePage() {
        prefHandler.putString(PrefKey.PRINT_PAPER_ORIENTATION, "LANDSCAPE")
        val pageSize = document().pageSize
        assertThat(pageSize.width).isEqualTo(PageSize.A4.height)
        assertThat(pageSize.height).isEqualTo(PageSize.A4.width)
    }

    @Test
    fun printedPageHasTheConfiguredSize() {
        prefHandler.putString(PrefKey.PRINT_PAPER_FORMAT, "LETTER")
        prefHandler.putString(PrefKey.PRINT_PAPER_ORIENTATION, "LANDSCAPE")
        val pageSize = printSomething().pageSizes.single()
        assertThat(pageSize.width).isEqualTo(PageSize.LETTER.height)
        assertThat(pageSize.height).isEqualTo(PageSize.LETTER.width)
    }

    // Margins

    @Test
    fun marginsAreSharesOfThePageWithTheseDefaults() {
        val document = document()
        assertThat(document.leftMargin()).isWithin(0.01f).of(PageSize.A4.width * 0.06f)
        assertThat(document.rightMargin()).isWithin(0.01f).of(PageSize.A4.width * 0.06f)
        assertThat(document.topMargin()).isWithin(0.01f).of(PageSize.A4.height * 0.04f)
        assertThat(document.bottomMargin()).isWithin(0.01f).of(PageSize.A4.height * 0.04f)
    }

    @Test
    fun storedMarginsAreUsed() {
        putFloat(PrefKey.PRINT_MARGIN_LEFT, 0.1f)
        putFloat(PrefKey.PRINT_MARGIN_RIGHT, 0.2f)
        putFloat(PrefKey.PRINT_MARGIN_TOP, 0.1f)
        putFloat(PrefKey.PRINT_MARGIN_BOTTOM, 0.2f)
        val document = document()
        assertThat(document.leftMargin()).isWithin(0.01f).of(PageSize.A4.width * 0.1f)
        assertThat(document.rightMargin()).isWithin(0.01f).of(PageSize.A4.width * 0.2f)
        assertThat(document.topMargin()).isWithin(0.01f).of(PageSize.A4.height * 0.1f)
        assertThat(document.bottomMargin()).isWithin(0.01f).of(PageSize.A4.height * 0.2f)
    }

    @Test
    fun aHeaderOrFooterKeepsRoomOfTwiceTheFontSize() {
        putFloat(PrefKey.PRINT_MARGIN_TOP, 0f)
        putFloat(PrefKey.PRINT_MARGIN_BOTTOM, 0f)
        putFloat(PrefKey.PRINT_FONT_SIZE, 20f)
        assertThat(document().topMargin()).isEqualTo(0f)
        assertThat(document().bottomMargin()).isEqualTo(0f)
        prefHandler.putString(PrefKey.PRINT_HEADER_RIGHT, "x")
        assertThat(document().topMargin()).isEqualTo(40f)
        assertThat(document().bottomMargin()).isEqualTo(0f)
        prefHandler.putString(PrefKey.PRINT_FOOTER_CENTER, "x")
        assertThat(document().bottomMargin()).isEqualTo(40f)
    }

    @Test
    fun aLargerMarginThanTheRoomForTheHeaderIsKept() {
        prefHandler.putString(PrefKey.PRINT_HEADER_LEFT, "x")
        assertThat(document().topMargin()).isWithin(0.01f).of(PageSize.A4.height * 0.04f)
    }

    // Font

    @Test
    fun fontSizeScalesTheText() {
        val account = insertAccount("Cash")
        book(account)
        val width = { pdf: PrintedPdf -> pdf.piece("Date").let { it.endX - it.x } }
        val normal = width(print(pageAccount(account)))
        putFloat(PrefKey.PRINT_FONT_SIZE, 6f)
        assertThat(width(print(pageAccount(account)))).isWithin(0.01f).of(normal / 2)
    }

    // Header and footer

    @Test
    fun aFreshInstallationWithoutDefaultValuesHasNoHeaderOrFooter() {
        val pdf = printSomething()
        assertThat(pdf.texts).containsNoneOf("My Expenses", "1")
    }

    @Test
    fun defaultsOfTheXmlPutDateGeneratorAndPageOnThePage() {
        PreferenceManager.setDefaultValues(application, R.xml.preferences_print, true)
        assertThat(prefHandler.getString(PrefKey.PRINT_HEADER_LEFT)).isNull()
        assertThat(prefHandler.getString(PrefKey.PRINT_HEADER_CENTER)).isEqualTo("{date}")
        assertThat(prefHandler.getString(PrefKey.PRINT_HEADER_RIGHT)).isNull()
        assertThat(prefHandler.getString(PrefKey.PRINT_FOOTER_LEFT)).isEqualTo("{generator}")
        assertThat(prefHandler.getString(PrefKey.PRINT_FOOTER_CENTER)).isNull()
        assertThat(prefHandler.getString(PrefKey.PRINT_FOOTER_RIGHT)).isEqualTo("{page}")
        assertThat(prefHandler.getString(PrefKey.PRINT_PAPER_ORIENTATION)).isEqualTo("PORTRAIT")
        // The seek bars do not store their defaults, the printer has its own copy of the values.
        assertThat(prefHandler.getFloat(PrefKey.PRINT_FONT_SIZE, -1f)).isEqualTo(-1f)
        assertThat(prefHandler.getFloat(PrefKey.PRINT_MARGIN_TOP, -1f)).isEqualTo(-1f)
        val today = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        assertThat(printSomething().texts).containsAtLeast(today, "My Expenses", "1")
    }

    @Test
    fun placeholdersAreReplacedInsideOfOtherText() {
        prefHandler.putString(PrefKey.PRINT_HEADER_LEFT, "By {generator}, page {page}")
        prefHandler.putString(PrefKey.PRINT_FOOTER_LEFT, "{unknown} text")
        assertThat(printSomething().texts)
            .containsAtLeast("By My Expenses, page 1", "{unknown} text")
    }

    @Test
    fun theSixPositionsAreAlignedInsideOfTheMargins() {
        prefHandler.putString(PrefKey.PRINT_HEADER_LEFT, "HL")
        prefHandler.putString(PrefKey.PRINT_HEADER_CENTER, "HC")
        prefHandler.putString(PrefKey.PRINT_HEADER_RIGHT, "HR")
        prefHandler.putString(PrefKey.PRINT_FOOTER_LEFT, "FL")
        prefHandler.putString(PrefKey.PRINT_FOOTER_CENTER, "FC")
        prefHandler.putString(PrefKey.PRINT_FOOTER_RIGHT, "FR")
        val document = document()
        val left = document.leftMargin()
        val right = PageSize.A4.width - document.rightMargin()
        val top = PageSize.A4.height - document.topMargin() + 10f
        val bottom = document.bottomMargin() - 22f
        val pdf = printSomething()
        listOf("HL", "FL").forEach { assertThat(pdf.piece(it).x).isWithin(0.1f).of(left) }
        listOf("HR", "FR").forEach { assertThat(pdf.piece(it).endX).isWithin(0.1f).of(right) }
        listOf("HC", "FC").forEach {
            val piece = pdf.piece(it)
            assertThat((piece.x + piece.endX) / 2).isWithin(0.1f).of(PageSize.A4.width / 2)
        }
        listOf("HL", "HC", "HR").forEach { assertThat(pdf.piece(it).y).isWithin(0.1f).of(top) }
        listOf("FL", "FC", "FR").forEach { assertThat(pdf.piece(it).y).isWithin(0.1f).of(bottom) }
    }

    @Test
    fun everyPageGetsItsOwnNumberAndTheColumnTitlesAgain() {
        prefHandler.putString(PrefKey.PRINT_FOOTER_RIGHT, "Page {page}")
        val pdf = printSomething(rows = 60)
        val pages = pdf.pageSizes.size
        assertThat(pages).isGreaterThan(1)
        assertThat(pdf.pieces.filter { it.text.startsWith("Page ") }.map { it.text })
            .containsExactlyElementsIn((1..pages).map { "Page $it" })
        (1..pages).forEach { page ->
            assertThat(pdf.pieces.filter { it.page == page }.map { it.text }).contains("Date")
        }
        assertThat(pdf.texts.count { it == "Cash" }).isEqualTo(1)
        assertThat(pdf.texts.count { it.startsWith("Row ") }).isEqualTo(60)
    }
}
