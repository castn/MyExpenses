package org.totschnig.myexpenses.next.feature.receipts

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.TestUri
import android.net.Uri
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.totschnig.itemgroupmodel.ProductGroupModel
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.feature.RecognizedBlock
import org.totschnig.myexpenses.feature.RecognizedLine
import org.totschnig.myexpenses.feature.RecognizedText
import org.totschnig.myexpenses.next.data.receipts.BookedTransaction
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelRelease
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModels
import org.totschnig.myexpenses.next.data.receipts.Receipt
import org.totschnig.myexpenses.next.data.receipts.ReceiptDatabase
import org.totschnig.myexpenses.next.data.receipts.ReceiptItem

/** Keeps what is saved in memory and records the calls a view model makes */
class RecordingReceiptDatabase : ReceiptDatabase {
    val saved = HashMap<Long, Receipt>()
    var saveCount = 0
    val savedWaiting = mutableListOf<Pair<Long?, Receipt>>()
    val keptPhotos = mutableListOf<Pair<Uri, Long?>>()
    val deleted = mutableListOf<Long>()
    val assigned = mutableListOf<Pair<Long, Long>>()
    val expenseRanges = mutableListOf<Pair<LocalDate, LocalDate>>()
    var waiting = emptyList<Pair<Long, Receipt>>()
    var expenses = emptyList<BookedTransaction>()

    /** Names with the id of the group the user chose for them */
    var learned = emptyList<Pair<String, String>>()
    var failSaving = false
    var failAssigning = false

    private val changes = MutableStateFlow(0)

    override fun changes(withTransactions: Boolean): Flow<Unit> = changes.map { }

    override fun changesOf(transactionId: Long): Flow<Unit> = changes.map { }

    override fun load(transactionId: Long) = saved[transactionId]

    override fun loadWaiting() = waiting

    override fun loadAllItems() = saved.mapValues { it.value.items }

    override fun loadLearnedItemGroups() = learned

    override fun loadExpenses(from: LocalDate, to: LocalDate): List<BookedTransaction> {
        expenseRanges += from to to
        return expenses
    }

    override fun save(transactionId: Long, receipt: Receipt) {
        if (failSaving) throw IOException("Disk full")
        saveCount++
        saved[transactionId] = receipt
    }

    override fun saveWaiting(receiptId: Long?, receipt: Receipt): Long {
        if (failSaving) throw IOException("Disk full")
        savedWaiting += receiptId to receipt
        return receiptId ?: 1
    }

    override fun assign(receiptId: Long, transactionId: Long) {
        if (failAssigning) throw IOException("Disk full")
        assigned += receiptId to transactionId
    }

    override fun delete(transactionId: Long) {
        deleted += transactionId
        saved -= transactionId
    }

    override fun deleteWaiting(receiptId: Long) {
        waiting = waiting.filter { it.first != receiptId }
    }

    override fun keepPhoto(photo: Uri, transactionId: Long?): Uri {
        keptPhotos += photo to transactionId
        return TestUri("kept:$photo")
    }

    override fun viewableUri(photo: Uri): Uri = TestUri("viewable:$photo")
}

/** Returns what [result] gives for every photo, records the photos it reads */
class FakeTextRecognition(var result: () -> RecognizedText?) : TextRecognition {
    val recognized = mutableListOf<Uri>()
    var scanResult: Uri? = null

    override suspend fun recognize(photo: Uri): RecognizedText? {
        recognized += photo
        return result()
    }

    override suspend fun documentScanIntent(activity: Activity): IntentSender? = null

    override fun documentScanResult(data: Intent?) = scanResult
}

/** No model installed and none offered */
class NoItemGroupModels : ItemGroupModels {
    override fun model(): ProductGroupModel? = null

    override fun install(uri: Uri) = throw IOException("Not readable")

    override fun latestRelease(): ItemGroupModelRelease? = null

    override fun download(release: ItemGroupModelRelease) = throw IOException("No connection")
}

/** A receipt as text recognition reads it: name column and price column as separate blocks */
fun recognizedReceipt(vararg rows: Pair<String, String?>) = RecognizedText(
    listOf(
        RecognizedBlock(rows.mapIndexed { i, (name, _) -> recognizedLine(name, 0, i) }),
        RecognizedBlock(
            rows.mapIndexedNotNull { i, (_, price) -> price?.let { recognizedLine(it, 800, i) } }
        )
    )
)

private fun recognizedLine(text: String, left: Int, row: Int) =
    RecognizedLine(text, Box(left, row * 100, left + 200, row * 100 + 80), emptyList())

fun item(name: String, amount: Long, group: String? = null) =
    ReceiptItem(name, amount, group = group)
