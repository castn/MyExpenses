package org.totschnig.myexpenses.export.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.archive
import org.totschnig.myexpenses.db2.insertTransfer
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.sort.SortDirection
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.filter.CategoryCriterion
import org.totschnig.myexpenses.viewmodel.Notes

/**
 * Pins the groups and the summary above each of them. The delta sign and the arrows are outside of
 * WinAnsi, so the delta reads ": + 1.00" in these tests.
 */
@RunWith(AndroidJUnit4::class)
class PdfPrinterSumsTest : BasePdfTest() {

    private val accountId by lazy { insertAccount("Cash", openingBalance = 10000) }
    private val account by lazy {
        pageAccount(accountId, grouping = Grouping.MONTH, openingBalance = 10000)
    }
    private val march = LocalDateTime.of(2025, 3, 10, 9, 0)
    private val february = LocalDateTime.of(2025, 2, 10, 9, 0)

    private fun summaries(pdf: PrintedPdf) = pdf.pieces
        .map { it.text }
        .filter {
            it.startsWith("Start:") || it.startsWith("End:") || it.startsWith(":") ||
                it.startsWith("Income:") || it.startsWith("Expenses:") ||
                it.startsWith("Transfers:")
        }

    @Test
    fun groupsFollowTheSortOfTheTransactionsAndHaveATitle() {
        book(accountId, date = march)
        book(accountId, date = february)
        assertThat(print(account).texts)
            .containsAtLeast("March 2025", "February 2025").inOrder()
        assertThat(print(account.copy(sortDirection = SortDirection.ASC)).texts)
            .containsAtLeast("February 2025", "March 2025").inOrder()
    }

    @Test
    fun titlesOfTheOtherGroupings() {
        book(accountId, date = DATE)
        val titled = { grouping: Grouping -> print(account.copy(grouping = grouping)).texts }
        assertThat(titled(Grouping.DAY)).contains("Saturday, March 15, 2025")
        assertThat(titled(Grouping.WEEK)).contains("3/9 - 3/15")
        assertThat(titled(Grouping.YEAR)).contains("2025")
    }

    @Test
    fun withoutGroupingThereIsOneSummaryAndNoTitle() {
        book(accountId, date = march)
        book(accountId, date = february)
        val pdf = print(account.copy(grouping = Grouping.NONE))
        assertThat(summaries(pdf).count { it.startsWith("Start:") }).isEqualTo(1)
        assertThat(pdf.texts).containsNoneOf("Total", "March 2025", "February 2025")
    }

    @Test
    fun summaryShowsStartDeltaEndAndTheThreeSums() {
        book(accountId, -500, date = march)
        book(accountId, 1200, date = march)
        repository.insertTransfer(accountId, insertAccount("Giro"), -250, date = march)
        assertThat(summaries(print(account))).containsExactly(
            "Start: €100.00",
            ": + €4.50",
            "End: €104.50",
            "Income: + €12.00",
            "Expenses: - €5.00",
            "Transfers: - €2.50"
        )
    }

    @Test
    fun balancesCarryFromOneGroupToTheNextStartingAtTheOpeningBalance() {
        book(accountId, 1000, date = february)
        book(accountId, -300, date = march)
        assertThat(summaries(print(account))).containsExactly(
            "Start: €110.00",
            ": - €3.00",
            "End: €107.00",
            "Income: + €0.00",
            "Expenses: - €3.00",
            "Transfers: €0.00",
            "Start: €100.00",
            ": + €10.00",
            "End: €110.00",
            "Income: + €10.00",
            "Expenses: - €0.00",
            "Transfers: €0.00"
        ).inOrder()
    }

    @Test
    fun startBalanceComesFromThePassedAccountNotFromTheDatabase() {
        book(accountId, date = march)
        val pdf = print(account.copy(openingBalance = 50))
        assertThat(summaries(pdf)).contains("Start: €0.50")
    }

    @Test
    fun incomingTransferHasAPlusSign() {
        repository.insertTransfer(accountId, insertAccount("Giro"), 250, date = march)
        assertThat(summaries(print(account))).contains("Transfers: + €2.50")
    }

    @Test
    fun sumsAreColoured() {
        book(accountId, date = march)
        val pdf = print(account)
        fun colorOf(prefix: String) = pdf.pieces.first { it.text.startsWith(prefix) }.color?.rgb
        assertThat(colorOf("Income:")).isEqualTo(INCOME_COLOR)
        assertThat(colorOf("Expenses:")).isEqualTo(EXPENSE_COLOR)
        assertThat(colorOf("Transfers:")).isEqualTo(TRANSFER_COLOR)
    }

    @Test
    fun theSecondSummaryLineThatIsBuiltIsNotPrinted() {
        book(accountId, 1200, date = march)
        // The code also builds a table with "+ sum", "- sum" and the transfer sum, but only adds
        // the first table to the document.
        assertThat(print(account).texts).containsNoneOf("+ €12.00", "- €0.00")
    }

    @Test
    fun voidedTransactionsAreLeftOutOfTheSumsButNotTheList() {
        book(accountId, -500, date = march, comment = "Kept")
        book(accountId, -700, date = march, comment = "Voided", crStatus = CrStatus.VOID)
        val pdf = print(account)
        assertThat(pdf.texts).containsAtLeast("Kept", "Voided")
        assertThat(summaries(pdf)).containsAtLeast("Expenses: - €5.00", "End: €95.00")
    }

    @Test
    fun partsOfASplitAreCountedOnce() {
        insertSplit(accountId, partCategoryIds = listOf(writeCategory("A"), writeCategory("B")))
        assertThat(summaries(print(account))).containsAtLeast("Expenses: - €6.00", ": - €6.00")
    }

    @Test
    fun anArchiveCountsItsTransfersAsExpenses() {
        book(accountId, -500, date = february)
        repository.insertTransfer(accountId, insertAccount("Giro"), -250, date = february)
        book(accountId, -100, date = march)
        val before = summaries(print(account))
        assertThat(before).containsAtLeast("Expenses: - €5.00", "Transfers: - €2.50")
        repository.archive(accountId, LocalDate.of(2025, 2, 1) to LocalDate.of(2025, 2, 28))
        // Looks like a bug: the balance is right, but the transfer moves to the expenses as soon
        // as the transaction is in an archive.
        val after = summaries(print(account))
        assertThat(after).containsAtLeast("Expenses: - €7.50", "Transfers: €0.00", "End: €92.50")
    }

    @Test
    fun sortingAcrossGroupsRepeatsTheGroupWithItsWholeSums() {
        book(accountId, -300, date = march, comment = "Big")
        book(accountId, -200, date = february, comment = "Middle")
        book(accountId, -100, date = march, comment = "Small")
        useLayout(listOf(Notes))
        val pdf = print(account.copy(sortBy = KEY_AMOUNT))
        // Looks like a bug: rows are not sorted by group first, so a group starts again whenever
        // the group of the row changes, each time with the sums of the whole group.
        assertThat(pdf.texts.filter { it.endsWith(" 2025") })
            .containsExactly("March 2025", "February 2025", "March 2025").inOrder()
        assertThat(summaries(pdf).filter { it.startsWith("Expenses:") })
            .containsExactly("Expenses: - €4.00", "Expenses: - €2.00", "Expenses: - €4.00")
            .inOrder()
    }

    // Filter

    @Test
    fun filterLeavesOutRowsAndBalancesAndLimitsTheSums() {
        val bakery = writeCategory("Bakery")
        book(accountId, -500, bakery, comment = "Rolls", date = march)
        book(accountId, -700, comment = "Other", date = march)
        book(accountId, 1000, comment = "Older", date = february)
        val pdf = print(account, CategoryCriterion("Bakery", bakery))
        assertThat(pdf.texts).contains("Rolls")
        assertThat(pdf.texts).containsNoneOf("Other", "Older", "February 2025")
        assertThat(summaries(pdf)).containsExactly(
            ": - €5.00",
            "Income: + €0.00",
            "Expenses: - €5.00",
            "Transfers: €0.00"
        )
    }

    @Test
    fun filterMatchingAPartPrintsTheWholeSplitButSumsOnlyThePart() {
        val first = writeCategory("First")
        val second = writeCategory("Second")
        insertSplit(accountId, partCategoryIds = listOf(first, second), date = march)
        val pdf = print(account, CategoryCriterion("First", first))
        assertThat(pdf.texts).containsAtLeast("€6.00", "Part 1", "Part 0")
        // The row and the sum of the group do not agree in this case.
        assertThat(summaries(pdf)).contains("Expenses: - €3.00")
    }
}
