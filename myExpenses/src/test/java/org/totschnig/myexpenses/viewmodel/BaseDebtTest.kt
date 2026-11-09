package org.totschnig.myexpenses.viewmodel

import android.content.ContentUris
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import org.totschnig.myexpenses.db2.createSplitTransaction
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.db2.requireParty
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.toEpoch
import org.totschnig.myexpenses.viewmodel.data.Debt
import org.totschnig.myexpenses.viewmodel.data.DisplayDebt

/**
 * Fixtures for the tests that pin what the debt logic does today. A positive debt amount means
 * that the payee owes me. Comments starting with "Looks like a bug" mark behaviour that is pinned,
 * not endorsed.
 */
abstract class BaseDebtTest : BaseViewModelTest() {

    protected lateinit var viewModel: DebtOverViewViewModel

    protected val payee by lazy { repository.requireParty("Alice")!! }

    @Before
    fun setUpDebtTest() {
        prefHandler.putString(PrefKey.HOME_CURRENCY, HOME)
        viewModel = DebtOverViewViewModel(application).also { application.injector.inject(it) }
    }

    @After
    fun tearDownDebtTest() {
        viewModel.viewModelScope.cancel()
    }

    protected fun debt(
        amount: Long,
        currency: String = HOME,
        label: String = "Loan",
        payeeId: Long = payee,
        equivalentAmount: Long? = null,
        date: LocalDate = DEBT_DATE,
        description: String = "",
        id: Long = 0
    ) = Debt(
        id = id,
        label = label,
        description = description,
        payeeId = payeeId,
        amount = amount,
        currency = currencyContext[currency],
        date = date,
        equivalentAmount = equivalentAmount
    )

    protected fun saveDebt(
        amount: Long,
        currency: String = HOME,
        label: String = "Loan",
        equivalentAmount: Long? = null,
        date: LocalDate = DEBT_DATE,
        description: String = ""
    ) = repository.saveDebt(
        debt(
            amount = amount,
            currency = currency,
            label = label,
            equivalentAmount = equivalentAmount,
            date = date,
            description = description
        )
    )

    protected fun insertLinked(
        accountId: Long,
        amount: Long,
        debtId: Long?,
        date: LocalDate = DEBT_DATE,
        equivalentAmount: Long? = null
    ) = repository.insertTransaction(
        accountId = accountId,
        amount = amount,
        date = LocalDateTime.of(date, LocalTime.NOON),
        equivalentAmount = equivalentAmount,
        debtId = debtId
    ).id

    /** A split of 80 with a part of 30 that is linked to the debt. Returns the parent id. */
    protected fun insertSplitWithLinkedPart(
        accountId: Long,
        debtId: Long,
        date: LocalDate = DEBT_DATE
    ): Long {
        val epoch = date.toEpoch()
        return repository.createSplitTransaction(
            Transaction(
                accountId = accountId,
                amount = 80,
                categoryId = SPLIT_CATID,
                date = epoch,
                uuid = generateUuid()
            ),
            listOf(
                Transaction(
                    accountId = accountId,
                    amount = 30,
                    date = epoch,
                    debtId = debtId,
                    uuid = generateUuid()
                ),
                Transaction(
                    accountId = accountId,
                    amount = 50,
                    date = epoch,
                    uuid = generateUuid()
                )
            )
        ).id
    }

    protected fun debtUri(id: Long) = ContentUris.withAppendedId(TransactionProvider.DEBTS_URI, id)

    protected fun loadDebts(
        rowId: Long? = null,
        date: LocalDate? = null,
        showSealed: Boolean = false,
        showZero: Boolean = true
    ): List<DisplayDebt> = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            viewModel.loadDebts(rowId, date, showSealed, showZero).first()
        }
    }

    /** The debt as the list queries return it, that is with its sums. */
    protected fun displayDebt(id: Long) = loadDebts(showSealed = true).single { it.id == id }

    /**
     * The flows of the view model hop back to the main thread, so the looper must run while
     * waiting. Returns the last value when none matches, so that the assertion of the caller shows
     * what was seen instead of a timeout.
     */
    protected fun <T> Flow<T>.awaitMatching(predicate: (T) -> Boolean): T {
        val seen = mutableListOf<T>()
        val job = viewModel.viewModelScope.launch { collect { seen.add(it) } }
        try {
            val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
            while (seen.none(predicate) && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                if (seen.none(predicate)) Thread.sleep(10)
            }
        } finally {
            job.cancel()
        }
        return seen.lastOrNull(predicate) ?: seen.last()
    }

    protected fun <T> LiveData<T>.await(): T {
        var value: T? = null
        val observer = Observer<T> { value = it }
        observeForever(observer)
        try {
            val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
            while (value == null && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                if (value == null) Thread.sleep(10)
            }
        } finally {
            removeObserver(observer)
        }
        return checkNotNull(value) { "LiveData value was never set." }
    }

    companion object {
        const val HOME = "EUR"
        const val FOREIGN = "USD"
        const val THIRD = "CHF"
        val DEBT_DATE: LocalDate = LocalDate.of(2025, 1, 10)
        private const val TIMEOUT_MILLIS = 10_000L
    }
}
