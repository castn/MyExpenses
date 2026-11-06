package org.totschnig.myexpenses.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.storeExchangeRate
import org.totschnig.myexpenses.injector

/**
 * Pins the sums and balances the debt queries return: what is counted, what the filters of
 * loadDebts do, and how other currencies are converted.
 */
@RunWith(AndroidJUnit4::class)
class DebtSumTest : BaseDebtTest() {

    private val accountId by lazy { insertAccount("Cash", currency = HOME) }

    private fun foreignAccount(
        currency: String = FOREIGN,
        rate: Double? = 2.0,
        dynamic: Boolean = false
    ) = insertAccount("Account $currency", currency = currency, dynamic = dynamic).also { id ->
        rate?.let { repository.storeExchangeRate(id, it, currency, HOME) }
    }

    // Same currency

    @Test
    fun balanceIsTheAmountMinusTheSumOfLinkedTransactions() {
        val id = saveDebt(amount = 10000)
        val otherId = saveDebt(amount = 500, label = "Other")
        insertLinked(accountId, 3000, id)
        insertLinked(accountId, -1000, id)
        insertLinked(accountId, 77, otherId)
        insertLinked(accountId, 88, null)
        val debt = displayDebt(id)
        assertThat(debt.sum).isEqualTo(2000)
        assertThat(debt.currentBalance).isEqualTo(8000)
        assertThat(debt.equivalentSum).isEqualTo(2000)
        assertThat(debt.currentEquivalentBalance).isEqualTo(8000)
    }

    @Test
    fun titleFollowsTheSignOfTheBalance() {
        val owesMe = saveDebt(amount = 100, label = "Owes me")
        val iOwe = saveDebt(amount = -100, label = "I owe")
        val settled = saveDebt(amount = 100, label = "Settled")
        insertLinked(accountId, 100, settled)
        assertThat(displayDebt(owesMe).title(application)).isEqualTo("Alice owes me")
        assertThat(displayDebt(iOwe).title(application)).isEqualTo("I owe Alice")
        assertThat(displayDebt(settled).title(application)).isEqualTo("Alice")
    }

    @Test
    fun overpayingTurnsTheBalanceAround() {
        val id = saveDebt(amount = 100)
        insertLinked(accountId, 150, id)
        assertThat(displayDebt(id).currentBalance).isEqualTo(-50)
        assertThat(displayDebt(id).title(application)).isEqualTo("I owe Alice")
    }

    @Test
    fun voidedTransactionStillCounts() {
        val id = saveDebt(amount = 1000)
        val transactionId = insertLinked(accountId, 100, id)
        repository.deleteTransaction(transactionId, markAsVoid = true)
        // Looks like a bug: a voided transaction is left out of account balances, but the debt sum
        // does not filter on the status.
        assertThat(displayDebt(id).sum).isEqualTo(100)
    }

    @Test
    fun splitCountsByTheLinkOfThePart() {
        val id = saveDebt(amount = 1000)
        insertSplitWithLinkedPart(accountId, id)
        assertThat(displayDebt(id).sum).isEqualTo(30)
    }

    // Filters of loadDebts

    @Test
    fun sealedDebtsAreHiddenByDefault() {
        val open = saveDebt(amount = 100, label = "Open")
        val sealed = saveDebt(amount = 100, label = "Sealed")
        viewModel.closeDebt(sealed)
        assertThat(loadDebts().map { it.id }).containsExactly(open)
        assertThat(loadDebts(showSealed = true).map { it.id }).containsExactly(open, sealed)
    }

    @Test
    fun settledDebtsAreHiddenOnlyWithShowZeroOff() {
        val open = saveDebt(amount = 100, label = "Open")
        val settled = saveDebt(amount = 100, label = "Settled")
        insertLinked(accountId, 100, settled)
        assertThat(loadDebts().map { it.id }).containsExactly(open, settled)
        assertThat(loadDebts(showZero = false).map { it.id }).containsExactly(open)
    }

    @Test
    fun rowIdLeavesThatTransactionOutOfTheSum() {
        val id = saveDebt(amount = 1000)
        val edited = insertLinked(accountId, 100, id)
        insertLinked(accountId, 30, id)
        val debt = loadDebts(rowId = edited).single()
        assertThat(debt.sum).isEqualTo(30)
        assertThat(debt.equivalentSum).isEqualTo(30)
        assertThat(loadDebts(rowId = 0).single().sum).isEqualTo(130)
    }

    @Test
    fun dateLimitsDebtsAndTransactionsToThatDayInclusive() {
        val id = saveDebt(amount = 1000, date = LocalDate.of(2025, 1, 10))
        insertLinked(accountId, 100, id, date = LocalDate.of(2025, 1, 15))
        insertLinked(accountId, 30, id, date = LocalDate.of(2025, 2, 15))

        assertThat(loadDebts(date = LocalDate.of(2025, 1, 9))).isEmpty()
        assertThat(loadDebts(date = LocalDate.of(2025, 1, 14)).single().sum).isEqualTo(0)
        assertThat(loadDebts(date = LocalDate.of(2025, 1, 15)).single().sum).isEqualTo(100)
        assertThat(loadDebts(date = LocalDate.of(2025, 2, 15)).single().sum).isEqualTo(130)
    }

    @Test
    fun debtSumOfTheOverviewAddsEquivalentBalancesIncludingSealedDebts() {
        val first = saveDebt(amount = 1000, label = "First")
        val sealed = saveDebt(amount = 200, label = "Sealed")
        saveDebt(amount = 100, currency = FOREIGN, equivalentAmount = 250, label = "Foreign")
        val settled = saveDebt(amount = 40, label = "Settled")
        insertLinked(accountId, 100, first)
        insertLinked(accountId, 40, settled)
        viewModel.closeDebt(sealed)

        val myExpenses = MyExpensesViewModel(application, SavedStateHandle())
            .also { application.injector.inject(it) }
        val sum = myExpenses.debtSum.awaitMatching { it != 0L }
        myExpenses.viewModelScope.cancel()

        assertThat(sum).isEqualTo(900 + 200 + 250)
    }

    // Other currencies

    @Test
    fun homeDebtCountsForeignTransactionsByExchangeRate() {
        val id = saveDebt(amount = 10000)
        insertLinked(foreignAccount(), 1000, id)
        val debt = displayDebt(id)
        assertThat(debt.sum).isEqualTo(2000)
        assertThat(debt.equivalentSum).isEqualTo(2000)
        assertThat(debt.currentBalance).isEqualTo(8000)
        assertThat(debt.currentEquivalentBalance).isEqualTo(8000)
    }

    @Test
    fun exchangeRateResultIsRounded() {
        val id = saveDebt(amount = 10000)
        insertLinked(foreignAccount(rate = 1.5), 333, id)
        assertThat(displayDebt(id).sum).isEqualTo(500)
    }

    @Test
    fun missingExchangeRateCountsAsOne() {
        val id = saveDebt(amount = 10000)
        insertLinked(foreignAccount(rate = null), 1000, id)
        assertThat(displayDebt(id).sum).isEqualTo(1000)
    }

    @Test
    fun dynamicAccountPrefersTheStoredEquivalentAmount() {
        val id = saveDebt(amount = 10000)
        val account = foreignAccount(dynamic = true)
        insertLinked(account, 1000, id, equivalentAmount = 1700)
        assertThat(displayDebt(id).sum).isEqualTo(1700)
        insertLinked(account, 100, id)
        assertThat(displayDebt(id).sum).isEqualTo(1700 + 200)
    }

    @Test
    fun staticAccountIgnoresTheStoredEquivalentAmount() {
        val id = saveDebt(amount = 10000)
        insertLinked(foreignAccount(), 1000, id, equivalentAmount = 1700)
        assertThat(displayDebt(id).sum).isEqualTo(2000)
    }

    @Test
    fun foreignDebtKeepsBothCurrencies() {
        val id = saveDebt(amount = 1000, currency = FOREIGN, equivalentAmount = 2500)
        insertLinked(foreignAccount(), 100, id)
        val debt = displayDebt(id)
        assertThat(debt.sum).isEqualTo(100)
        assertThat(debt.equivalentSum).isEqualTo(200)
        assertThat(debt.currentBalance).isEqualTo(900)
        assertThat(debt.currentEquivalentBalance).isEqualTo(2300)
    }

    @Test
    fun foreignDebtWithoutEquivalentAmountUsesTheForeignAmountAsHomeValue() {
        val id = saveDebt(amount = 1000, currency = FOREIGN)
        insertLinked(foreignAccount(), 100, id)
        assertThat(displayDebt(id).currentEquivalentBalance).isEqualTo(1000 - 200)
    }

    @Test
    fun foreignDebtAddsAmountsOfOtherCurrenciesWithoutConverting() {
        val id = saveDebt(amount = 1000, currency = FOREIGN, equivalentAmount = 2000)
        insertLinked(foreignAccount(currency = THIRD, rate = 3.0), 100, id)
        insertLinked(accountId, 10, id)
        val debt = displayDebt(id)
        // Looks like a bug: the sum adds francs and euros to a dollar debt as they are. The edit
        // screen does not offer these links, but neither the database nor the repository refuse
        // them. The equivalent sum is correct.
        assertThat(debt.sum).isEqualTo(110)
        assertThat(debt.equivalentSum).isEqualTo(310)
    }
}
