package org.totschnig.myexpenses.next.feature.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.data.debts.DebtChoice
import org.totschnig.myexpenses.next.data.debts.DebtDatabase
import org.totschnig.myexpenses.next.data.debts.debtChoice
import timber.log.Timber

/** What can be done with the debt of a transaction in its details */
interface DebtActions {
    /** The transaction pays the debt from now on */
    fun link(transactionId: Long, debtId: Long)

    fun unlink(transactionId: Long)

    fun dismissFailure()
}

/**
 * The debts of transactions in their details: which debt a transaction pays, or could pay, and
 * what is left after it, see [debtChoice]
 */
class DebtsViewModel(
    private val database: DebtDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(),
    DebtActions {

    private val _failedFor = MutableStateFlow<Long?>(null)

    /** The transaction whose debt could not be changed, until dismissed */
    val failedFor: StateFlow<Long?> = _failedFor.asStateFlow()

    /** What the transaction pays or could pay; again after each change of debts or transactions */
    fun choice(transactionId: Long): Flow<DebtChoice> = database.changes().map {
        load(transactionId)
    }.flowOn(io)

    private fun load(transactionId: Long): DebtChoice {
        val transaction = database.transaction(transactionId) ?: return DebtChoice.None
        val linked = transaction.debtId?.let(database::debt)
        val debtsOfPayee = transaction.payeeId?.let(database::debtsOf).orEmpty()
        val debtIds = (debtsOfPayee.map { it.id } + listOfNotNull(linked?.id)).toSet()
        val payments = if (debtIds.isEmpty()) emptyList() else database.paymentsOf(debtIds)
        return debtChoice(transaction, linked, debtsOfPayee, payments, database.homeCurrency)
    }

    override fun link(transactionId: Long, debtId: Long) = write(transactionId, debtId)

    override fun unlink(transactionId: Long) = write(transactionId, null)

    private fun write(transactionId: Long, debtId: Long?) {
        _failedFor.value = null
        viewModelScope.launch(io) {
            try {
                database.link(transactionId, debtId)
            } catch (e: IllegalStateException) {
                Timber.w(e)
                _failedFor.value = transactionId
            }
        }
    }

    override fun dismissFailure() {
        _failedFor.value = null
    }
}
