package org.totschnig.myexpenses.export.pdf

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.ImageRenderInfo
import com.itextpdf.text.pdf.parser.PdfReaderContentParser
import com.itextpdf.text.pdf.parser.RenderListener
import com.itextpdf.text.pdf.parser.TextRenderInfo
import java.io.File
import java.time.LocalDateTime
import kotlin.math.abs
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.db2.createSplitTransaction
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.preference.ColorSource
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.printLayout
import org.totschnig.myexpenses.preference.printLayoutColumnWidths
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.filter.Criterion
import org.totschnig.myexpenses.util.toEpoch
import org.totschnig.myexpenses.viewmodel.ColumnFeed
import org.totschnig.myexpenses.viewmodel.Field
import org.totschnig.myexpenses.viewmodel.data.PageAccount

/**
 * Prints into a temporary folder and reads the text back from the PDF with iText. Robolectric has
 * no /system/fonts, so PdfHelper falls back to Helvetica: the font selection stays untested and
 * characters outside of WinAnsi, like the arrows of transfers, are lost in the output. Comments
 * starting with "Looks like a bug" mark behaviour that is pinned, not endorsed.
 */
abstract class BasePdfTest : BaseTestWithRepository() {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUpPdfTest() {
        prefHandler.putString(PrefKey.HOME_CURRENCY, HOME)
    }

    /**
     * A folder for each print: the file name has a resolution of one second and printing twice
     * into the same folder within that second fails.
     */
    protected fun newDestDir(): DocumentFile = DocumentFile.fromFile(temporaryFolder.newFolder())

    /** The file scheme makes the second value the absolute path. */
    protected fun Pair<Uri, String>.toPrinted() = PrintedPdf.read(File(first.path!!), first, second)

    /** The default minus sign is outside of WinAnsi. This format replaces it with an N. */
    protected fun showNegativeSignInPdf() {
        prefHandler.putString(PrefKey.CUSTOM_DECIMAL_FORMAT, "#,##0.00;N#,##0.00")
    }

    protected fun useLayout(vararg columns: List<Field>, widths: List<Int> = columns.map { 100 }) {
        prefHandler.printLayout = columns.flatMapIndexed { index, fields ->
            if (index == 0) fields else listOf(ColumnFeed) + fields
        }
        prefHandler.printLayoutColumnWidths = widths
    }

    protected fun pageAccount(
        id: Long,
        label: String = "Cash",
        currency: String = HOME,
        grouping: Grouping = Grouping.NONE,
        openingBalance: Long = 0,
        currentBalance: Long = 0
    ) = PageAccount(
        id = id,
        label = label,
        currencyUnit = currencyContext[currency],
        grouping = grouping,
        openingBalance = openingBalance,
        currentBalance = currentBalance
    )

    protected fun print(
        account: PageAccount,
        filter: Criterion? = null,
        colorSource: ColorSource = ColorSource.TYPE
    ) = PdfPrinter.print(application, account, newDestDir(), filter, colorSource).toPrinted()

    protected fun book(
        accountId: Long,
        amount: Long = -500,
        categoryId: Long? = null,
        payeeId: Long? = null,
        comment: String? = null,
        referenceNumber: String? = null,
        crStatus: CrStatus = CrStatus.UNRECONCILED,
        date: LocalDateTime = DATE,
        originalAmount: Long? = null,
        originalCurrency: String? = null
    ) = repository.insertTransaction(
        accountId = accountId,
        amount = amount,
        categoryId = categoryId,
        payeeId = payeeId,
        comment = comment,
        referenceNumber = referenceNumber,
        crStatus = crStatus,
        date = date,
        originalAmount = originalAmount,
        originalCurrency = originalCurrency
    ).data.id

    protected fun insertSplit(
        accountId: Long,
        payeeId: Long? = null,
        partCategoryIds: List<Long>,
        partAmount: Long = -300,
        date: LocalDateTime = DATE,
        comment: (Int) -> String = { "Part $it" }
    ): Long {
        val epoch = date.toEpoch()
        return repository.createSplitTransaction(
            Transaction(
                accountId = accountId,
                amount = partAmount * partCategoryIds.size,
                categoryId = SPLIT_CATID,
                payeeId = payeeId,
                date = epoch,
                uuid = generateUuid()
            ),
            partCategoryIds.mapIndexed { index, categoryId ->
                Transaction(
                    accountId = accountId,
                    amount = partAmount,
                    categoryId = categoryId,
                    comment = comment(index),
                    date = epoch,
                    uuid = generateUuid()
                )
            }
        ).data.id
    }

    companion object {
        const val HOME = "EUR"
        val DATE: LocalDateTime = LocalDateTime.of(2025, 3, 15, 10, 30)
    }
}

data class PrintedText(
    val text: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val endX: Float,
    val color: BaseColor?
)

/**
 * What a reader of the PDF finds. Every text operator of iText is a piece, so a cell that wraps
 * yields several pieces. Tests use short texts for this reason.
 */
class PrintedPdf(
    val file: File,
    val uri: Uri,
    val displayName: String,
    val pieces: List<PrintedText>,
    val pageSizes: List<Rectangle>,
    val pageContents: List<String>,
    val info: Map<String, String>
) {
    val texts: List<String> get() = pieces.map { it.text }

    /**
     * Rows of pieces, top to bottom, each left to right. Fonts of different sizes in a row have
     * slightly different baselines, so this allows for some distance.
     */
    val lines: List<String>
        get() = pieces
            .sortedWith(compareBy({ it.page }, { -it.y }))
            .fold(mutableListOf<MutableList<PrintedText>>()) { rows, piece ->
                val row = rows.lastOrNull()
                val sameRow = row != null && row.first().page == piece.page &&
                    row.first().y - piece.y < 3f
                if (sameRow) row!!.add(piece) else rows.add(mutableListOf(piece))
                rows
            }
            .map { row -> row.sortedBy { it.x }.joinToString(" | ") { it.text } }

    fun piece(text: String) = pieces.first { it.text == text }

    /** Strokes in red are only used for the line through voided transactions. */
    val hasRedLine: Boolean get() = pageContents.any { "1 0 0 RG" in it }

    fun columnOf(text: String, candidates: List<Float>): Int {
        val x = piece(text).x
        return candidates.indices.minBy { abs(candidates[it] - x) }
    }

    companion object {
        fun read(file: File, uri: Uri, displayName: String): PrintedPdf {
            val reader = PdfReader(file.absolutePath)
            try {
                val parser = PdfReaderContentParser(reader)
                val pieces = mutableListOf<PrintedText>()
                val pages = 1..reader.numberOfPages
                pages.forEach { parser.processContent(it, Collector(it, pieces)) }
                return PrintedPdf(
                    file = file,
                    uri = uri,
                    displayName = displayName,
                    pieces = pieces,
                    pageSizes = pages.map { reader.getPageSizeWithRotation(it) },
                    pageContents = pages.map {
                        String(reader.getPageContent(it), Charsets.ISO_8859_1)
                    },
                    info = reader.info
                )
            } finally {
                reader.close()
            }
        }
    }

    private class Collector(private val page: Int, private val pieces: MutableList<PrintedText>) :
        RenderListener {
        override fun renderText(renderInfo: TextRenderInfo) {
            val baseline = renderInfo.baseline
            val start = baseline.startPoint
            pieces.add(
                PrintedText(
                    text = renderInfo.text,
                    page = page,
                    x = start[0],
                    y = start[1],
                    endX = baseline.endPoint[0],
                    color = renderInfo.fillColor
                )
            )
        }

        override fun beginTextBlock() = Unit
        override fun endTextBlock() = Unit
        override fun renderImage(renderInfo: ImageRenderInfo) = Unit
    }
}

// The colours of LazyFontSelector.FontType.
val INCOME_COLOR = 0xFF006800.toInt()
val EXPENSE_COLOR = 0xFF800000.toInt()
val TRANSFER_COLOR = 0xFF000080.toInt()
val NORMAL_COLOR = 0xFF000000.toInt()
