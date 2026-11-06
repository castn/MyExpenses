package org.totschnig.myexpenses.export.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.findAccountType
import org.totschnig.myexpenses.model.PREDEFINED_NAME_BANK
import org.totschnig.myexpenses.model.PREDEFINED_NAME_LIABILITY
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.viewmodel.data.BalanceData
import org.totschnig.myexpenses.viewmodel.data.DisplayDebt
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FullAccount.Companion.partitionByAccountType

/**
 * Pins the two other documents that are printed. They share the paper and margins of the print
 * preferences and the header and footer, but not the layout.
 */
@RunWith(AndroidJUnit4::class)
class BalanceSheetAndDebtPdfTest : BasePdfTest() {

    private val date = LocalDate.of(2025, 3, 15)

    private fun fullAccount(
        label: String,
        type: String,
        balance: Long,
        currency: String = HOME,
        equivalentBalance: Long = balance
    ) = FullAccount(
        id = label.hashCode().toLong(),
        label = label,
        type = repository.findAccountType(type)!!,
        currencyUnit = currencyContext[currency],
        currentBalance = balance,
        equivalentCurrentBalance = equivalentBalance
    )

    private fun balanceSheet(data: BalanceData, debts: Long = 0) =
        BalanceSheetPdfGenerator(application)
            .generatePdf(newDestDir(), data, debts, date)
            .toPrinted()

    private fun debt(
        label: String,
        payeeId: Long,
        payeeName: String,
        amount: Long,
        sum: Long = 0,
        equivalentAmount: Long? = null
    ) = DisplayDebt(
        id = label.hashCode().toLong(),
        label = label,
        description = "",
        payeeId = payeeId,
        amount = amount,
        currency = currencyContext[HOME],
        date = 0,
        payeeName = payeeName,
        sum = sum,
        equivalentAmount = equivalentAmount
    )

    private fun debtOverview(vararg groups: List<DisplayDebt>) =
        DebtOverviewPdfGenerator(application).generatePdf(newDestDir(), groups.toList()).toPrinted()

    private val mixedAccounts
        get() = listOf(
            fullAccount("Giro", PREDEFINED_NAME_BANK, 100000),
            fullAccount("Loan", PREDEFINED_NAME_LIABILITY, -30000)
        ).partitionByAccountType()

    // Balance sheet

    @Test
    fun balanceSheetFileAndTitleWithDate() {
        val pdf = balanceSheet(mixedAccounts)
        assertThat(pdf.file.name).matches("""BalanceSheet-\d{8}-\d{6}\.pdf""")
        assertThat(pdf.texts).containsAtLeast("Balance Sheet", "March 15, 2025").inOrder()
    }

    @Test
    fun balanceSheetListsSectionsAccountsAndNetWorth() {
        assertThat(balanceSheet(mixedAccounts).lines).containsAtLeast(
            "Assets | €1,000.00",
            "Bank | €1,000.00",
            "Giro | €1,000.00",
            "Liabilities | €300.00",
            "Other Liabilities | €300.00",
            "Loan | €300.00",
            "Net Worth | €700.00"
        ).inOrder()
    }

    @Test
    fun amountsOfSectionsAreAbsoluteAndOnlyTheNetWorthHasASign() {
        showNegativeSignInPdf()
        val data = listOf(fullAccount("Loan", PREDEFINED_NAME_LIABILITY, -30000))
            .partitionByAccountType()
        assertThat(balanceSheet(data).lines).containsAtLeast(
            "Liabilities | 300.00",
            "Loan | 300.00",
            "Net Worth | N300.00"
        ).inOrder()
    }

    @Test
    fun negativeAccountIsColouredAsExpense() {
        val pdf = balanceSheet(mixedAccounts)
        assertThat(pdf.pieces.last { it.text == "€300.00" }.color?.rgb).isEqualTo(EXPENSE_COLOR)
        assertThat(pdf.pieces.first { it.text == "€1,000.00" }.color?.rgb).isEqualTo(INCOME_COLOR)
    }

    @Test
    fun debtsAreALineOfTheirOwnAndAddedWhenNotZero() {
        val with = balanceSheet(mixedAccounts, debts = 5000)
        assertThat(with.lines).containsAtLeast("Debts | €50.00", "Net Worth | €750.00").inOrder()
        assertThat(balanceSheet(mixedAccounts).lines.none { it.startsWith("Debts") }).isTrue()
    }

    @Test
    fun foreignAccountShowsTheEquivalentAndBelowItsOwnBalance() {
        val data = listOf(
            fullAccount("Dollar", PREDEFINED_NAME_BANK, 1000, "USD", 2000),
            fullAccount("Zero", PREDEFINED_NAME_BANK, 0, "USD", 0)
        ).partitionByAccountType()
        val lines = balanceSheet(data).lines
        assertThat(lines).containsAtLeast("Dollar | €20.00", "$10.00", "Zero | €0.00").inOrder()
        assertThat(lines).doesNotContain("$0.00")
    }

    @Test
    fun balanceSheetUsesThePaperOfThePrintPreferencesAndTheFooter() {
        prefHandler.putString(PrefKey.PRINT_PAPER_ORIENTATION, "LANDSCAPE")
        prefHandler.putString(PrefKey.PRINT_FOOTER_RIGHT, "Page {page}")
        val pdf = balanceSheet(mixedAccounts)
        assertThat(pdf.pageSizes.single().width).isGreaterThan(pdf.pageSizes.single().height)
        assertThat(pdf.texts).contains("Page 1")
    }

    @Test
    fun balanceSheetWithoutAccountsStillPrints() {
        assertThat(balanceSheet(emptyList<FullAccount>().partitionByAccountType()).lines)
            .containsAtLeast("Assets | €0.00", "Liabilities | €0.00", "Net Worth | €0.00")
            .inOrder()
    }

    // Debt overview

    @Test
    fun debtOverviewFileAndTitle() {
        val pdf = debtOverview(listOf(debt("Rent", 1, "Alice", 1000)))
        assertThat(pdf.file.name).matches("""DebtOverview-\d{8}-\d{6}\.pdf""")
        assertThat(pdf.texts).contains("Debt Overview")
    }

    @Test
    fun debtOverviewSumsPerPayeeAndInTotal() {
        val pdf = debtOverview(
            listOf(debt("Rent", 1, "Alice", 1000, sum = 200), debt("Car", 1, "Alice", 500)),
            listOf(debt("Book", 2, "Bob", -300))
        )
        assertThat(pdf.lines).containsAtLeast(
            "Alice | €13.00",
            "Rent | €8.00",
            "Car | €5.00",
            "Bob | €3.00",
            "Book | €3.00",
            "Total | €10.00"
        ).inOrder()
    }

    @Test
    fun debtOverviewUsesTheEquivalentAmountAndKeepsTheSign() {
        showNegativeSignInPdf()
        val pdf = debtOverview(
            listOf(debt("Foreign", 1, "Alice", 1000, equivalentAmount = 2500)),
            listOf(debt("Book", 2, "Bob", -300))
        )
        assertThat(pdf.lines).containsAtLeast(
            "Foreign | 25.00",
            "Book | N3.00",
            "Total | 22.00"
        ).inOrder()
        assertThat(pdf.pieces.first { it.text == "N3.00" }.color?.rgb).isEqualTo(EXPENSE_COLOR)
    }

    @Test
    fun debtOverviewWithoutDebtsPrintsTheTotal() {
        assertThat(debtOverview().lines).contains("Total | €0.00")
    }
}
