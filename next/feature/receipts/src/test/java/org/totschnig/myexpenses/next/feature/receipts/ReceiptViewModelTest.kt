package org.totschnig.myexpenses.next.feature.receipts

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.receipts.BookedTransaction
import org.totschnig.myexpenses.next.data.receipts.Receipt
import org.totschnig.myexpenses.next.data.receipts.ReceiptDatabase
import org.totschnig.myexpenses.next.data.receipts.ReceiptItem
import org.totschnig.myexpenses.next.data.receipts.ReceiptTarget

// Receipts without photo: android.net.Uri cannot be created in a JVM test
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val receipt = Receipt("REWE", 300, listOf(ReceiptItem("Milch", 100), ReceiptItem("Brot", 200)))

    private class FakeReceiptDatabase : ReceiptDatabase {
        val saved = HashMap<Long, Receipt>()
        val assigned = mutableListOf<Pair<Long, Long>>()
        val deletedWaiting = mutableListOf<Long>()
        override fun changes(withTransactions: Boolean): Flow<Unit> = flowOf(Unit)
        override fun changesOf(transactionId: Long): Flow<Unit> = flowOf(Unit)
        override fun load(transactionId: Long) = saved[transactionId]
        override fun loadWaiting() = emptyList<Pair<Long, Receipt>>()
        override fun loadAllItems() = emptyMap<Long, List<ReceiptItem>>()
        override fun loadLearnedItemGroups() = emptyList<Pair<String, String>>()
        override fun loadExpenses(from: LocalDate, to: LocalDate) = emptyList<BookedTransaction>()
        override fun save(transactionId: Long, receipt: Receipt) {
            saved[transactionId] = receipt
        }

        override fun saveWaiting(receiptId: Long?, receipt: Receipt) = receiptId ?: 1
        override fun assign(receiptId: Long, transactionId: Long) {
            assigned += receiptId to transactionId
        }

        override fun delete(transactionId: Long) {
            saved -= transactionId
        }

        override fun deleteWaiting(receiptId: Long) {
            deletedWaiting += receiptId
        }

        override fun keepPhoto(photo: Uri, transactionId: Long?) = photo
        override fun viewableUri(photo: Uri) = photo
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withViewModel(
        database: FakeReceiptDatabase = FakeReceiptDatabase(),
        block: suspend (ReceiptViewModel) -> Unit,
    ) = runTest(dispatcher) {
        val viewModel = ReceiptViewModel(
            SavedStateHandle(), database, MutableStateFlow(CurrencyInfo.Preview), { null }, dispatcher
        ) { null }
        block(viewModel)
    }

    @Test
    fun savesCorrectedReceiptOfTransaction() {
        val database = FakeReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.edit(7, 300, "REWE", receipt)
            viewModel.setMerchant("Rewe Markt")
            viewModel.removeItem(1)
            viewModel.saveDraft()
            assertNull(viewModel.draft.value)
            val saved = database.saved.getValue(7)
            assertEquals("Rewe Markt", saved.merchant)
            assertEquals(listOf("Milch"), saved.items.map { it.name })
        }
    }

    @Test
    fun closingDraftSavesNothing() {
        val database = FakeReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.edit(7, 300, "REWE", receipt)
            viewModel.setTotal(400)
            assertTrue(viewModel.draft.value!!.isChanged)
            viewModel.closeDraft()
            assertNull(viewModel.draft.value)
            assertTrue(database.saved.isEmpty())
        }
    }

    @Test
    fun assignsWaitingReceiptToChosenTransaction() {
        val database = FakeReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.assignments.openWaiting(ReceiptAssignment(receipt, null, 3, emptyList()))
            viewModel.assignments.assign(BookedTransaction(9, LocalDate.of(2026, 10, 26), -300))
            assertNull(viewModel.assignments.assignment.value)
            assertEquals(listOf(3L to 9L), database.assigned)
        }
    }

    @Test
    fun deletesWaitingReceipt() {
        val database = FakeReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.assignments.openWaiting(ReceiptAssignment(receipt, null, 3, emptyList()))
            viewModel.assignments.deleteWaiting()
            assertNull(viewModel.assignments.assignment.value)
            assertEquals(listOf(3L), database.deletedWaiting)
        }
    }

    @Test
    fun newScanWaitsForItsTransaction() = withViewModel { viewModel ->
        viewModel.assignments.showScan(receipt, 2, emptyList())
        viewModel.assignments.keepWaiting()
        val draft = viewModel.draft.value!!
        assertEquals(ReceiptTarget.Waiting(null), draft.target)
        assertEquals(300L, draft.transactionAmount)
        assertTrue(draft.isStandalone)
        assertEquals(CurrencyInfo.Preview, viewModel.assignments.standaloneCurrency)
    }

    @Test
    fun newScanIsCheckedForChosenTransactionInItsCurrency() = withViewModel { viewModel ->
        val chf = CurrencyInfo("CHF", "CHF", 2)
        viewModel.assignments.showScan(receipt, 2, emptyList())
        viewModel.assignments.assign(BookedTransaction(9, LocalDate.of(2026, 10, 26), -300, currency = chf))
        assertNull(viewModel.assignments.assignment.value)
        assertEquals(ReceiptTarget.Transaction(9), viewModel.draft.value!!.target)
        assertEquals(chf, viewModel.assignments.standaloneCurrency)
    }

    @Test
    fun prefillsNewTransactionWithAmountOfReceipt() = withViewModel { viewModel ->
        viewModel.assignments.showScan(receipt.copy(cardAmount = 250), 2, emptyList())
        val prefill = viewModel.assignments.newTransactionPrefill()!!
        assertEquals(BigDecimal("-2.50"), prefill.amount)
        assertNull(prefill.payee)
    }
}
