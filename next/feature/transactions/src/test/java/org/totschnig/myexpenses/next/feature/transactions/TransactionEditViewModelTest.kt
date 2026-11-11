package org.totschnig.myexpenses.next.feature.transactions

import androidx.lifecycle.SavedStateHandle
import java.math.BigDecimal
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.data.transactions.AccountOption
import org.totschnig.myexpenses.next.data.transactions.CategorySuggestions
import org.totschnig.myexpenses.next.data.transactions.ChangedField
import org.totschnig.myexpenses.next.data.transactions.EditValues
import org.totschnig.myexpenses.next.data.transactions.EditableTransaction
import org.totschnig.myexpenses.next.data.transactions.MethodOption
import org.totschnig.myexpenses.next.data.transactions.NewTransaction
import org.totschnig.myexpenses.next.data.transactions.SuggestedCategory
import org.totschnig.myexpenses.next.data.transactions.TransactionStore

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val now = LocalDate.of(2026, 3, 10).atTime(9, 15).toInstant(ZoneOffset.UTC)

    private class FakeStore(var stored: EditableTransaction? = null) : TransactionStore {
        val saved = mutableListOf<Pair<EditValues, EditValues>>()
        val created = mutableListOf<EditValues>()
        val methodsAsked = mutableListOf<Pair<Long, Boolean>>()
        var accounts = listOf(
            AccountOption(1, "Giro", "EUR", "€", 2, type = 10),
            AccountOption(2, "Bargeld", "EUR", "€", 2, type = 20),
            AccountOption(3, "Dollar", "USD", "$", 2, type = 10)
        )
        var defaultTags = mapOf(1L to listOf(7L), 2L to listOf(8L))
        var failCreate = false

        override suspend fun load(id: Long) = stored?.takeIf { it.id == id }

        override suspend fun methods(accountType: Long, isIncome: Boolean): List<MethodOption> {
            methodsAsked += accountType to isIncome
            return listOf(
                MethodOption(if (isIncome) 2 else 1, if (isIncome) "Gutschrift" else "Lastschrift")
            )
        }

        override suspend fun accounts() = accounts

        override suspend fun defaultTags(accountId: Long) = defaultTags[accountId].orEmpty()

        override suspend fun save(id: Long, from: EditValues, to: EditValues) {
            saved += from to to
            stored = stored?.copy(values = to)
        }

        override suspend fun create(values: EditValues): Long {
            check(!failCreate) { "closed" }
            created += values
            return 100L + created.size
        }
    }

    /** Suggests a category for payees in [byPayee] */
    private class FakeSuggestions(val byPayee: Map<String, SuggestedCategory>) :
        CategorySuggestions {
        override suspend fun suggest(payee: String?, comment: String?, isIncome: Boolean) =
            byPayee[payee]
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        store: TransactionStore,
        savedState: SavedStateHandle = SavedStateHandle(),
        suggestions: CategorySuggestions = CategorySuggestions.None
    ) = TransactionEditViewModel(store, suggestions, savedState, ',', {
        ZoneOffset.UTC
    }, { now }, dispatcher)

    // Changing a transaction from its details

    @Test
    fun loadsTransactionAndMethodsForItsSign() {
        val store = FakeStore(transaction())
        val viewModel = viewModel(store)
        viewModel.open(1)
        assertEquals(transaction(), viewModel.state.value.transaction)
        assertEquals(listOf(10L to false), store.methodsAsked)
        assertEquals("Lastschrift", viewModel.state.value.methods.single().label)
    }

    @Test
    fun nothingToEditForWhatTheStoreLeavesToTheClassicForm() {
        val viewModel = viewModel(FakeStore(null))
        viewModel.open(1)
        assertNull(viewModel.state.value.transaction)
        viewModel.startEditing()
        assertNull(viewModel.state.value.draft)
    }

    @Test
    fun quickChangeSavesAtOnceAndCanBeUndone() {
        val store = FakeStore(transaction())
        val viewModel = viewModel(store)
        viewModel.open(1)
        viewModel.changeComment(" Wocheneinkauf ")
        assertEquals("Wocheneinkauf", store.saved.last().second.comment)
        assertEquals(null, store.saved.last().first.comment)
        assertEquals("Wocheneinkauf", viewModel.state.value.transaction!!.values.comment)
        assertEquals(ChangedField.COMMENT, viewModel.state.value.undo!!.field)

        viewModel.undo()
        assertEquals("Wocheneinkauf", store.saved.last().first.comment)
        assertNull(store.saved.last().second.comment)
        assertNull(viewModel.state.value.transaction!!.values.comment)
        assertNull(viewModel.state.value.undo)
    }

    @Test
    fun quickChangeWithoutDifferenceSavesNothing() {
        val store = FakeStore(transaction())
        val viewModel = viewModel(store)
        viewModel.open(1)
        viewModel.changeDay(LocalDate.of(2026, 3, 1))
        assertTrue(store.saved.isEmpty())
        assertNull(viewModel.state.value.undo)
    }

    @Test
    fun formSavesAllFieldsOnlyOnSave() {
        val store = FakeStore(transaction())
        val viewModel = viewModel(store)
        viewModel.open(1)
        viewModel.startEditing()
        val draft = viewModel.state.value.draft!!
        assertEquals("42,90", draft.amountText)

        viewModel.changeDraft(
            draft.copy(
                amountText = "50",
                payee = "Lidl",
                methodId = 1,
                categoryId = 5,
                tagIds = listOf(3)
            )
        )
        assertTrue(store.saved.isEmpty())

        viewModel.saveDraft()
        assertEquals(
            transaction().values.copy(
                amount = -5000,
                payee = "Lidl",
                methodId = 1,
                categoryId = 5,
                tagIds = listOf(3)
            ),
            store.saved.single().second
        )
        assertNull(viewModel.state.value.draft)
        assertEquals(ChangedField.ALL, viewModel.state.value.undo!!.field)
    }

    @Test
    fun aTransactionMovesOnlyToAccountsOfItsCurrency() {
        val viewModel = viewModel(FakeStore(transaction()))
        viewModel.open(1)
        viewModel.startEditing()
        assertEquals(listOf(1L, 2L), viewModel.state.value.accounts.map { it.id })
    }

    @Test
    fun invalidAmountIsNotSaved() {
        val store = FakeStore(transaction())
        val viewModel = viewModel(store)
        viewModel.open(1)
        viewModel.startEditing()
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(amountText = "4x"))
        viewModel.saveDraft()
        assertTrue(store.saved.isEmpty())
        assertTrue(viewModel.state.value.isAmountInvalid)
        assertNotNull(viewModel.state.value.draft)
    }

    @Test
    fun changingTheSignLoadsTheMethodsForIt() {
        val store = FakeStore(transaction())
        val viewModel = viewModel(store)
        viewModel.open(1)
        viewModel.startEditing()
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(isIncome = true))
        assertEquals(10L to true, store.methodsAsked.last())
        assertEquals("Gutschrift", viewModel.state.value.methods.single().label)
    }

    @Test
    fun draftSurvivesInSavedStateForItsTransactionOnly() {
        val savedState = SavedStateHandle()
        val store = FakeStore(transaction())
        viewModel(store, savedState).apply {
            open(1)
            startEditing()
            changeDraft(state.value.draft!!.copy(comment = "Notiz"))
        }
        val restored = viewModel(store, savedState)
        restored.open(1)
        assertEquals("Notiz", restored.state.value.draft!!.comment)

        store.stored = transaction(id = 2)
        restored.open(2)
        assertNull(restored.state.value.draft)
    }

    // A new transaction

    @Test
    fun newTransactionStartsInTheAccountAskedForWithItsTags() {
        val store = FakeStore()
        val viewModel = viewModel(store)
        viewModel.startNew(NewTransaction(accountId = 2, isIncome = true))
        val draft = viewModel.state.value.draft!!
        assertTrue(draft.isNew)
        assertEquals(2L, draft.accountId)
        assertEquals(listOf(8L), draft.tagIds)
        assertEquals(LocalDate.of(2026, 3, 10), draft.day)
        assertEquals(20L to true, store.methodsAsked.last())
        assertEquals("€", viewModel.state.value.draftAccount!!.currencySymbol)
    }

    @Test
    fun newTransactionWithoutAccountTakesTheOneUsedLast() {
        val viewModel = viewModel(FakeStore())
        viewModel.startNew(NewTransaction(accountId = 99))
        assertEquals(1L, viewModel.state.value.draft!!.accountId)
    }

    @Test
    fun withoutOpenAccountThereIsNoForm() {
        val viewModel = viewModel(FakeStore().apply { accounts = emptyList() })
        viewModel.startNew(NewTransaction())
        assertNull(viewModel.state.value.draft)
        assertTrue(viewModel.state.value.hasNoAccount)
    }

    @Test
    fun savesNewTransactionAndCloses() {
        val store = FakeStore()
        val viewModel = viewModel(store)
        viewModel.startNew(NewTransaction(amount = BigDecimal("12.50"), payee = "Bäcker"))
        viewModel.saveDraft()
        val values = store.created.single()
        assertEquals(-1250L, values.amount)
        assertEquals("Bäcker", values.payee)
        assertEquals(listOf(7L), values.tagIds)
        assertEquals(
            LocalDate.of(2026, 3, 10).atTime(9, 15).toEpochSecond(ZoneOffset.UTC),
            values.date
        )
        assertNull(viewModel.state.value.draft)
        assertEquals(101L, viewModel.state.value.created)
        viewModel.createdShown(101)
        assertNull(viewModel.state.value.created)
    }

    @Test
    fun saveAndNewKeepsAccountAndDay() {
        val store = FakeStore()
        val viewModel = viewModel(store)
        viewModel.startNew(NewTransaction(accountId = 2, day = LocalDate.of(2026, 3, 8)))
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(amountText = "3", payee = "Kiosk"))
        viewModel.saveDraft(andNew = true)
        assertEquals(1, store.created.size)
        val next = viewModel.state.value.draft!!
        assertTrue(next.isNew)
        assertEquals(2L, next.accountId)
        assertEquals(LocalDate.of(2026, 3, 8), next.day)
        assertEquals("", next.amountText)
        assertEquals("", next.payee)
        assertEquals(101L, viewModel.state.value.created)
    }

    @Test
    fun aNewTransactionThatCannotBeSavedStaysInTheForm() {
        val store = FakeStore().apply { failCreate = true }
        val viewModel = viewModel(store)
        viewModel.startNew(NewTransaction(amount = BigDecimal.ONE))
        viewModel.saveDraft()
        assertTrue(viewModel.state.value.isSaveFailed)
        assertNotNull(viewModel.state.value.draft)
        assertNull(viewModel.state.value.created)
    }

    @Test
    fun newTransactionFollowsTheTagsOfItsAccountUntilTheyAreChanged() {
        val viewModel = viewModel(FakeStore())
        viewModel.startNew(NewTransaction(accountId = 1))
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(accountId = 2))
        assertEquals(listOf(8L), viewModel.state.value.draft!!.tagIds)

        viewModel.changeDraft(viewModel.state.value.draft!!.copy(tagIds = listOf(8L, 5L)))
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(accountId = 1))
        assertEquals(listOf(8L, 5L), viewModel.state.value.draft!!.tagIds)
    }

    @Test
    fun newDraftSurvivesInSavedState() {
        val savedState = SavedStateHandle()
        val store = FakeStore()
        viewModel(store, savedState).apply {
            startNew(NewTransaction())
            changeDraft(state.value.draft!!.copy(amountText = "7"))
        }
        val restored = viewModel(store, savedState)
        restored.startNew(NewTransaction(amount = BigDecimal.TEN))
        assertEquals("7", restored.state.value.draft!!.amountText)
        assertEquals(3, restored.state.value.accounts.size)
    }

    // Categories suggested while typing

    @Test
    fun offersSuggestionForThePayeeAndTakesItOnAccept() {
        val suggestions =
            FakeSuggestions(
                mapOf("Bäcker" to SuggestedCategory(5, "Lebensmittel", isCertain = false))
            )
        val viewModel = viewModel(FakeStore(), suggestions = suggestions)
        viewModel.startNew(NewTransaction())
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(payee = "Bäcker"))
        assertNull(viewModel.state.value.draft!!.categoryId)
        assertEquals(5L, viewModel.state.value.suggestion!!.id)

        viewModel.acceptSuggestion()
        assertEquals(5L, viewModel.state.value.draft!!.categoryId)
        assertEquals("Lebensmittel", viewModel.state.value.draft!!.categoryPath)
        assertFalse(viewModel.state.value.draft!!.isCategorySuggested)
        assertNull(viewModel.state.value.suggestion)
    }

    @Test
    fun certainSuggestionIsTakenAndFollowsThePayee() {
        val suggestions = FakeSuggestions(
            mapOf(
                "Stadtwerke" to SuggestedCategory(6, "Wohnen > Strom", isCertain = true),
                "Kiosk" to SuggestedCategory(5, "Lebensmittel", isCertain = false)
            )
        )
        val viewModel = viewModel(FakeStore(), suggestions = suggestions)
        viewModel.startNew(NewTransaction())
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(payee = "Stadtwerke"))
        assertEquals(6L, viewModel.state.value.draft!!.categoryId)
        assertTrue(viewModel.state.value.draft!!.isCategorySuggested)

        // Another payee: the category of the rule no longer fits, the new one is only offered
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(payee = "Kiosk"))
        assertNull(viewModel.state.value.draft!!.categoryId)
        assertEquals(5L, viewModel.state.value.suggestion!!.id)
    }

    @Test
    fun categoryChosenByTheUserIsNotReplaced() {
        val suggestions =
            FakeSuggestions(mapOf("Stadtwerke" to SuggestedCategory(6, "Strom", isCertain = true)))
        val viewModel = viewModel(FakeStore(), suggestions = suggestions)
        viewModel.startNew(NewTransaction())
        viewModel.changeDraft(
            viewModel.state.value.draft!!.copy(categoryId = 9, categoryPath = "Geschenke")
        )
        viewModel.changeDraft(viewModel.state.value.draft!!.copy(payee = "Stadtwerke"))
        assertEquals(9L, viewModel.state.value.draft!!.categoryId)
        assertNull(viewModel.state.value.suggestion)
    }
}

private fun transaction(id: Long = 1) = EditableTransaction(
    id = id,
    values = EditValues(
        accountId = 1,
        amount = -4290,
        date = LocalDate.of(2026, 3, 1).atTime(12, 0).toEpochSecond(ZoneOffset.UTC),
        valueDate = LocalDate.of(2026, 3, 1).atTime(12, 0).toEpochSecond(ZoneOffset.UTC),
        payee = "REWE",
        categoryId = null,
        tagIds = emptyList(),
        methodId = null,
        comment = null
    ),
    categoryPath = null,
    fractionDigits = 2,
    accountType = 10,
    isAmountEditable = true,
    isImported = false
)
