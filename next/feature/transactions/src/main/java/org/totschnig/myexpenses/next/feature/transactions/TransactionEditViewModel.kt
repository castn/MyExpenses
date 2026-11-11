package org.totschnig.myexpenses.next.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.next.data.transactions.AccountOption
import org.totschnig.myexpenses.next.data.transactions.CategorySuggestions
import org.totschnig.myexpenses.next.data.transactions.ChangedField
import org.totschnig.myexpenses.next.data.transactions.EditValues
import org.totschnig.myexpenses.next.data.transactions.EditableTransaction
import org.totschnig.myexpenses.next.data.transactions.MethodOption
import org.totschnig.myexpenses.next.data.transactions.NewTransaction
import org.totschnig.myexpenses.next.data.transactions.SplitPart
import org.totschnig.myexpenses.next.data.transactions.SuggestedCategory
import org.totschnig.myexpenses.next.data.transactions.TransactionDraft
import org.totschnig.myexpenses.next.data.transactions.TransactionStore
import org.totschnig.myexpenses.next.data.transactions.Undo
import org.totschnig.myexpenses.next.data.transactions.next
import org.totschnig.myexpenses.next.data.transactions.toDraft
import org.totschnig.myexpenses.next.data.transactions.toValues
import org.totschnig.myexpenses.next.data.transactions.withDay

/**
 * What the form and the details of a transaction show for entering or changing it
 *
 * @param transaction the opened transaction, null while it loads or if the details cannot change
 * it (see [TransactionStore.load])
 * @param loadedId the transaction whose loading has finished, so that a [transaction] of null means
 * that the details cannot change it
 * @param methods payment methods to choose from, for the account and the sign of the draft or of
 * the transaction
 * @param draft what is typed in the form, null outside of it
 * @param accounts to choose from in the form; for a transaction that is changed only those of its
 * currency
 * @param hasNoAccount there is no open account a new transaction could be entered in
 * @param suggestion a category for the draft, offered while it has none
 * @param isAmountInvalid the amount of the draft cannot be read, it is not saved then
 * @param isSaveFailed the database rejected the new transaction, e.g. as its account was closed
 * meanwhile
 * @param undo the last change, offered to undo until it was shown
 * @param saved a transaction saved from the form, new or changed, until
 * [TransactionFormActions.savedShown]
 * @param splitParts the parts of an opened split, which is only shown
 */
data class TransactionEditState(
    val transaction: EditableTransaction? = null,
    val loadedId: Long? = null,
    val methods: List<MethodOption> = emptyList(),
    val draft: TransactionDraft? = null,
    val accounts: List<AccountOption> = emptyList(),
    val hasNoAccount: Boolean = false,
    val suggestion: SuggestedCategory? = null,
    val isAmountInvalid: Boolean = false,
    val isSaveFailed: Boolean = false,
    val undo: Undo? = null,
    val saved: Long? = null,
    val splitParts: List<SplitPart> = emptyList()
) {
    /** The account of the draft, e.g. for the symbol of its currency */
    val draftAccount: AccountOption?
        get() = accounts.find { it.id == draft?.accountId }
}

/** What the user can do in the form of a transaction, see [TransactionForm] */
interface TransactionFormActions {
    /** The form with all fields of the opened transaction, saved only on [saveDraft] */
    fun startEditing()

    /**
     * The form for a new transaction, unless one is typed already, e.g. after Android ended the
     * process
     */
    fun startNew(prefill: NewTransaction)

    /**
     * The form for [transactionId] on a screen of its own, once it is loaded, unless its draft
     * survived
     */
    fun edit(transactionId: Long)

    fun changeDraft(draft: TransactionDraft)

    /** Takes the [TransactionEditState.suggestion] as the category of the draft */
    fun acceptSuggestion()

    /** @param andNew a new transaction follows, see [next] */
    fun saveDraft(andNew: Boolean = false)

    fun cancelEditing()

    fun savedShown(id: Long)
}

/**
 * What the user can do to enter a transaction or to change it in its details: the form, and quick
 * changes of single values in the details
 */
interface TransactionEditActions : TransactionFormActions {
    /** Loads [transactionId] when its details open */
    fun open(transactionId: Long)

    fun close()

    /** Quick changes in the details, saved at once and offered to undo */
    fun changeComment(comment: String)

    fun changeDay(day: LocalDate)

    fun changeMethod(methodId: Long?)

    fun undo()

    fun undoShown()
}

/**
 * Enters a new expense or income and changes a transaction from its details, see
 * [TransactionEditActions]. The draft of the form is kept in [savedState], so that it survives
 * when Android ends the process. A category is suggested from the payee and the note while the
 * draft has none, see [CategorySuggestions]; a certain suggestion is taken right away.
 */
class TransactionEditViewModel(
    private val store: TransactionStore,
    private val suggestions: CategorySuggestions,
    private val savedState: SavedStateHandle,
    private val decimalSeparator: Char,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(),
    TransactionEditActions {

    private val _state = MutableStateFlow(TransactionEditState(draft = savedState[KEY_DRAFT]))
    val state: StateFlow<TransactionEditState> = _state.asStateFlow()

    private var openedId: Long? = null
    private var loadJob: Job? = null
    private var suggestJob: Job? = null
    private var isSaving = false

    // Starts the form once the transaction of [edit] is loaded
    private var editOnLoad = false

    init {
        // A new transaction typed before Android ended the process
        _state.value.draft?.takeIf { it.isNew }?.let { draft ->
            viewModelScope.launch {
                loadAccounts(null)
                loadMethods(draft)
                suggest(draft)
            }
        }
    }

    override fun open(transactionId: Long) {
        if (openedId == transactionId) return
        openedId = transactionId
        // A draft only belongs to the transaction it was started for
        if (_state.value.draft?.id != transactionId) setDraft(null)
        _state.update {
            it.copy(
                transaction = null,
                loadedId = null,
                methods = emptyList(),
                undo = null,
                suggestion = null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { reload(transactionId) }
    }

    override fun close() {
        openedId = null
        loadJob?.cancel()
        setDraft(null)
        _state.value = TransactionEditState()
    }

    override fun changeComment(comment: String) =
        quickChange(ChangedField.COMMENT) { it.copy(comment = comment.trim().ifEmpty { null }) }

    override fun changeDay(day: LocalDate) = quickChange(ChangedField.DATE) {
        it.withDay(day, zone())
    }

    override fun changeMethod(methodId: Long?) = quickChange(ChangedField.METHOD) {
        it.copy(methodId = methodId)
    }

    override fun undo() {
        val undo = _state.value.undo ?: return
        _state.update { it.copy(undo = null) }
        viewModelScope.launch {
            withContext(io) { store.save(undo.id, undo.saved, undo.previous) }
            if (openedId == undo.id) reload(undo.id)
        }
    }

    override fun undoShown() = _state.update { it.copy(undo = null) }

    override fun startEditing() {
        val transaction = _state.value.transaction ?: return
        val draft = transaction.toDraft(decimalSeparator, zone())
        setDraft(draft)
        viewModelScope.launch {
            loadAccounts(transaction)
            suggest(draft)
        }
    }

    override fun edit(transactionId: Long) {
        // Asked again, e.g. after a rotation
        if (openedId == transactionId) return
        editOnLoad = _state.value.draft?.id != transactionId
        open(transactionId)
    }

    override fun startNew(prefill: NewTransaction) {
        if (_state.value.draft?.isNew == true) return
        viewModelScope.launch {
            // The account asked for, else the one used last, of the currency asked for if any
            val account = loadAccounts(null).let { accounts ->
                accounts.find { it.id == prefill.accountId }
                    ?: accounts.firstOrNull { it.currency == prefill.currency }
                    ?: accounts.firstOrNull()
            }
            if (account == null) {
                _state.update { it.copy(hasNoAccount = true) }
                return@launch
            }
            val tagIds = withContext(io) { store.defaultTags(account.id) }
            val draft = prefill.toDraft(
                account.id,
                tagIds,
                now().atZone(zone()).toLocalDate(),
                decimalSeparator
            )
            setDraft(draft)
            loadMethods(draft)
            suggest(draft)
        }
    }

    override fun changeDraft(draft: TransactionDraft) {
        val previous = _state.value.draft
        setDraft(draft)
        _state.update { it.copy(isAmountInvalid = false, isSaveFailed = false) }
        if (previous == null) return
        if (draft.isIncome != previous.isIncome || draft.accountId != previous.accountId) {
            viewModelScope.launch { loadMethods(draft) }
        }
        if (draft.isNew && draft.accountId != previous.accountId) {
            viewModelScope.launch { followAccountTags(previous.accountId, draft.accountId) }
        }
        if (draft.payee != previous.payee || draft.comment != previous.comment ||
            draft.isIncome != previous.isIncome
        ) {
            suggest(draft)
        } else if (draft.categoryId != previous.categoryId) {
            _state.update { it.copy(suggestion = null) }
        }
    }

    override fun acceptSuggestion() {
        val suggestion = _state.value.suggestion ?: return
        val draft = _state.value.draft ?: return
        setDraft(
            draft.copy(
                categoryId = suggestion.id,
                categoryPath = suggestion.path,
                isCategorySuggested = false
            )
        )
        _state.update { it.copy(suggestion = null) }
    }

    override fun saveDraft(andNew: Boolean) {
        val draft = _state.value.draft ?: return
        if (isSaving) return
        if (draft.isNew) {
            create(draft, andNew)
            return
        }
        val transaction = _state.value.transaction?.takeIf { it.id == draft.id } ?: return
        val values = draft.toValues(
            transaction.values,
            transaction.fractionDigits,
            transaction.isAmountEditable,
            zone(),
            now()
        )
        if (values == null) {
            _state.update { it.copy(isAmountInvalid = true) }
            return
        }
        setDraft(null)
        save(transaction, values, ChangedField.ALL)
    }

    override fun cancelEditing() {
        setDraft(null)
        _state.update { it.copy(suggestion = null, isAmountInvalid = false, isSaveFailed = false) }
        _state.value.transaction?.let { transaction ->
            viewModelScope.launch {
                loadMethods(
                    transaction.accountType,
                    transaction.values.amount > 0
                )
            }
        }
    }

    override fun savedShown(id: Long) = _state.update {
        if (it.saved ==
            id
        ) {
            it.copy(saved = null)
        } else {
            it
        }
    }

    private fun create(draft: TransactionDraft, andNew: Boolean) {
        val account = _state.value.accounts.find { it.id == draft.accountId } ?: return
        val values = draft.toValues(null, account.fractionDigits, true, zone(), now())
        if (values == null) {
            _state.update { it.copy(isAmountInvalid = true) }
            return
        }
        isSaving = true
        viewModelScope.launch {
            val created = withContext(io) { runCatching { store.create(values) } }
            isSaving = false
            val id = created.getOrElse {
                _state.update { it.copy(isSaveFailed = true) }
                return@launch
            }
            if (andNew) {
                val next = draft.next(withContext(io) { store.defaultTags(account.id) })
                setDraft(next)
                _state.update { it.copy(suggestion = null, saved = id) }
            } else {
                setDraft(null)
                _state.update { it.copy(saved = id) }
            }
        }
    }

    private fun quickChange(field: ChangedField, change: (EditValues) -> EditValues) {
        val transaction = _state.value.transaction ?: return
        val values = change(transaction.values)
        if (values != transaction.values) save(transaction, values, field)
    }

    private fun save(transaction: EditableTransaction, values: EditValues, field: ChangedField) {
        // Shown at once, the list catches up when the database tells it
        _state.update {
            it.copy(
                transaction = transaction.copy(values = values),
                undo = Undo(transaction.id, transaction.values, values, field),
                suggestion = null
            )
        }
        viewModelScope.launch {
            val saved =
                withContext(io) {
                    runCatching { store.save(transaction.id, transaction.values, values) }
                }
            if (saved.isFailure) {
                // Show what the database has, nothing to undo
                _state.update { it.copy(undo = null) }
                if (openedId == transaction.id) reload(transaction.id)
            } else {
                if (field == ChangedField.ALL) _state.update { it.copy(saved = transaction.id) }
                afterSave(transaction, values)
            }
        }
    }

    private suspend fun afterSave(transaction: EditableTransaction, values: EditValues) {
        if (openedId == transaction.id && values.categoryId != transaction.values.categoryId) {
            // The path of the category comes from the database
            reload(transaction.id)
        } else if (transaction.values.amount > 0 != values.amount > 0) {
            loadMethods(transaction.accountType, values.amount > 0)
        }
    }

    private suspend fun reload(id: Long) {
        val transaction = withContext(io) { store.load(id) }
        if (openedId != id) return
        // A split is only shown with its parts
        val parts = if (transaction ==
            null
        ) {
            withContext(io) { store.splitParts(id) }
        } else {
            emptyList()
        }
        _state.update { it.copy(transaction = transaction, loadedId = id, splitParts = parts) }
        if (transaction != null && editOnLoad && _state.value.draft == null) startEditing()
        editOnLoad = false
        val draft = _state.value.draft
        if (transaction == null) {
            setDraft(null)
        } else if (draft != null) {
            loadAccounts(transaction)
            loadMethods(draft)
        } else {
            loadMethods(transaction.accountType, transaction.values.amount > 0)
        }
    }

    /**
     * The accounts to choose from: all open ones for a new transaction, those of its currency for
     * [transaction], as its amount stays
     */
    private suspend fun loadAccounts(transaction: EditableTransaction?): List<AccountOption> {
        val all = withContext(io) { store.accounts() }
        val accounts = if (transaction == null) {
            all
        } else {
            val currency = all.find { it.id == transaction.values.accountId }?.currency
            all.filter { it.currency == currency }
        }
        _state.update {
            it.copy(
                accounts = accounts,
                hasNoAccount =
                    transaction == null && accounts.isEmpty()
            )
        }
        return accounts
    }

    private suspend fun loadMethods(draft: TransactionDraft) {
        val type = _state.value.accounts.find { it.id == draft.accountId }?.type
            ?: _state.value.transaction?.takeIf {
                it.values.accountId == draft.accountId
            }?.accountType
        loadMethods(type, draft.isIncome)
    }

    private suspend fun loadMethods(accountType: Long?, isIncome: Boolean) {
        val methods = accountType?.let { withContext(io) { store.methods(it, isIncome) } }.orEmpty()
        _state.update { it.copy(methods = methods) }
    }

    /** A new transaction gets the tags of its account, unless the user changed them */
    private suspend fun followAccountTags(fromAccountId: Long, toAccountId: Long) {
        val (from, to) = withContext(io) {
            store.defaultTags(fromAccountId) to
                store.defaultTags(toAccountId)
        }
        val draft = _state.value.draft ?: return
        if (draft.accountId == toAccountId &&
            draft.tagIds.toSet() == from.toSet()
        ) {
            setDraft(draft.copy(tagIds = to))
        }
    }

    private fun suggest(draft: TransactionDraft) {
        suggestJob?.cancel()
        if (draft.categoryId != null && !draft.isCategorySuggested) {
            _state.update { it.copy(suggestion = null) }
            return
        }
        suggestJob = viewModelScope.launch {
            val suggestion = withContext(io) {
                suggestions.suggest(
                    draft.payee.trim().ifEmpty {
                        null
                    },
                    draft.comment.trim().ifEmpty { null },
                    draft.isIncome
                )
            }
            val current = _state.value.draft ?: return@launch
            if (current.categoryId != null && !current.isCategorySuggested) return@launch
            when {
                suggestion?.isCertain == true -> {
                    setDraft(
                        current.copy(
                            categoryId = suggestion.id,
                            categoryPath = suggestion.path,
                            isCategorySuggested = true
                        )
                    )
                    _state.update { it.copy(suggestion = null) }
                }

                else -> {
                    // A category taken from a suggestion for another payee no longer fits
                    if (current.isCategorySuggested) {
                        setDraft(
                            current.copy(
                                categoryId = null,
                                categoryPath = null,
                                isCategorySuggested = false
                            )
                        )
                    }
                    _state.update { it.copy(suggestion = suggestion) }
                }
            }
        }
    }

    private fun setDraft(draft: TransactionDraft?) {
        savedState[KEY_DRAFT] = draft
        _state.update {
            it.copy(draft = draft, isAmountInvalid = it.isAmountInvalid && draft != null)
        }
    }

    private companion object {
        const val KEY_DRAFT = "transactionDraft"
    }
}
