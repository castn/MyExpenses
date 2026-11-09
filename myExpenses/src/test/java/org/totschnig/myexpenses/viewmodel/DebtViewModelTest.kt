package org.totschnig.myexpenses.viewmodel

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.storeExchangeRate
import org.totschnig.myexpenses.model.sort.Sort
import org.totschnig.myexpenses.model.sort.SortDirection
import org.totschnig.myexpenses.util.getDateTimeFormatter

/**
 * Pins what the debt view models compute on top of the queries: the running totals of the details,
 * the export and the settings of the overview.
 */
@RunWith(AndroidJUnit4::class)
class DebtViewModelTest : BaseDebtTest() {

    private val accountId by lazy { insertAccount("Cash", currency = HOME) }

    private val foreignAccountId by lazy {
        insertAccount("Dollar", currency = FOREIGN).also {
            repository.storeExchangeRate(it, 2.0, FOREIGN, HOME)
        }
    }

    private val day1 = LocalDate.of(2025, 2, 1)
    private val day2 = LocalDate.of(2025, 2, 2)
    private val day3 = LocalDate.of(2025, 2, 3)

    @Before
    fun clearOverviewSettings() = runBlocking<Unit> {
        dataStore.edit { it.clear() }
    }

    private fun transactions(id: Long) = viewModel.loadTransactions(displayDebt(id)).await()

    private fun formatted(date: LocalDate) = getDateTimeFormatter(application).format(date)

    private fun exportLines(id: Long) =
        viewModel.exportText(application, displayDebt(id)).await().lines()

    private fun overview(vararg labels: String) =
        viewModel.debts.awaitMatching { (_, debts) -> debts.map { it.label } == labels.toList() }
            .second.map { it.label }

    // Running totals

    @Test
    fun loadDebtReadsStoredValuesWithoutSums() {
        val id = saveDebt(amount = 1000, currency = FOREIGN, equivalentAmount = 2000)
        insertLinked(foreignAccountId, 100, id)
        viewModel.closeDebt(id)
        val debt = viewModel.loadDebt(id).awaitMatching { it != null }!!
        assertThat(debt.isSealed).isTrue()
        assertThat(debt.amount).isEqualTo(1000)
        assertThat(debt.equivalentAmount).isEqualTo(2000)
        // the single row query has no sum columns, so the balance is the amount
        assertThat(debt.sum).isEqualTo(0)
        assertThat(debt.equivalentSum).isEqualTo(0)
        assertThat(debt.currentBalance).isEqualTo(1000)
    }

    @Test
    fun runningTotalFollowsTheDateAndFlipsTheSign() {
        val id = saveDebt(amount = 1000)
        val second = insertLinked(accountId, -50, id, date = day2)
        val third = insertLinked(accountId, 200, id, date = day3)
        val first = insertLinked(accountId, 300, id, date = day1)
        insertLinked(accountId, 7, null, date = day1)

        val rows = transactions(id)

        assertThat(rows.map { it.id }).containsExactly(first, second, third).inOrder()
        assertThat(rows.map { it.date }).containsExactly(day1, day2, day3).inOrder()
        assertThat(rows.map { it.amount }).containsExactly(-300L, 50L, -200L).inOrder()
        assertThat(rows.map { it.runningTotal }).containsExactly(700L, 750L, 550L).inOrder()
        assertThat(rows.map { it.equivalentRunningTotal })
            .containsExactly(700L, 750L, 550L).inOrder()
        assertThat(rows.last().runningTotal).isEqualTo(displayDebt(id).currentBalance)
    }

    @Test
    fun splitPartIsListedWithItsParent() {
        val id = saveDebt(amount = 1000)
        val split = insertSplitWithLinkedPart(accountId, id, day1)
        val row = transactions(id).single()
        assertThat(row.amount).isEqualTo(-30)
        assertThat(row.parentId).isEqualTo(split)
        assertThat(row.runningTotal).isEqualTo(970)
    }

    @Test
    fun homeDebtLeavesForeignTransactionsOutOfTheRunningTotal() {
        val id = saveDebt(amount = 1000)
        insertLinked(foreignAccountId, 100, id, date = day1)
        val row = transactions(id).single()
        assertThat(row.currency).isEqualTo(FOREIGN)
        assertThat(row.amount).isEqualTo(-100)
        assertThat(row.equivalentAmount).isEqualTo(200)
        // Looks like a bug: transactionsFlow compares the currency of the transaction with home
        // where its message speaks of the currency of the debt. This link is the one the edit
        // screen offers, it reports a crash, and the list shows a balance of 800.
        assertThat(row.runningTotal).isEqualTo(1000)
        assertThat(displayDebt(id).currentBalance).isEqualTo(800)
        assertThat(row.equivalentRunningTotal).isEqualTo(800)
    }

    @Test
    fun foreignDebtRunsInBothCurrencies() {
        val id = saveDebt(amount = 1000, currency = FOREIGN, equivalentAmount = 2500)
        insertLinked(foreignAccountId, 100, id, date = day1)
        val row = transactions(id).single()
        assertThat(row.runningTotal).isEqualTo(900)
        assertThat(row.equivalentRunningTotal).isEqualTo(2300)
    }

    @Test
    fun foreignDebtIgnoresTransactionsOfAThirdCurrencyInTheRunningTotal() {
        val id = saveDebt(amount = 1000, currency = FOREIGN, equivalentAmount = 2000)
        val third = insertAccount("Franc", currency = THIRD)
        repository.storeExchangeRate(third, 3.0, THIRD, HOME)
        insertLinked(third, 100, id, date = day1)
        val row = transactions(id).single()
        // Looks like a bug: the details leave the row out of the total and report a crash, while
        // the list counts it in the sum, so both show a different balance.
        assertThat(row.runningTotal).isEqualTo(1000)
        assertThat(displayDebt(id).currentBalance).isEqualTo(900)
        assertThat(row.equivalentRunningTotal).isEqualTo(1700)
    }

    @Test
    fun foreignDebtTakesHomeCurrencyTransactionsWithoutConverting() {
        val id = saveDebt(amount = 1000, currency = FOREIGN, equivalentAmount = 2000)
        insertLinked(accountId, 100, id, date = day1)
        val row = transactions(id).single()
        // Looks like a bug: the check for home currency sends this link down the branch meant for
        // the home debt, so 100 cents of home currency reduce a dollar balance by 100 cents.
        assertThat(row.runningTotal).isEqualTo(900)
        assertThat(row.equivalentRunningTotal).isEqualTo(1900)
    }

    // Export

    @Test
    fun exportTextListsOpeningRowAndTransactions() {
        val id = saveDebt(amount = 10000, label = "Car", description = "For the car")
        insertLinked(accountId, 2500, id, date = day1)
        insertLinked(accountId, -1000, id, date = day2)
        val lines = exportLines(id)
        assertThat(lines[0]).isEqualTo("Car")
        assertThat(lines[1]).isEqualTo("Alice owes me")
        assertThat(lines[2]).isEqualTo("For the car")
        assertThat(lines[3]).isEmpty()
        val width = listOf(DEBT_DATE, day1, day2).maxOf { formatted(it).length }
        assertThat(lines.subList(4, lines.size)).containsExactly(
            "${formatted(DEBT_DATE).padStart(width)} |         | €100.00",
            "${formatted(day1).padStart(width)} | \u2212€25.00 |  €75.00",
            "${formatted(day2).padStart(width)} |  €10.00 |  €85.00",
            ""
        ).inOrder()
    }

    @Test
    fun exportTextLeavesOutABlankDescription() {
        val id = saveDebt(amount = 10000, label = "Car")
        val lines = exportLines(id)
        assertThat(lines[1]).isEqualTo("Alice owes me")
        assertThat(lines[2]).isEmpty()
    }

    @Test
    fun exportTextShowsForeignTransactionsWithoutFlippingTheSign() {
        val id = saveDebt(amount = 10000, label = "Car")
        insertLinked(accountId, 500, id, date = day1)
        insertLinked(foreignAccountId, 500, id, date = day2)
        val lines = exportLines(id)
        // Looks like a bug: rows in the debt currency show the negated amount, rows in another
        // currency show the equivalent amount as it is, so the same direction gets both signs.
        assertThat(lines[lines.size - 3]).contains("| \u2212€5.00 |  €95.00")
        assertThat(lines[lines.size - 2]).contains("| €10.00 |  €95.00")
    }

    @Test
    fun exportHtmlWritesATableWithTheLastBalanceInBold() {
        resetFileProviderCache()
        val id = saveDebt(amount = 10000, label = "Car", description = "For the car")
        insertLinked(accountId, 2500, id, date = day1)
        viewModel.exportHtml(application, displayDebt(id)).await()
        val html = File(application.cacheDir, "debt_$id.html").readText()
        assertThat(html).contains("<b>Car</b>")
        assertThat(html).contains("Alice owes me")
        assertThat(html).contains("For the car")
        assertThat(html.substringAfter("<table>").split("<tr>")).hasSize(3)
        assertThat(html.replace(Regex("\\s"), "")).contains("<td>€100.00</td>")
        assertThat(html.replace(Regex("\\s"), "")).contains("<td><b>€75.00</b></td>")
    }

    // Overview

    @Test
    fun overviewDefaultsToLabelAscendingWithoutSealedAndSettledDebts() {
        saveDebt(amount = 100, label = "B")
        saveDebt(amount = 100, label = "A")
        val sealed = saveDebt(amount = 100, label = "Sealed")
        val settled = saveDebt(amount = 100, label = "Settled")
        insertLinked(accountId, 100, settled)
        viewModel.closeDebt(sealed)
        assertThat(overview("A", "B")).containsExactly("A", "B").inOrder()
        assertThat(viewModel.debts.value.first).isEqualTo(Sort.LABEL)
    }

    @Test
    fun showAllAddsSealedAndSettledDebts() {
        saveDebt(amount = 100, label = "A")
        val sealed = saveDebt(amount = 100, label = "Sealed")
        val settled = saveDebt(amount = 100, label = "Settled")
        insertLinked(accountId, 100, settled)
        viewModel.closeDebt(sealed)
        runBlocking { viewModel.persistShowAll(true) }
        assertThat(overview("A", "Sealed", "Settled"))
            .containsExactly("A", "Sealed", "Settled").inOrder()
    }

    @Test
    fun labelSortCanBeDescending() {
        saveDebt(amount = 100, label = "A")
        saveDebt(amount = 100, label = "B")
        runBlocking { viewModel.persistSortDirection(SortDirection.DESC) }
        assertThat(overview("B", "A")).containsExactly("B", "A").inOrder()
    }

    @Test
    fun debtSumSortUsesTheEquivalentBalance() {
        saveDebt(amount = 300, label = "Middle")
        saveDebt(amount = 100, currency = FOREIGN, equivalentAmount = 900, label = "Largest")
        saveDebt(amount = -500, label = "Negative")
        runBlocking { viewModel.persistSortOrder(Sort.DEBT_SUM) }
        assertThat(overview("Negative", "Middle", "Largest"))
            .containsExactly("Negative", "Middle", "Largest").inOrder()
        assertThat(viewModel.debts.value.first).isEqualTo(Sort.DEBT_SUM)
        runBlocking { viewModel.persistSortDirection(SortDirection.DESC) }
        assertThat(overview("Largest", "Middle", "Negative"))
            .containsExactly("Largest", "Middle", "Negative").inOrder()
    }

    @Test
    fun storedAmountSortIsReadAsDebtSum() {
        runBlocking {
            viewModel.persistSortOrder(Sort.AMOUNT)
            assertThat(viewModel.sortOrder().first()).isEqualTo(Sort.DEBT_SUM)
        }
    }
}
