package org.totschnig.myexpenses.next.feature.receipts

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.itemgroupmodel.ProductGroupModel
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.receipts.BookedTransaction
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelRelease
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelSetupStore
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModels
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

    /** No model installed, none offered or no connection */
    private class FakeModels(private val release: ItemGroupModelRelease? = null) : ItemGroupModels {
        override fun model(): ProductGroupModel? = null
        override fun install(uri: Uri) = throw IOException("Not readable")
        override fun latestRelease() = release
        override fun download(release: ItemGroupModelRelease) = throw IOException("No connection")
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
        models: ItemGroupModels = FakeModels(),
        block: suspend (ReceiptViewModel, ItemGroupModelSetupStore) -> Unit,
    ) = withTestDataStore { dataStore ->
        val setupStore = ItemGroupModelSetupStore(dataStore)
        runTest(dispatcher) {
            val viewModel = ReceiptViewModel(
                SavedStateHandle(), database, models, setupStore, MutableStateFlow(CurrencyInfo.Preview),
                { null }, dispatcher
            ) { null }
            block(viewModel, setupStore)
        }
    }

    @Test
    fun savesCorrectedReceiptOfTransaction() {
        val database = FakeReceiptDatabase()
        withViewModel(database) { viewModel, _ ->
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
        withViewModel(database) { viewModel, _ ->
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
        withViewModel(database) { viewModel, _ ->
            viewModel.assignments.openWaiting(ReceiptAssignment(receipt, null, 3, emptyList()))
            viewModel.assignments.assign(BookedTransaction(9, LocalDate.of(2026, 10, 26), -300))
            assertNull(viewModel.assignments.assignment.value)
            assertEquals(listOf(3L to 9L), database.assigned)
        }
    }

    @Test
    fun deletesWaitingReceipt() {
        val database = FakeReceiptDatabase()
        withViewModel(database) { viewModel, _ ->
            viewModel.assignments.openWaiting(ReceiptAssignment(receipt, null, 3, emptyList()))
            viewModel.assignments.deleteWaiting()
            assertNull(viewModel.assignments.assignment.value)
            assertEquals(listOf(3L), database.deletedWaiting)
        }
    }

    @Test
    fun newScanWaitsForItsTransaction() = withViewModel { viewModel, _ ->
        viewModel.assignments.showScan(receipt, 2, emptyList())
        viewModel.assignments.keepWaiting()
        val draft = viewModel.draft.value!!
        assertEquals(ReceiptTarget.Waiting(null), draft.target)
        assertEquals(300L, draft.transactionAmount)
        assertTrue(draft.isStandalone)
        assertEquals(CurrencyInfo.Preview, viewModel.assignments.standaloneCurrency)
    }

    @Test
    fun newScanIsCheckedForChosenTransactionInItsCurrency() = withViewModel { viewModel, _ ->
        val chf = CurrencyInfo("CHF", "CHF", 2)
        viewModel.assignments.showScan(receipt, 2, emptyList())
        viewModel.assignments.assign(BookedTransaction(9, LocalDate.of(2026, 10, 26), -300, currency = chf))
        assertNull(viewModel.assignments.assignment.value)
        assertEquals(ReceiptTarget.Transaction(9), viewModel.draft.value!!.target)
        assertEquals(chf, viewModel.assignments.standaloneCurrency)
    }

    @Test
    fun prefillsNewTransactionWithAmountOfReceipt() = withViewModel { viewModel, _ ->
        viewModel.assignments.showScan(receipt.copy(cardAmount = 250), 2, emptyList())
        val prefill = viewModel.assignments.newTransactionPrefill()!!
        assertEquals(BigDecimal("-2.50"), prefill.amount)
        assertNull(prefill.payee)
    }

    @Test
    fun offersModelUntilDeclined() = withViewModel { viewModel, setupStore ->
        assertTrue(viewModel.modelInstaller.offer.first())
        viewModel.modelInstaller.decline()
        // Written by the data store in its own scope
        setupStore.declined.first { it }
        assertFalse(viewModel.modelInstaller.offer.first())
    }

    @Test
    fun downloadWithoutReleaseFails() = withViewModel { viewModel, _ ->
        viewModel.modelInstaller.download()
        assertEquals(ModelSetup.Failed(ModelSetup.Reason.NoRelease), viewModel.modelInstaller.setup.value)
    }

    @Test
    fun downloadWithoutConnectionFails() = withViewModel(
        models = FakeModels(ItemGroupModelRelease("de", "1", 1, "https://example.com/m.bin", "", 1))
    ) { viewModel, _ ->
        viewModel.modelInstaller.download()
        assertEquals(ModelSetup.Failed(ModelSetup.Reason.Network), viewModel.modelInstaller.setup.value)
    }
}
