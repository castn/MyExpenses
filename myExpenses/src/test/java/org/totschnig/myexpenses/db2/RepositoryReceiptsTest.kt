package org.totschnig.myexpenses.db2

import android.content.ContentUris
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.BaseTestWithRepository
import org.totschnig.myexpenses.feature.Box
import org.totschnig.myexpenses.model2.Receipt
import org.totschnig.myexpenses.model2.ReceiptItem
import org.totschnig.myexpenses.provider.RECEIPT_ITEMS_URI
import org.totschnig.myexpenses.provider.TransactionProvider
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryReceiptsTest : BaseTestWithRepository() {

    private val receipt = Receipt(
        merchant = "REWE",
        total = 399,
        items = listOf(
            ReceiptItem(
                "KICHERERBSEN", 177, BigDecimal("3"), unitPrice = 59, taxClass = "B", box = Box(89, 1563, 2673, 1837),
                group = "pantry"
            ),
            ReceiptItem("BANANEN", 153, BigDecimal("0.512"), unit = "kg", unitPrice = 299, taxClass = "B"),
            ReceiptItem("PFAND", 25),
            ReceiptItem("LEERGUT", -25),
            ReceiptItem("JA FRISCHHALTE", 69, taxClass = "A")
        )
    )

    private fun itemCount() = contentResolver.query(RECEIPT_ITEMS_URI, null, null, null, null)!!
        .use { it.count }

    @Test
    fun saveLoadAndReplace() {
        val transactionId = repository.insertTransaction(insertAccount("Test"), -399).id
        assertThat(repository.loadReceipt(transactionId)).isNull()

        repository.saveReceipt(transactionId, receipt)
        assertThat(repository.loadReceipt(transactionId)).isEqualTo(receipt)

        val corrected = receipt.copy(items = receipt.items.dropLast(1))
        repository.saveReceipt(transactionId, corrected)
        assertThat(repository.loadReceipt(transactionId)).isEqualTo(corrected)
        assertThat(itemCount()).isEqualTo(4)
    }

    @Test
    fun photoIsLinkedToAttachment() {
        val transactionId = repository.insertTransaction(insertAccount("Test"), -399).id
        val photo = android.net.Uri.parse("file:///receipt.jpg")
        repository.addAttachments(transactionId, listOf(photo))
        repository.saveReceipt(transactionId, receipt.copy(photo = photo))
        assertThat(repository.loadReceipt(transactionId)!!.photo).isEqualTo(photo)
    }

    @Test
    fun deleteReceipt() {
        val transactionId = repository.insertTransaction(insertAccount("Test"), -399).id
        repository.saveReceipt(transactionId, receipt)
        repository.deleteReceipt(transactionId)
        assertThat(repository.loadReceipt(transactionId)).isNull()
        assertThat(itemCount()).isEqualTo(0)
    }

    @Test
    fun deletingTransactionDeletesReceipt() {
        val transactionId = repository.insertTransaction(insertAccount("Test"), -399).id
        repository.saveReceipt(transactionId, receipt)
        contentResolver.delete(
            ContentUris.withAppendedId(TransactionProvider.TRANSACTIONS_URI, transactionId), null, null
        )
        assertThat(repository.loadReceipt(transactionId)).isNull()
        assertThat(itemCount()).isEqualTo(0)
    }

    @Test
    fun groupChosenByUserIsLearnedAndAppliedToEarlierReceipts() {
        val account = insertAccount("Test")
        val earlier = repository.insertTransaction(account, -300).id
        val chosenBefore = repository.insertTransaction(account, -300).id
        val latest = repository.insertTransaction(account, -300).id
        repository.saveReceipt(
            earlier, Receipt("EDEKA", 300, listOf(ReceiptItem("MEGGLE BAGUET", 300, group = "bakery")))
        )
        repository.saveReceipt(
            chosenBefore,
            Receipt("EDEKA", 300, listOf(ReceiptItem("Meggle Baguet", 300, group = "other", groupByUser = true)))
        )
        assertThat(repository.loadLearnedItemGroups()).containsExactly("Meggle Baguet" to "other")

        repository.saveReceipt(
            latest,
            Receipt("EDEKA", 300, listOf(ReceiptItem("MEGGLE BAGUET", 300, group = "ready_meals", groupByUser = true)))
        )
        assertThat(repository.loadLearnedItemGroups())
            .containsExactly("Meggle Baguet" to "other", "MEGGLE BAGUET" to "ready_meals").inOrder()
        // the automatic group of the same product follows, the earlier choice of the user stays
        assertThat(repository.loadReceipt(earlier)!!.items.single().group).isEqualTo("ready_meals")
        assertThat(repository.loadReceipt(chosenBefore)!!.items.single().group).isEqualTo("other")
    }

    @Test
    fun allItemsByTransaction() {
        val account = insertAccount("Test")
        val first = repository.insertTransaction(account, -399).id
        val second = repository.insertTransaction(account, -300).id
        repository.saveReceipt(first, receipt)
        repository.saveReceipt(second, Receipt("EDEKA", 300, listOf(ReceiptItem("MEGGLE BAGUET", 300, group = "bakery"))))
        val all = repository.loadAllReceiptItems()
        assertThat(all.keys).containsExactly(first, second)
        assertThat(all[first]).isEqualTo(receipt.items)
        assertThat(all[second]!!.single().group).isEqualTo("bakery")
    }

    private val photo = android.net.Uri.parse("file:///receipt_waiting.jpg")

    private val waiting = receipt.copy(photo = photo, date = LocalDate.of(2026, 10, 9), cardAmount = 2399)

    @Test
    fun receiptWithoutTransactionIsSavedReplacedAndDeleted() {
        val id = repository.saveReceiptWithoutTransaction(null, waiting)
        assertThat(repository.loadReceiptsWithoutTransaction()).containsExactly(id to waiting)

        val corrected = waiting.copy(merchant = "REWE Markt", items = waiting.items.take(1))
        assertThat(repository.saveReceiptWithoutTransaction(id, corrected)).isEqualTo(id)
        assertThat(repository.loadReceiptsWithoutTransaction()).containsExactly(id to corrected)
        assertThat(itemCount()).isEqualTo(1)
        assertThat(repository.loadAllReceiptItems()).isEmpty()

        repository.deleteReceiptWithoutTransaction(id)
        assertThat(repository.loadReceiptsWithoutTransaction()).isEmpty()
        assertThat(itemCount()).isEqualTo(0)
    }

    @Test
    fun assigningKeepsTheItemsAndMakesThePhotoAnAttachment() {
        val transactionId = repository.insertTransaction(insertAccount("Test"), -2399).id
        repository.saveReceipt(transactionId, Receipt("ALT", 100, listOf(ReceiptItem("ALT", 100))))
        val id = repository.saveReceiptWithoutTransaction(null, waiting)

        repository.assignReceipt(id, transactionId)

        assertThat(repository.loadReceiptsWithoutTransaction()).isEmpty()
        assertThat(repository.loadReceipt(transactionId)).isEqualTo(waiting)
        assertThat(repository.loadAttachmentIds(transactionId)).hasSize(1)
        assertThat(itemCount()).isEqualTo(waiting.items.size)
    }

    @Test
    fun dateAndCardAmountOfATransactionsReceipt() {
        val transactionId = repository.insertTransaction(insertAccount("Test"), -2399).id
        val withPayment = receipt.copy(date = LocalDate.of(2026, 10, 9), cardAmount = 2399)
        repository.saveReceipt(transactionId, withPayment)
        assertThat(repository.loadReceipt(transactionId)).isEqualTo(withPayment)
    }
}
