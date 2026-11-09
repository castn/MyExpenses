package org.totschnig.myexpenses.next.feature.receipts

import android.net.TestUri
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
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
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.model.receipts.ItemGroup
import org.totschnig.myexpenses.next.data.receipts.BookedTransaction
import org.totschnig.myexpenses.next.data.receipts.ItemGroupModelSetupStore
import org.totschnig.myexpenses.next.data.receipts.Receipt
import org.totschnig.myexpenses.next.data.receipts.ReceiptTarget

/**
 * Reading photos, correcting and saving the draft, and the receipts scanned on their own, with
 * [TestUri] as photo. The photo itself is never loaded: [ReceiptPhoto] needs a Bitmap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptViewModelActionsTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val photo = TestUri("content://scanner/receipt.jpg")

    /** Two items no source knows on their own, and the total */
    private val text = recognizedReceipt(
        "ZORBLAX" to "1,19 A",
        "QWERTZ" to "2,49 A",
        "SUMME" to "3,68"
    )

    private val today = LocalDate.now()

    private val loadedPhotos = mutableListOf<Uri>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withViewModel(
        database: RecordingReceiptDatabase = RecordingReceiptDatabase(),
        recognition: TextRecognition? = FakeTextRecognition { text },
        block: suspend (ReceiptViewModel) -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val viewModel = ReceiptViewModel(
                SavedStateHandle(),
                database,
                NoItemGroupModels(),
                ItemGroupModelSetupStore(dataStore),
                MutableStateFlow(CurrencyInfo.Preview),
                { uri ->
                    loadedPhotos += uri
                    null
                },
                dispatcher
            ) { recognition }
            try {
                block(viewModel)
            } finally {
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    @Test
    fun recognizedPhotoOpensForCheckingItsTransaction() = withViewModel { viewModel ->
        assertTrue(viewModel.canRecognize)
        viewModel.recognize(7, 368, "REWE", photo)
        assertNull(viewModel.scan.value)
        val draft = viewModel.draft.value!!
        assertEquals(ReceiptTarget.Transaction(7), draft.target)
        assertEquals(368L, draft.transactionAmount)
        assertEquals(listOf("ZORBLAX", "QWERTZ"), draft.items.map { it.name })
        assertEquals(368L, draft.total)
        assertEquals(photo, draft.photo)
        assertTrue(draft.isNewPhoto)
        assertFalse(draft.isChanged)
        assertEquals(listOf<Uri>(photo), loadedPhotos)
    }

    @Test
    fun recognizedItemsGetGroupsLearnedBefore() {
        val database = RecordingReceiptDatabase().apply { learned = listOf("Zorblax" to "drinks") }
        withViewModel(database) { viewModel ->
            viewModel.recognize(7, 368, null, photo)
            val items = viewModel.draft.value!!.items
            assertEquals(listOf(ItemGroup.DRINKS.id, null), items.map { it.group })
            assertFalse(viewModel.draft.value!!.isChanged)
        }
    }

    @Test
    fun payeeTellsTheKindOfShop() = withViewModel { viewModel ->
        viewModel.recognize(7, 368, "Rossmann", photo)
        assertEquals(
            listOf(ItemGroup.DRUGSTORE.id, ItemGroup.DRUGSTORE.id),
            viewModel.draft.value!!.items.map { it.group }
        )
    }

    @Test
    fun withoutRecognitionScanFails() = withViewModel(recognition = null) { viewModel ->
        assertFalse(viewModel.canRecognize)
        viewModel.recognize(7, 368, null, photo)
        assertEquals(
            ReceiptScanState.Failed(
                ReceiptTarget.Transaction(7),
                ReceiptScanState.Reason.NoRecognition
            ),
            viewModel.scan.value
        )
        assertNull(viewModel.draft.value)
    }

    @Test
    fun recognitionInExternalAppFails() = withViewModel(
        recognition = FakeTextRecognition { null }
    ) { viewModel ->
        assertTrue(viewModel.canRecognize)
        viewModel.recognizeUnassigned(photo)
        assertEquals(
            ReceiptScanState.Failed(
                ReceiptTarget.Unassigned,
                ReceiptScanState.Reason.NoRecognition
            ),
            viewModel.scan.value
        )
        assertNull(viewModel.assignments.assignment.value)
    }

    @Test
    fun photoWithoutItemsFailsUntilDismissed() = withViewModel(
        recognition = FakeTextRecognition { recognizedReceipt("Vielen Dank" to null) }
    ) { viewModel ->
        viewModel.recognize(7, 368, null, photo)
        assertEquals(
            ReceiptScanState.Failed(ReceiptTarget.Transaction(7), ReceiptScanState.Reason.NoItems),
            viewModel.scan.value
        )
        viewModel.dismissScan()
        assertNull(viewModel.scan.value)
        assertNull(viewModel.draft.value)
    }

    @Test
    fun recognitionErrorFails() = withViewModel(
        recognition = FakeTextRecognition { error("Recognition crashed") }
    ) { viewModel ->
        viewModel.recognize(7, 368, null, photo)
        assertEquals(
            ReceiptScanState.Failed(ReceiptTarget.Transaction(7), ReceiptScanState.Reason.Error),
            viewModel.scan.value
        )
    }

    @Test
    fun receiptScannedOnItsOwnSuggestsTransactions() {
        val fitting = BookedTransaction(9, today, -368, payeeName = "Kiosk")
        val other = BookedTransaction(10, today, -500, payeeName = "Kiosk")
        val database = RecordingReceiptDatabase().apply { expenses = listOf(other, fitting) }
        withViewModel(database) { viewModel ->
            viewModel.recognizeUnassigned(photo)
            assertNull(viewModel.scan.value)
            assertNull(viewModel.draft.value)
            val assignment = viewModel.assignments.assignment.value!!
            assertEquals(photo, assignment.receipt.photo)
            assertEquals(listOf("ZORBLAX", "QWERTZ"), assignment.receipt.items.map { it.name })
            assertNull(assignment.waitingId)
            // Only the amount fits, so the user chooses
            assertEquals(listOf(fitting), assignment.candidates.map { it.transaction })
            assertNull(assignment.preselected)
        }
    }

    @Test
    fun recognizingAgainReplacesItemsOfSavedReceipt() {
        val saved = Receipt("REWE", 368, listOf(item("Alt", 368)), photo = photo)
        withViewModel { viewModel ->
            viewModel.edit(7, 368, "REWE", saved)
            assertFalse(viewModel.draft.value!!.isNewPhoto)
            viewModel.recognizeAgain()
            assertFalse(viewModel.recognizingAgain.value)
            assertNull(viewModel.recognizeAgainFailed.value)
            val draft = viewModel.draft.value!!
            assertEquals(listOf("ZORBLAX", "QWERTZ"), draft.items.map { it.name })
            assertEquals(ReceiptTarget.Transaction(7), draft.target)
            assertFalse(draft.isNewPhoto)
            assertTrue(draft.isChanged)
        }
    }

    @Test
    fun recognizingAgainForgetsThePayee() = withViewModel { viewModel ->
        viewModel.recognize(7, 368, "Rossmann", photo)
        viewModel.recognizeAgain()
        // Looks like a bug: recognizeAgain classifies with payee null, so the kind of shop the
        // payee of the transaction tells is lost, unlike after recognize or a new model
        assertEquals(listOf(null, null), viewModel.draft.value!!.items.map { it.group })
    }

    @Test
    fun recognizingAgainWithoutTextIsReportedUntilDismissed() {
        val recognition = FakeTextRecognition { text }
        withViewModel(recognition = recognition) { viewModel ->
            viewModel.recognize(7, 368, null, photo)
            recognition.result = { null }
            viewModel.recognizeAgain()
            assertEquals(
                ReceiptScanState.Reason.NoRecognition,
                viewModel.recognizeAgainFailed.value
            )
            assertFalse(viewModel.recognizingAgain.value)
            assertEquals(listOf("ZORBLAX", "QWERTZ"), viewModel.draft.value!!.items.map { it.name })
            viewModel.dismissRecognizeAgainFailed()
            assertNull(viewModel.recognizeAgainFailed.value)
        }
    }

    @Test
    fun recognizingAgainWithoutItemsIsReportedUntilDraftCloses() {
        val recognition = FakeTextRecognition { text }
        withViewModel(recognition = recognition) { viewModel ->
            viewModel.recognize(7, 368, null, photo)
            recognition.result = { recognizedReceipt("Vielen Dank" to null) }
            viewModel.recognizeAgain()
            assertEquals(ReceiptScanState.Reason.NoItems, viewModel.recognizeAgainFailed.value)
            viewModel.closeDraft()
            assertNull(viewModel.recognizeAgainFailed.value)
            assertNull(viewModel.draft.value)
        }
    }

    @Test
    fun recognizingAgainWithErrorKeepsDraft() {
        val recognition = FakeTextRecognition { text }
        withViewModel(recognition = recognition) { viewModel ->
            viewModel.recognize(7, 368, null, photo)
            val draft = viewModel.draft.value
            recognition.result = { error("Recognition crashed") }
            viewModel.recognizeAgain()
            assertEquals(ReceiptScanState.Reason.Error, viewModel.recognizeAgainFailed.value)
            assertFalse(viewModel.recognizingAgain.value)
            assertEquals(draft, viewModel.draft.value)
        }
    }

    @Test
    fun recognizingAgainNeedsPhoto() {
        val recognition = FakeTextRecognition { text }
        withViewModel(recognition = recognition) { viewModel ->
            viewModel.recognizeAgain()
            viewModel.edit(7, 300, null, Receipt("REWE", 300, listOf(item("Milch", 300))))
            viewModel.recognizeAgain()
            assertTrue(recognition.recognized.isEmpty())
            assertNull(viewModel.recognizeAgainFailed.value)
            assertEquals(listOf("Milch"), viewModel.draft.value!!.items.map { it.name })
        }
    }

    @Test
    fun savingKeepsNewPhotoWithTransaction() {
        val database = RecordingReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.recognize(7, 368, null, photo)
            viewModel.saveDraft()
            assertNull(viewModel.draft.value)
            assertEquals(listOf<Pair<Uri, Long?>>(photo to 7L), database.keptPhotos)
            val saved = database.saved.getValue(7)
            assertEquals(TestUri("kept:$photo"), saved.photo)
            assertEquals(listOf("ZORBLAX", "QWERTZ"), saved.items.map { it.name })
            assertEquals(368L, saved.total)
        }
    }

    @Test
    fun savingSavedReceiptKeepsItsPhoto() {
        val database = RecordingReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.edit(7, 300, null, Receipt("REWE", 300, listOf(item("Milch", 300)), photo))
            viewModel.saveDraft()
            assertTrue(database.keptPhotos.isEmpty())
            assertEquals(photo, database.saved.getValue(7).photo)
        }
    }

    @Test
    fun newScanWithoutTransactionIsSavedAsWaiting() {
        val database = RecordingReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.recognizeUnassigned(photo)
            viewModel.assignments.keepWaiting()
            assertNull(viewModel.assignments.assignment.value)
            viewModel.saveDraft()
            assertEquals(listOf<Pair<Uri, Long?>>(photo to null), database.keptPhotos)
            val (id, receipt) = database.savedWaiting.single()
            assertNull(id)
            assertEquals(TestUri("kept:$photo"), receipt.photo)
            assertTrue(database.saved.isEmpty())
        }
    }

    @Test
    fun failedSavingIsShownAtItsTransaction() {
        val database = RecordingReceiptDatabase().apply { failSaving = true }
        withViewModel(database) { viewModel ->
            viewModel.recognize(7, 368, null, photo)
            viewModel.saveDraft()
            assertNull(viewModel.draft.value)
            assertEquals(
                ReceiptScanState.Failed(
                    ReceiptTarget.Transaction(7),
                    ReceiptScanState.Reason.Error
                ),
                viewModel.scan.value
            )
        }
    }

    @Test
    fun failedSavingOfScanOnItsOwnIsShownAsUnassigned() {
        val database = RecordingReceiptDatabase().apply { failSaving = true }
        withViewModel(database) { viewModel ->
            viewModel.recognizeUnassigned(photo)
            viewModel.assignments.assign(BookedTransaction(9, today, -368))
            assertTrue(viewModel.draft.value!!.isStandalone)
            viewModel.saveDraft()
            assertEquals(
                ReceiptScanState.Failed(ReceiptTarget.Unassigned, ReceiptScanState.Reason.Error),
                viewModel.scan.value
            )
        }
    }

    @Test
    fun correctionsChangeDraft() = withViewModel { viewModel ->
        val date = LocalDate.of(2026, 11, 2)
        viewModel.edit(
            7,
            400,
            null,
            Receipt("REWE", 400, listOf(item("Milch", 100), item("Brot", 200), item("Milch", 100)))
        )
        assertFalse(viewModel.draft.value!!.isChanged)
        viewModel.setDate(date)
        viewModel.setCardAmount(380)
        viewModel.updateItem(1, item("Brötchen", 200))
        viewModel.insertItem(3, item("Pfand", 25))
        viewModel.setGroup(0, ItemGroup.DAIRY)
        val draft = viewModel.draft.value!!
        assertTrue(draft.isChanged)
        assertEquals(date, draft.date)
        assertEquals(380L, draft.cardAmount)
        assertEquals(listOf("Milch", "Brötchen", "Milch", "Pfand"), draft.items.map { it.name })
        // The same product further down follows the choice, without counting as chosen there
        assertEquals(ItemGroup.DAIRY.id, draft.items[2].group)
        assertEquals(listOf(true, false, false, false), draft.items.map { it.groupByUser })
    }

    @Test
    fun correctionsWithoutDraftDoNothing() {
        val database = RecordingReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.setMerchant("REWE")
            viewModel.setTotal(100)
            viewModel.removeItem(0)
            viewModel.setGroup(0, ItemGroup.DAIRY)
            assertNull(viewModel.draft.value)
            viewModel.saveDraft()
            assertTrue(database.saved.isEmpty())
            assertTrue(database.savedWaiting.isEmpty())
            assertNull(viewModel.scan.value)
        }
    }

    @Test
    fun editingGivesGroupsToItemsWithoutOne() {
        val database = RecordingReceiptDatabase().apply { learned = listOf("Zorblax" to "drinks") }
        withViewModel(database) { viewModel ->
            val receipt = Receipt(
                "REWE",
                300,
                listOf(item("Zorblax", 100), item("Brot", 200, ItemGroup.BAKERY.id)),
                photo
            )
            viewModel.edit(7, 300, null, receipt)
            val draft = viewModel.draft.value!!
            assertEquals(
                listOf(ItemGroup.DRINKS.id, ItemGroup.BAKERY.id),
                draft.items.map {
                    it.group
                }
            )
            assertFalse(draft.isChanged)
            assertEquals(listOf<Uri>(photo), loadedPhotos)
            assertTrue(database.saved.isEmpty())
        }
    }

    @Test
    fun savedReceiptGetsGroupsOnce() {
        val database = RecordingReceiptDatabase().apply {
            learned = listOf("Zorblax" to "drinks")
            saved[7] = Receipt("REWE", 300, listOf(item("Zorblax", 100), item("Qwertz", 200)))
        }
        withViewModel(database) { viewModel ->
            val receipt = viewModel.receipt(7, null).first()!!
            assertEquals(listOf(ItemGroup.DRINKS.id, null), receipt.items.map { it.group })
            assertEquals(receipt, database.saved.getValue(7))
            assertEquals(1, database.saveCount)

            // Qwertz still has no group, but nothing new was found
            assertEquals(receipt, viewModel.receipt(7, null).first())
            assertEquals(1, database.saveCount)
        }
    }

    @Test
    fun receiptOfTransactionWithoutReceiptIsNull() = withViewModel { viewModel ->
        assertNull(viewModel.receipt(7, null).first())
    }

    @Test
    fun deletingReceiptDeletesItsItems() {
        val database = RecordingReceiptDatabase().apply {
            saved[7] = Receipt("REWE", 300, listOf(item("Milch", 300)), photo)
        }
        withViewModel(database) { viewModel ->
            viewModel.deleteReceipt(7)
            assertEquals(listOf(7L), database.deleted)
            assertTrue(database.saved.isEmpty())
        }
    }

    @Test
    fun photosAndScansComeFromDatabaseAndRecognition() {
        val recognition = FakeTextRecognition { text }.apply { scanResult = photo }
        withViewModel(recognition = recognition) { viewModel ->
            assertEquals(TestUri("viewable:$photo"), viewModel.viewableUri(photo))
            assertEquals(photo, viewModel.documentScanResult(null))
        }
    }

    @Test
    fun withoutRecognitionThereIsNoScanResult() = withViewModel(recognition = null) { viewModel ->
        assertNull(viewModel.documentScanResult(null))
    }

    @Test
    fun waitingReceiptsWithSureTransactionComeFirst() {
        val unknown = Receipt("ALDI", 500, listOf(item("Brot", 500)))
        val rewe = Receipt("REWE", 368, listOf(item("Milch", 368)))
        val database = RecordingReceiptDatabase().apply {
            waiting = listOf(1L to unknown, 2L to rewe)
            expenses = listOf(BookedTransaction(9, today, -368, payeeName = "REWE"))
        }
        withViewModel(database) { viewModel ->
            val waiting = viewModel.assignments.waiting.first()
            assertEquals(listOf(2L, 1L), waiting.map { it.waitingId })
            assertEquals(9L, waiting.first().preselected!!.transaction.id)
            assertTrue(waiting.last().candidates.isEmpty())
        }
    }

    @Test
    fun failedAssigningIsShownAtTheTransaction() {
        val database = RecordingReceiptDatabase().apply { failAssigning = true }
        withViewModel(database) { viewModel ->
            viewModel.assignments.openWaiting(
                ReceiptAssignment(Receipt("REWE", 368, emptyList()), null, 3, emptyList())
            )
            viewModel.assignments.assign(BookedTransaction(9, today, -368))
            assertNull(viewModel.assignments.assignment.value)
            assertEquals(
                ReceiptScanState.Failed(
                    ReceiptTarget.Transaction(9),
                    ReceiptScanState.Reason.Error
                ),
                viewModel.scan.value
            )
        }
    }

    @Test
    fun waitingReceiptIsNotOpenedAgainForWaiting() = withViewModel { viewModel ->
        viewModel.assignments.openWaiting(
            ReceiptAssignment(Receipt("REWE", 368, emptyList()), null, 3, emptyList())
        )
        viewModel.assignments.keepWaiting()
        assertNull(viewModel.assignments.assignment.value)
        assertNull(viewModel.draft.value)
    }

    @Test
    fun otherExpensesLeaveOutTransfersAndThoseWithReceipt() {
        val expense = BookedTransaction(9, today, -368)
        val database = RecordingReceiptDatabase().apply {
            expenses = listOf(
                expense,
                BookedTransaction(10, today, -100, isTransfer = true),
                BookedTransaction(11, today, -200, hasReceipt = true)
            )
        }
        val date = LocalDate.of(2026, 10, 1)
        withViewModel(database) { viewModel ->
            viewModel.assignments.loadOtherExpenses()
            assertNull(viewModel.assignments.otherExpenses.value)
            viewModel.assignments.openWaiting(
                ReceiptAssignment(
                    Receipt("REWE", 368, emptyList(), date = date),
                    null,
                    3,
                    emptyList()
                )
            )
            database.expenseRanges.clear()
            viewModel.assignments.loadOtherExpenses()
            assertEquals(listOf(expense), viewModel.assignments.otherExpenses.value)
            assertEquals(LocalDate.of(2026, 10, 9), database.expenseRanges.single().second)
            assertEquals(LocalDate.of(2026, 8, 10), database.expenseRanges.single().first)
            viewModel.assignments.close()
            assertNull(viewModel.assignments.otherExpenses.value)
        }
    }

    @Test
    fun newTransactionTakesPayeeOfLatestExpenseAtTheShop() {
        val database = RecordingReceiptDatabase().apply {
            expenses = listOf(
                BookedTransaction(8, today, -100, payeeName = "Bäckerei"),
                BookedTransaction(9, today, -368, payeeName = "REWE Markt", payeeId = 4)
            )
        }
        val date = LocalDate.of(2026, 10, 1)
        withViewModel(database) { viewModel ->
            assertNull(viewModel.assignments.newTransactionPrefill())
            viewModel.assignments.showScan(
                Receipt("REWE", null, listOf(item("Milch", 100), item("Brot", 268)), date = date),
                2,
                emptyList()
            )
            val prefill = viewModel.assignments.newTransactionPrefill()!!
            assertEquals(BigDecimal("-3.68"), prefill.amount)
            assertEquals(date, prefill.date)
            assertEquals(PrefillPayee(4, "REWE Markt"), prefill.payee)
        }
    }

    @Test
    fun newTransactionIsFoundAmongCandidatesAfterRefresh() {
        val database = RecordingReceiptDatabase()
        withViewModel(database) { viewModel ->
            viewModel.assignments.showScan(Receipt("REWE", 368, emptyList()), null, emptyList())
            assertTrue(viewModel.assignments.assignment.value!!.candidates.isEmpty())
            val booked = BookedTransaction(9, today, -368, payeeName = "REWE")
            database.expenses = listOf(booked)
            viewModel.assignments.refreshCandidates()
            assertEquals(booked, viewModel.assignments.assignment.value!!.preselected!!.transaction)
        }
    }
}
