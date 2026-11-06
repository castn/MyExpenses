package org.totschnig.myexpenses.export.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.db2.FLAG_INCOME
import org.totschnig.myexpenses.db2.FLAG_NEUTRAL
import org.totschnig.myexpenses.db2.insertTransfer
import org.totschnig.myexpenses.db2.storeExchangeRate
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.preference.ColorSource
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DataBaseAccount
import org.totschnig.myexpenses.viewmodel.Account
import org.totschnig.myexpenses.viewmodel.Amount
import org.totschnig.myexpenses.viewmodel.Notes
import org.totschnig.myexpenses.viewmodel.OriginalAmount
import org.totschnig.myexpenses.viewmodel.data.PageAccount

/**
 * Pins how amounts are formatted and coloured, in one currency and in aggregates. The ids of
 * aggregates come in two forms: the old screens pass a negative id, and AggregateAccount of
 * MyExpensesV2 passes 0 with an account grouping.
 */
@RunWith(AndroidJUnit4::class)
class PdfPrinterCurrencyTest : BasePdfTest() {

    private val euroId by lazy { insertAccount("Euro") }
    private val euro by lazy { pageAccount(euroId, "Euro") }

    private fun dollarAccount(rate: Double = 2.0) = insertAccount("Dollar", currency = "USD").also {
        repository.storeExchangeRate(it, rate, "USD", HOME)
    }

    private val homeAggregate
        get() = pageAccount(DataBaseAccount.HOME_AGGREGATE_ID, "All")

    private val euroAggregate
        get() = pageAccount(-1, "Sum")

    private fun PrintedPdf.colorOf(text: String) = piece(text).color?.rgb

    // Format

    @Test
    fun amountsAreInTheCurrencyOfTheAccount() {
        val id = insertAccount("Dollar", currency = "USD")
        book(id, -1234)
        val texts = print(pageAccount(id, "Dollar", currency = "USD", currentBalance = -1234)).texts
        assertThat(texts).containsAtLeast("$12.34", "Expenses: - $12.34")
    }

    @Test
    fun currencyWithoutFractionDigitsHasNone() {
        val id = insertAccount("Yen", currency = "JPY")
        book(id, -1234)
        val texts = print(pageAccount(id, "Yen", currency = "JPY")).texts
        assertThat(texts).containsAtLeast("¥1,234", "Expenses: - ¥1,234")
    }

    @Test
    fun amountKeepsItsSignAndTheSumsDoNot() {
        showNegativeSignInPdf()
        book(euroId, -500)
        val texts = print(euro).texts
        assertThat(texts).contains("N5.00")
        assertThat(texts).contains("Expenses: - 5.00")
    }

    @Test
    fun customFormatAppliesToAllAmounts() {
        prefHandler.putString(
            PrefKey.CUSTOM_DECIMAL_FORMAT,
            "0.000"
        )
        book(euroId, 500)
        assertThat(print(euro).texts).containsAtLeast("5.00", "Income: + 5.00")
    }

    @Test
    fun originalAmountIsPrintedInItsCurrencyAndGetsTheSignOfTheAmount() {
        showNegativeSignInPdf()
        book(euroId, -500, originalAmount = 900, originalCurrency = "JPY")
        book(euroId, 500, originalAmount = 700, originalCurrency = "USD", comment = "In")
        useLayout(listOf(OriginalAmount), listOf(Amount))
        val texts = print(euro).texts
        assertThat(texts).containsAtLeast("N900", "7.00")
    }

    @Test
    fun accountWithoutOriginalAmountPrintsNothingForIt() {
        book(euroId, -500)
        useLayout(listOf(OriginalAmount))
        assertThat(print(euro).texts.last()).isEqualTo("Original amount")
    }

    // Colours

    @Test
    fun sourceTypeColoursByTheTypeOfTheTransaction() {
        book(euroId, -500, comment = "Out")
        book(euroId, 700, comment = "In")
        repository.insertTransfer(euroId, insertAccount("Giro"), 300)
        val pdf = print(euro, colorSource = ColorSource.TYPE)
        assertThat(pdf.colorOf("€5.00")).isEqualTo(EXPENSE_COLOR)
        assertThat(pdf.colorOf("€7.00")).isEqualTo(INCOME_COLOR)
        assertThat(pdf.colorOf("€3.00")).isEqualTo(TRANSFER_COLOR)
    }

    @Test
    fun sourceSignColoursTransfersBySign() {
        repository.insertTransfer(euroId, insertAccount("Giro"), 300)
        repository.insertTransfer(euroId, insertAccount("Other"), -400)
        val pdf = print(euro, colorSource = ColorSource.SIGN)
        assertThat(pdf.colorOf("€3.00")).isEqualTo(INCOME_COLOR)
        assertThat(pdf.colorOf("€4.00")).isEqualTo(EXPENSE_COLOR)
    }

    @Test
    fun sourceTypeWithSignKeepsTransfersBlue() {
        repository.insertTransfer(euroId, insertAccount("Giro"), 300)
        book(euroId, -500)
        val pdf = print(euro, colorSource = ColorSource.TYPE_WITH_SIGN)
        assertThat(pdf.colorOf("€3.00")).isEqualTo(TRANSFER_COLOR)
        assertThat(pdf.colorOf("€5.00")).isEqualTo(EXPENSE_COLOR)
    }

    @Test
    fun typeOfTheCategoryDecidesWithSourceTypeAndTheSignWithTheOthers() {
        book(euroId, -500, categoryId = writeCategory("Refund", type = FLAG_INCOME))
        val colorOf = { source: ColorSource -> print(euro, colorSource = source).colorOf("€5.00") }
        assertThat(colorOf(ColorSource.TYPE)).isEqualTo(INCOME_COLOR)
        assertThat(colorOf(ColorSource.SIGN)).isEqualTo(EXPENSE_COLOR)
        assertThat(colorOf(ColorSource.TYPE_WITH_SIGN)).isEqualTo(EXPENSE_COLOR)
    }

    @Test
    fun categoryWithoutATypeIsColouredBySign() {
        book(euroId, -500, categoryId = writeCategory("Neutral", type = FLAG_NEUTRAL))
        assertThat(print(euro).colorOf("€5.00")).isEqualTo(EXPENSE_COLOR)
    }

    @Test
    fun partsOfASplitAreColouredLikeOtherAmounts() {
        insertSplit(euroId, partCategoryIds = listOf(writeCategory("First")))
        val pdf = print(euro)
        assertThat(pdf.pieces.filter { it.text == "€3.00" }.map { it.color?.rgb })
            .containsExactly(EXPENSE_COLOR, EXPENSE_COLOR)
    }

    // Aggregates

    @Test
    fun homeAggregatePrintsTheAmountAndTheEquivalentInHomeCurrency() {
        showNegativeSignInPdf()
        val dollar = dollarAccount()
        book(dollar, -1000, comment = "Foreign")
        book(euroId, -500, comment = "Home")
        useLayout(listOf(Notes), listOf(Amount))
        val pdf = print(homeAggregate)
        val foreign = pdf.piece("Foreign")
        assertThat(pdf.pieces.filter { it.y in foreign.y - 20..foreign.y + 20 }.map { it.text })
            .containsAtLeast("N10.00", "N20.00").inOrder()
        val home = pdf.piece("Home")
        assertThat(pdf.pieces.filter { it.y == home.y }.map { it.text })
            .containsExactly("Home", "N5.00")
    }

    @Test
    fun sumsOfTheHomeAggregateAreConverted() {
        book(dollarAccount(), -1000)
        book(euroId, -500)
        val texts = print(homeAggregate).texts
        assertThat(texts).contains("Expenses: - €25.00")
    }

    @Test
    fun homeAggregatePrintsAllAmountsOfATransaction() {
        book(
            dollarAccount(),
            -1000,
            originalAmount = 900,
            originalCurrency = "JPY",
            comment = "Three"
        )
        useLayout(listOf(OriginalAmount, Amount))
        assertThat(print(homeAggregate).texts)
            .containsAtLeast("¥900", "$10.00", "€20.00").inOrder()
    }

    @Test
    fun currencyAggregateLeavesOutOtherCurrenciesAndHasNoEquivalent() {
        book(dollarAccount(), -1000, comment = "Foreign")
        book(euroId, -500, comment = "Home")
        useLayout(listOf(Notes), listOf(Amount))
        val texts = print(euroAggregate).texts
        assertThat(texts).containsAtLeast("Home", "€5.00")
        assertThat(texts).doesNotContain("Foreign")
    }

    @Test
    fun accountFieldNamesTheAccountOfEachRowInAnAggregate() {
        book(euroId, -500, comment = "Row")
        useLayout(listOf(Account), listOf(Notes))
        assertThat(print(homeAggregate).texts).containsAtLeast("Euro", "Row").inOrder()
    }

    @Test
    fun partsOfASplitDoNotNameTheAccount() {
        insertSplit(euroId, partCategoryIds = listOf(writeCategory("First")))
        useLayout(listOf(Account), listOf(Notes))
        assertThat(print(homeAggregate).texts.count { it == "Euro" }).isEqualTo(1)
    }

    @Test
    fun transferInsideOfAnAggregateIsOneNeutralRowWithoutSum() {
        showNegativeSignInPdf()
        repository.insertTransfer(euroId, insertAccount("Giro"), -250, comment = "Move")
        val pdf = print(euroAggregate)
        assertThat(pdf.texts.count { it == "Move" }).isEqualTo(1)
        assertThat(pdf.colorOf("2.50")).isEqualTo(NORMAL_COLOR)
        assertThat(pdf.texts).contains("Transfers: 0.00")
    }

    @Test
    fun neutralRowHasNoSignWhicheverSideOfTheTransferIsPicked() {
        showNegativeSignInPdf()
        val giro = insertAccount("Giro")
        repository.insertTransfer(euroId, giro, -250)
        repository.insertTransfer(euroId, giro, 350)
        val texts = print(euroAggregate).texts
        assertThat(texts).containsAtLeast("2.50", "3.50")
        assertThat(texts).containsNoneOf("N2.50", "N3.50")
    }

    @Test
    fun transferBetweenCurrenciesInTheHomeAggregateShowsTheHomeSide() {
        val dollar = insertAccount("Dollar", currency = "USD")
        repository.insertTransfer(dollar, euroId, -1000, 500, comment = "Move")
        useLayout(listOf(Notes), listOf(Amount))
        val pdf = print(homeAggregate)
        assertThat(pdf.texts.count { it == "Move" }).isEqualTo(1)
        assertThat(pdf.texts).contains("€5.00")
        assertThat(pdf.texts).doesNotContain("$10.00")
    }

    @Test
    fun aggregateWithIdZeroPrintsTheSameRowsButNotTheAccount() {
        book(euroId, -500, comment = "Row")
        book(dollarAccount(), -1000, comment = "Foreign")
        useLayout(listOf(Account), listOf(Notes), listOf(Amount))
        val pdf = print(
            PageAccount(
                id = 0,
                label = "All",
                currencyUnit = currencyContext[HOME],
                accountGrouping = AccountGrouping.NONE
            )
        )
        assertThat(pdf.texts).containsAtLeast("Row", "Foreign", "€5.00", "$10.00", "€20.00")
        // Looks like a bug: the check for an aggregate in the printer is "id < 0", the rest of the
        // code uses "id <= 0". So the aggregates of MyExpensesV2 print no account for a row.
        assertThat(pdf.texts).containsNoneOf("Euro", "Dollar")
    }

    @Test
    fun aggregateWithIdZeroAndAGroupingOnlyPrintsThatGroup() {
        book(euroId, -500, comment = "Row")
        book(dollarAccount(), -1000, comment = "Foreign")
        useLayout(listOf(Notes))
        val pdf = print(
            PageAccount(
                id = 0,
                label = "EUR",
                currencyUnit = currencyContext[HOME],
                accountGrouping = AccountGrouping.CURRENCY
            )
        )
        assertThat(pdf.texts).contains("Row")
        assertThat(pdf.texts).doesNotContain("Foreign")
    }
}
