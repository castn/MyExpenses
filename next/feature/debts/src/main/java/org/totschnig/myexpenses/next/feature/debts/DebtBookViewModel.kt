package org.totschnig.myexpenses.next.feature.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.debts.Debt
import org.totschnig.myexpenses.next.data.debts.DebtCheck
import org.totschnig.myexpenses.next.data.debts.DebtDatabase
import org.totschnig.myexpenses.next.data.debts.DebtDraft
import org.totschnig.myexpenses.next.data.debts.DebtEntry
import org.totschnig.myexpenses.next.data.debts.DebtOverview
import org.totschnig.myexpenses.next.data.debts.DebtOverviewSettings
import org.totschnig.myexpenses.next.data.debts.DebtOverviewStore
import org.totschnig.myexpenses.next.data.debts.DebtProblem
import org.totschnig.myexpenses.next.data.debts.DebtSort
import org.totschnig.myexpenses.next.data.debts.DebtSummary
import org.totschnig.myexpenses.next.data.debts.DebtWriting
import org.totschnig.myexpenses.next.data.debts.debtOverview
import org.totschnig.myexpenses.next.data.debts.history
import org.totschnig.myexpenses.next.data.debts.summary
import org.totschnig.myexpenses.next.data.debts.toDraft
import org.totschnig.myexpenses.next.data.debts.validate
import timber.log.Timber

/** A debt with what is left and its history, see [DebtBookViewModel.detail] */
data class DebtDetail(val summary: DebtSummary, val history: List<DebtEntry>)

/**
 * The form of a debt
 *
 * @param problems what is missing or wrong, shown after trying to save
 * @param savedId set once the draft is saved, until [DebtBookActions.savedShown]
 */
data class DebtFormState(
    val draft: DebtDraft? = null,
    val problems: Set<DebtProblem> = emptySet(),
    val payees: List<String> = emptyList(),
    val currencies: List<CurrencyInfo> = emptyList(),
    val savedId: Long? = null
)

/** What a change of a debt resulted in, shown once */
enum class DebtMessage { Refused, Deleted }

/** What can be done on the pages of the debts */
interface DebtBookActions {
    fun setSort(sort: DebtSort)

    fun setShowAll(showAll: Boolean)

    /** Starts the form of a new debt, of [payee] if given, or of [debt] to change it */
    fun startForm(debt: Debt? = null, payee: String = "")

    fun changeDraft(draft: DebtDraft)

    fun saveDraft()

    fun savedShown()

    fun cancelForm()

    fun setSealed(debt: Debt, isSealed: Boolean)

    fun delete(debt: Debt)

    fun messageShown()
}

/** The pages of the debts: overview, a debt with its history, and the form */
class DebtBookViewModel(
    private val database: DebtDatabase,
    private val writing: DebtWriting,
    private val store: DebtOverviewStore,
    private val savedState: SavedStateHandle,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(),
    DebtBookActions {

    val settings: StateFlow<DebtOverviewSettings> =
        store.settings.stateWhileShown(viewModelScope, DebtOverviewSettings())

    /** Null while loading */
    val overview: StateFlow<DebtOverview?> =
        combine(database.changes(), store.settings) { _, settings -> loadOverview(settings) }
            .flowOn(io)
            .stateWhileShown(viewModelScope, null)

    private fun loadOverview(settings: DebtOverviewSettings): DebtOverview {
        val debts = database.allDebts()
        val ids = debts.map { it.id }
        val payments = if (ids.isEmpty()) emptyList() else database.paymentsOf(ids)
        val home = database.homeCurrency.code
        return debtOverview(debts, payments, home, settings.sort, settings.showAll)
    }

    /** The debt with its history; null if it is gone */
    fun detail(debtId: Long): Flow<DebtDetail?> = database.changes().map {
        database.debt(debtId)?.let { debt ->
            val payments = database.paymentsOf(listOf(debtId))
            val home = database.homeCurrency.code
            DebtDetail(debt.summary(payments, home), debt.history(payments, home))
        }
    }.flowOn(io)

    private val _form = MutableStateFlow(DebtFormState(draft = savedState[KEY_DRAFT]))
    val form: StateFlow<DebtFormState> = _form.asStateFlow()

    private val _message = MutableStateFlow<DebtMessage?>(null)
    val message: StateFlow<DebtMessage?> = _message.asStateFlow()

    val homeCurrency: CurrencyInfo get() = database.homeCurrency

    init {
        if (_form.value.draft != null) loadChoices()
    }

    override fun setSort(sort: DebtSort) {
        viewModelScope.launch { store.setSort(sort) }
    }

    override fun setShowAll(showAll: Boolean) {
        viewModelScope.launch { store.setShowAll(showAll) }
    }

    override fun startForm(debt: Debt?, payee: String) {
        val home = database.homeCurrency
        val draft = debt?.toDraft(home)
            ?: DebtDraft(payee = payee, currency = home.code, date = LocalDate.now())
        setDraft(draft)
        _form.update { it.copy(problems = emptySet(), savedId = null) }
        loadChoices()
    }

    private fun loadChoices() {
        viewModelScope.launch(io) {
            val payees = writing.payeeNames()
            val currencies = writing.currencies()
            _form.update { it.copy(payees = payees, currencies = currencies) }
        }
    }

    override fun changeDraft(draft: DebtDraft) = setDraft(draft)

    private fun setDraft(draft: DebtDraft?) {
        savedState[KEY_DRAFT] = draft
        _form.update { it.copy(draft = draft) }
    }

    override fun saveDraft() {
        val draft = _form.value.draft ?: return
        val home = database.homeCurrency
        val currency = _form.value.currencies.find { it.code == draft.currency }
        val fractionDigits = currency?.fractionDigits ?: home.fractionDigits
        when (val check = draft.validate(fractionDigits, home)) {
            is DebtCheck.Invalid -> _form.update { it.copy(problems = check.problems) }

            is DebtCheck.Valid -> viewModelScope.launch(io) {
                try {
                    val id = writing.save(check.values)
                    setDraft(null)
                    _form.update { it.copy(problems = emptySet(), savedId = id) }
                } catch (e: IllegalStateException) {
                    Timber.w(e)
                    _message.value = DebtMessage.Refused
                }
            }
        }
    }

    override fun savedShown() {
        _form.update { it.copy(savedId = null) }
    }

    override fun cancelForm() {
        setDraft(null)
        _form.update { it.copy(problems = emptySet()) }
    }

    override fun setSealed(debt: Debt, isSealed: Boolean) = write {
        writing.setSealed(debt.id, isSealed)
    }

    override fun delete(debt: Debt) = write {
        if (writing.delete(debt.id)) _message.value = DebtMessage.Deleted
    }

    private fun write(block: () -> Unit) {
        viewModelScope.launch(io) {
            try {
                block()
            } catch (e: IllegalStateException) {
                Timber.w(e)
                _message.value = DebtMessage.Refused
            }
        }
    }

    override fun messageShown() {
        _message.value = null
    }

    private companion object {
        const val KEY_DRAFT = "debtDraft"
    }
}
