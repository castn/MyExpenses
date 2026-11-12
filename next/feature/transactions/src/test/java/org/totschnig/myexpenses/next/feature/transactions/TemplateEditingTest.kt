package org.totschnig.myexpenses.next.feature.transactions

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.data.transactions.CategorySuggestions
import org.totschnig.myexpenses.next.data.transactions.NewTransaction
import org.totschnig.myexpenses.next.data.transactions.TransactionDraft

/** The templates in [TransactionEditViewModel]: chips in the form of a new transaction and the form of a template */
@OptIn(ExperimentalCoroutinesApi::class)
class TemplateEditingTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val now = LocalDate.of(2026, 3, 10).atTime(9, 15).toInstant(ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        templates: FakeTemplateStore,
        store: TransactionEditViewModelTest.FakeStore = TransactionEditViewModelTest.FakeStore(),
        savedState: SavedStateHandle = SavedStateHandle()
    ) = TransactionEditViewModel(
        store,
        CategorySuggestions.None,
        templates,
        savedState,
        ',',
        { ZoneOffset.UTC },
        { now },
        dispatcher
    )

    // Templates in the form of a new transaction

    @Test
    fun newTransactionOffersTheTemplatesThatCanFillTheForm() {
        val templates = FakeTemplateStore(
            mapOf(
                1L to templateValues(),
                2L to templateValues("Aufteilung"),
                3L to templateValues("Alt")
            ),
            splits = setOf(2),
            closed = setOf(3)
        )
        val viewModel = viewModel(templates)
        viewModel.startNew(NewTransaction())
        assertEquals(listOf(1L), viewModel.state.value.templates.map { it.id })
    }

    @Test
    fun templateFillsTheFormKeepsTheDayAndCountsAsUsedWhenSaved() {
        val templates = FakeTemplateStore(mapOf(1L to templateValues()))
        val store = TransactionEditViewModelTest.FakeStore()
        val viewModel = viewModel(templates, store)
        viewModel.startNew(NewTransaction(day = LocalDate.of(2026, 3, 2)))
        viewModel.applyTemplate(1)
        val draft = viewModel.state.value.draft!!
        assertEquals(2L, draft.accountId)
        assertEquals("850,00", draft.amountText)
        assertFalse(draft.isIncome)
        assertEquals("Vermieter", draft.payee)
        assertEquals(4L, draft.categoryId)
        assertEquals(listOf(3L), draft.tagIds)
        assertEquals(LocalDate.of(2026, 3, 2), draft.day)
        assertNull(draft.template)
        assertTrue(templates.used.isEmpty())

        viewModel.saveDraft()
        assertEquals(-85000L, store.created.single().amount)
        assertEquals(listOf(1L), templates.used)
    }

    @Test
    fun aTemplateFromTheWidgetFillsTheForm() {
        val viewModel = viewModel(FakeTemplateStore(mapOf(1L to templateValues())))
        viewModel.startNew(NewTransaction(templateId = 1))
        assertEquals("Vermieter", viewModel.state.value.draft!!.payee)
        assertEquals(1L, viewModel.state.value.draft!!.fromTemplateId)
    }

    @Test
    fun saveAndNewDoesNotCountTheTemplateAgain() {
        val templates = FakeTemplateStore(mapOf(1L to templateValues()))
        val viewModel = viewModel(templates)
        viewModel.startNew(NewTransaction())
        viewModel.applyTemplate(1)
        viewModel.saveDraft(andNew = true)
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(amountText = "5"))
        viewModel.saveDraft()
        assertEquals(listOf(1L), templates.used)
    }

    @Test
    fun anAccountThatCannotBeChosenIsNotTaken() {
        val templates = FakeTemplateStore(mapOf(1L to templateValues(accountId = 9)))
        val viewModel = viewModel(templates)
        viewModel.startNew(NewTransaction(accountId = 1))
        viewModel.applyTemplate(1)
        assertEquals(1L, viewModel.state.value.draft!!.accountId)
    }

    // The form of a template

    @Test
    fun newTemplateNeedsATitleAndMayLeaveTheAmountEmpty() {
        val templates = FakeTemplateStore()
        val viewModel = viewModel(templates)
        viewModel.startTemplate(null)
        val draft = viewModel.state.value.draft!!
        assertEquals(TransactionDraft.NEW, draft.template!!.id)
        assertEquals(listOf(7L), draft.tagIds)

        viewModel.saveDraft()
        assertTrue(viewModel.state.value.isTitleMissing)
        assertTrue(templates.stored.value.isEmpty())

        viewModel.changeDraft(
            draft.copy(template = draft.template!!.copy(title = " Bäcker "), payee = "Bäcker")
        )
        assertFalse(viewModel.state.value.isTitleMissing)
        viewModel.saveDraft()
        val (id, values) = templates.stored.value.entries.single()
        assertEquals("Bäcker", values.title)
        assertEquals(0L, values.amount)
        assertEquals(1L, values.accountId)
        assertEquals(id, viewModel.state.value.savedTemplate)
        assertNull(viewModel.state.value.draft)
    }

    @Test
    fun changedTemplateIsSaved() {
        val templates = FakeTemplateStore(mapOf(1L to templateValues()))
        val viewModel = viewModel(templates)
        viewModel.startTemplate(1)
        val draft = viewModel.state.value.draft!!
        assertEquals("Miete", draft.template!!.title)
        assertEquals("850,00", draft.amountText)
        viewModel.changeDraft(
            draft.copy(amountText = "900", template = draft.template!!.copy(saveDirectly = true))
        )
        viewModel.saveDraft()
        assertEquals(-90000L, templates.stored.value[1]!!.amount)
        assertTrue(templates.stored.value[1]!!.saveDirectly)
        assertEquals(1L, viewModel.state.value.savedTemplate)
    }

    @Test
    fun anotherTemplateReplacesTheDraftOfTheFirst() {
        val templates = FakeTemplateStore(
            mapOf(
                1L to templateValues(),
                2L to templateValues("Strom")
            )
        )
        val viewModel = viewModel(templates)
        viewModel.startTemplate(1)
        viewModel.startTemplate(2)
        assertEquals("Strom", viewModel.state.value.draft!!.template!!.title)
    }

    @Test
    fun aTemplateTypedBeforeTheProcessEndedStays() {
        val savedState = SavedStateHandle()
        val templates = FakeTemplateStore(mapOf(1L to templateValues()))
        viewModel(templates, savedState = savedState).apply {
            startTemplate(1)
            changeDraft(state.value.draft!!.copy(comment = "getippt"))
        }
        val restored = viewModel(templates, savedState = savedState)
        restored.startTemplate(1)
        assertEquals("getippt", restored.state.value.draft!!.comment)
    }

    @Test
    fun goneTemplateCannotBeChanged() {
        val viewModel = viewModel(FakeTemplateStore(mapOf(2L to templateValues())))
        viewModel.startTemplate(1)
        assertTrue(viewModel.state.value.isTemplateGone)
        assertNull(viewModel.state.value.draft)
    }

    @Test
    fun templateThatCannotBeSavedStaysInTheForm() {
        val templates = FakeTemplateStore(mapOf(1L to templateValues())).apply { failSave = true }
        val viewModel = viewModel(templates)
        viewModel.startTemplate(1)
        viewModel.saveDraft()
        assertTrue(viewModel.state.value.isSaveFailed)
        assertEquals("Miete", viewModel.state.value.draft!!.template!!.title)
    }

    @Test
    fun templateFromATransactionStartsWithItsValues() {
        val templates = FakeTemplateStore()
        val viewModel = viewModel(templates, TransactionEditViewModelTest.FakeStore(transaction()))
        viewModel.startTemplateFrom(1)
        val draft = viewModel.state.value.draft!!
        assertEquals("REWE", draft.template!!.title)
        assertEquals("REWE", draft.payee)
        assertEquals("42,90", draft.amountText)
        assertTrue(templates.createdFrom.isEmpty())
    }

    @Test
    fun aSplitOrTransferIsSavedAsTemplateAtOnce() {
        val templates = FakeTemplateStore()
        val viewModel = viewModel(templates, TransactionEditViewModelTest.FakeStore(null))
        viewModel.startTemplateFrom(5)
        assertEquals(listOf(5L), templates.createdFrom)
        assertEquals(90L, viewModel.state.value.savedTemplate)
        assertNull(viewModel.state.value.draft)
    }
}
