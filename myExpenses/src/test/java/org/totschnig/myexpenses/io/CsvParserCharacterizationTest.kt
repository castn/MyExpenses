package org.totschnig.myexpenses.io

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import org.apache.commons.csv.CSVFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.export.CategoryInfo
import org.totschnig.myexpenses.export.qif.QifDateFormat
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.util.timeFormatter

/**
 * Pins what [CSVParser] does today, so that moving it and the Qif helpers it uses can be checked.
 * Comments starting with "Looks like a bug" mark behaviour that is pinned, not endorsed.
 */
@RunWith(AndroidJUnit4::class)
class CsvParserCharacterizationTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val dateAndAmount = intArrayOf(R.string.date, R.string.amount)

    private fun row(vararg cells: String) = cells.joinToString(",") { "\"$it\"" }

    private fun parse(
        content: String,
        columnToFieldMap: IntArray,
        dateFormat: QifDateFormat = QifDateFormat.EU
    ) = CSVParser(
        context,
        CSVFormat.DEFAULT.parse(content.reader()).records,
        columnToFieldMap,
        dateFormat,
        CurrencyUnit.DebugInstance,
        AccountType.BANK
    ).apply { parse() }

    private fun transactions(
        content: String,
        columnToFieldMap: IntArray,
        dateFormat: QifDateFormat = QifDateFormat.EU
    ) = parse(content, columnToFieldMap, dateFormat).accounts.single().transactions

    private fun single(
        content: String,
        columnToFieldMap: IntArray,
        dateFormat: QifDateFormat = QifDateFormat.EU
    ) = transactions(content, columnToFieldMap, dateFormat).single()

    private fun amountOf(raw: String) = single(row(raw), intArrayOf(R.string.amount)).amount

    private fun dateOf(raw: String, format: QifDateFormat) =
        single(row(raw), intArrayOf(R.string.date), format).date.toLocalDateTime()

    private fun Date.toLocalDateTime() =
        toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0
    ) = LocalDateTime.of(year, month, day, hour, minute, second)

    // column mapping

    @Test
    fun mapsEveryFieldByPositionInTheMapping() {
        val transaction = single(
            row(
                "tag1,tag2",
                "1234",
                "X",
                "Cheque",
                "Baker",
                "Food",
                "Notes",
                "Peter",
                "02.01.2023",
                "01.01.2023",
                "-1.50",
                "ignored"
            ),
            intArrayOf(
                R.string.tags,
                R.string.reference_number,
                R.string.status,
                R.string.method,
                R.string.subcategory,
                R.string.category,
                R.string.notes,
                R.string.payer_or_payee,
                R.string.value_date,
                R.string.booking_date,
                R.string.amount,
                R.string.discard
            )
        )
        with(transaction) {
            assertThat(tags).containsExactly("tag1", "tag2").inOrder()
            assertThat(number).isEqualTo("1234")
            assertThat(status).isEqualTo("X")
            assertThat(method).isEqualTo("Cheque")
            assertThat(category).isEqualTo("Food:Baker")
            assertThat(memo).isEqualTo("Notes")
            assertThat(payee).isEqualTo("Peter")
            assertThat(valueDate!!.toLocalDateTime()).isEqualTo(at(2023, 1, 2))
            assertThat(date.toLocalDateTime()).isEqualTo(at(2023, 1, 1))
            assertThat(amount).isEqualToIgnoringScale("-1.50")
        }
    }

    @Test
    fun trimsCellValues() {
        val transaction = single(
            row("  Peter  ", "  Food  ", " 1.50 "),
            intArrayOf(R.string.payer_or_payee, R.string.category, R.string.amount)
        )
        assertThat(transaction.payee).isEqualTo("Peter")
        assertThat(transaction.category).isEqualTo("Food")
        assertThat(transaction.amount).isEqualToIgnoringScale("1.50")
    }

    @Test
    fun unmappedColumnsAreNullAndEmptyMappedCellsAreEmptyStrings() {
        val unmapped = single(row("1"), intArrayOf(R.string.amount))
        with(unmapped) {
            assertThat(payee).isNull()
            assertThat(memo).isNull()
            assertThat(category).isNull()
            assertThat(toAccount).isNull()
            assertThat(status).isNull()
            assertThat(number).isNull()
            assertThat(method).isNull()
            assertThat(tags).isNull()
            assertThat(valueDate).isNull()
        }
        val empty = single(
            row("1", "", "", "", "", ""),
            intArrayOf(
                R.string.amount,
                R.string.payer_or_payee,
                R.string.notes,
                R.string.status,
                R.string.reference_number,
                R.string.method
            )
        )
        with(empty) {
            assertThat(payee).isNull()
            assertThat(memo).isEmpty()
            assertThat(status).isEmpty()
            assertThat(number).isEmpty()
            assertThat(method).isEmpty()
        }
    }

    @Test
    fun rowShorterThanMappingImportsAsEmptyRowInsteadOfBeingSkipped() {
        val result = transactions(
            content = row("01.01.2023", "5") + "\n" + row("02.01.2023"),
            columnToFieldMap = dateAndAmount + R.string.payer_or_payee
        )
        assertThat(result).hasSize(2)
        with(result[1]) {
            assertThat(date.toLocalDateTime()).isEqualTo(at(2023, 1, 2))
            assertThat(amount).isEqualToIgnoringScale("0")
            assertThat(payee).isNull()
        }
    }

    @Test
    fun headerRowLeftInTheDataBecomesATransaction() {
        // Rows are only ever removed by the selection in CsvImportDataFragment
        val result = transactions(
            content = row("Date", "Amount", "Category") + "\n" + row("01.01.2023", "5", "Food"),
            columnToFieldMap = dateAndAmount + R.string.category
        )
        assertThat(result).hasSize(2)
        with(result[0]) {
            assertThat(date.toLocalDateTime().toLocalDate()).isEqualTo(LocalDate.now())
            assertThat(amount).isEqualToIgnoringScale("0")
            assertThat(category).isEqualTo("Category")
        }
    }

    // dates

    @Test
    fun parsesDateByFormat() {
        listOf(
            Triple("12/31/2023", QifDateFormat.US, at(2023, 12, 31)),
            Triple("12.31.23", QifDateFormat.US, at(2023, 12, 31)),
            Triple("3/4/2023", QifDateFormat.US, at(2023, 3, 4)),
            Triple("31.12.2023", QifDateFormat.EU, at(2023, 12, 31)),
            Triple("31/12/23", QifDateFormat.EU, at(2023, 12, 31)),
            Triple("31-12-2023", QifDateFormat.EU, at(2023, 12, 31)),
            Triple("3/4/2023", QifDateFormat.EU, at(2023, 4, 3)),
            Triple("2023-12-31", QifDateFormat.YMD, at(2023, 12, 31)),
            Triple("23/12/31", QifDateFormat.YMD, at(2023, 12, 31)),
            Triple("20231231", QifDateFormat.YMD, at(2023, 12, 31)),
            Triple("6 Dec 2023", QifDateFormat.EU, at(2023, 12, 6)),
            Triple("Dec 6, 2023", QifDateFormat.US, at(2023, 12, 6)),
            Triple("6-Dec-2023", QifDateFormat.EU, at(2023, 12, 6)),
            Triple("31.12.2023 14:58", QifDateFormat.EU, at(2023, 12, 31, 14, 58)),
            Triple("2023-12-31T14:58:30", QifDateFormat.YMD, at(2023, 12, 31, 14, 58, 30))
        ).forEach { (raw, format, expected) ->
            assertWithMessage("$raw as $format").that(dateOf(raw, format)).isEqualTo(expected)
        }
    }

    @Test
    fun twoDigitYearsPivotAt2029() {
        assertThat(dateOf("01.02.28", QifDateFormat.EU)).isEqualTo(at(2028, 2, 1))
        assertThat(dateOf("01.02.29", QifDateFormat.EU)).isEqualTo(at(1929, 2, 1))
    }

    @Test
    fun eightDigitsAreReadAsYearMonthDayInEveryFormat() {
        assertThat(dateOf("20231231", QifDateFormat.EU)).isEqualTo(at(2023, 12, 31))
        assertThat(dateOf("20231231", QifDateFormat.US)).isEqualTo(at(2023, 12, 31))
        // Looks like a bug: 31122023 in EU format is not 31 Dec 2023, it rolls over to year 3113
        assertThat(dateOf("31122023", QifDateFormat.EU)).isEqualTo(at(3113, 8, 23))
    }

    @Test
    fun dateThatDoesNotFitTheFormatRollsOverSilently() {
        // Looks like a bug: month and day are not validated, the calendar is lenient
        assertThat(dateOf("25.12.2023", QifDateFormat.US)).isEqualTo(at(2025, 1, 12))
        assertThat(dateOf("12/25/2023", QifDateFormat.EU)).isEqualTo(at(2025, 1, 12))
        assertThat(dateOf("31.02.2023", QifDateFormat.EU)).isEqualTo(at(2023, 3, 3))
    }

    @Test
    fun unparseableDateFallsBackToNow() {
        // Looks like a bug: the row is imported with today's date and nothing tells the user
        listOf("not a date", "", "32").forEach {
            val date = single(row(it, "1"), dateAndAmount).date
            assertWithMessage(it).that(date.toLocalDateTime().toLocalDate())
                .isEqualTo(LocalDate.now())
        }
    }

    @Test
    fun withoutDateColumnDateIsNow() {
        val date = single(row("1"), intArrayOf(R.string.amount)).date
        assertThat(date.toLocalDateTime().toLocalDate()).isEqualTo(LocalDate.now())
    }

    @Test
    fun dateColumnWinsOverBookingDate() {
        val transaction = single(
            row("01.01.2023", "02.01.2023", "1"),
            intArrayOf(R.string.booking_date, R.string.date, R.string.amount)
        )
        assertThat(transaction.date.toLocalDateTime()).isEqualTo(at(2023, 1, 2))
    }

    @Test
    fun unparseableValueDateFallsBackToNow() {
        val transaction = single(
            row("nope", "1"),
            intArrayOf(R.string.value_date, R.string.amount)
        )
        assertThat(transaction.valueDate!!.toLocalDateTime().toLocalDate())
            .isEqualTo(LocalDate.now())
    }

    /** The time column is read with the format of the device, so the test asks for that format */
    private fun deviceTime(hour: Int, minute: Int) =
        LocalTime.of(hour, minute).format(context.timeFormatter)

    @Test
    fun timeColumnIsCombinedWithDate() {
        val transaction = single(
            row("31.12.2023", deviceTime(14, 58), "1"),
            intArrayOf(R.string.date, R.string.time, R.string.amount)
        )
        assertThat(transaction.date.toLocalDateTime()).isEqualTo(at(2023, 12, 31, 14, 58))
    }

    @Test
    fun timeThatDoesNotMatchTheDeviceFormatIsIgnored() {
        listOf("not a time", "25:99", "14:58:30", "").forEach {
            val transaction = single(
                row("31.12.2023", it, "1"),
                intArrayOf(R.string.date, R.string.time, R.string.amount)
            )
            assertWithMessage(it).that(transaction.date.toLocalDateTime())
                .isEqualTo(at(2023, 12, 31))
        }
    }

    @Test
    fun withTimeColumnDateIsParsedLessTolerant() {
        val map = intArrayOf(R.string.date, R.string.time, R.string.amount)
        val time = deviceTime(14, 58)
        // Looks like a bug: the same date is understood without a time column, but with one it
        // falls back to today, as only the first parsing attempt of QifUtils.parseDate is used
        assertThat(single(row("6 Dec 2023", time, "1"), map).date.toLocalDateTime().withNano(0))
            .isEqualTo(LocalDate.now().atTime(14, 58))
        assertThat(single(row("6-Dec-2023", time, "1"), map).date.toLocalDateTime())
            .isEqualTo(at(2023, 12, 6, 14, 58))
    }

    @Test
    fun timeColumnWithoutDateColumnUsesToday() {
        val transaction = single(
            row(deviceTime(14, 58), "1"),
            intArrayOf(R.string.time, R.string.amount)
        )
        assertThat(transaction.date.toLocalDateTime().withNano(0))
            .isEqualTo(LocalDate.now().atTime(14, 58))
    }

    // amounts

    @Test
    fun parsesAmountNotations() {
        listOf(
            "12.50" to "12.50",
            "12,50" to "12.50",
            "12" to "12",
            "-12.50" to "-12.50",
            "-12,50" to "-12.50",
            "+12.50" to "12.50",
            "1,234.56" to "1234.56",
            "1.234,56" to "1234.56",
            "-1.234,56" to "-1234.56",
            "1.234.567,89" to "1234567.89",
            "1 234,56" to "1234.56",
            "1 234,56" to "1234.56",
            "12,50 €" to "12.50",
            "€12,50" to "12.50",
            "1e3" to "1000",
            "1234567890123456" to "1234567890123456"
        ).forEach { (raw, expected) ->
            assertWithMessage(raw).that(amountOf(raw)).isEqualToIgnoringScale(expected)
        }
    }

    @Test
    fun amountThatCannotBeUnderstoodIsZeroAndTheRowIsKept() {
        listOf("", "abc", "-").forEach {
            assertWithMessage(it).that(amountOf(it)).isEqualToIgnoringScale("0")
        }
    }

    @Test
    fun amountNotationsReadWrongly() {
        // Looks like a bug in all of these, each one silently gives a different amount
        listOf(
            // a single separator is always decimal, the thousand is lost
            "1,000" to "1",
            "1.000" to "1",
            // a sign that is not the first character is lost
            "12.50-" to "12.50",
            "(12.50)" to "12.50",
            "$-5.00" to "5",
            // without a separator, text after the number gives zero
            "12 EUR" to "0",
            "12," to "0",
            // text in front of the number counts as the integer part
            "abc5" to "0.5",
            // beyond the maximum for the currency, CSVParser catches the exception
            "12345678901234567" to "0"
        ).forEach { (raw, expected) ->
            assertWithMessage(raw).that(amountOf(raw)).isEqualToIgnoringScale(expected)
        }
    }

    @Test
    fun incomeAndExpenseColumnsGiveTheSignedAmount() {
        val map = intArrayOf(R.string.income, R.string.expense)
        listOf(
            row("10.50", "") to "10.50",
            row("", "10,50") to "-10.50",
            row("10", "4") to "6",
            row("-10", "") to "10",
            row("", "-10") to "-10",
            row("-10", "-4") to "6",
            row("", "") to "0",
            row("abc", "4") to "-4"
        ).forEach { (content, expected) ->
            assertWithMessage(content).that(single(content, map).amount)
                .isEqualToIgnoringScale(expected)
        }
    }

    @Test
    fun onlyIncomeOrOnlyExpenseColumnMapped() {
        assertThat(single(row("-5"), intArrayOf(R.string.income)).amount)
            .isEqualToIgnoringScale("5")
        assertThat(single(row("5"), intArrayOf(R.string.expense)).amount)
            .isEqualToIgnoringScale("-5")
    }

    @Test
    fun oversizedValueInIncomeOrExpenseZeroesTheWholeRow() {
        val transaction = single(
            row("12345678901234567", "4"),
            intArrayOf(R.string.income, R.string.expense)
        )
        assertThat(transaction.amount).isEqualToIgnoringScale("0")
    }

    @Test
    fun amountColumnWinsOverIncomeAndExpense() {
        val transaction = single(
            row("7", "1", "2"),
            intArrayOf(R.string.amount, R.string.income, R.string.expense)
        )
        assertThat(transaction.amount).isEqualToIgnoringScale("7")
    }

    @Test
    fun withoutAnyAmountColumnAmountIsZero() {
        assertThat(single(row("Peter"), intArrayOf(R.string.payer_or_payee)).amount)
            .isEqualToIgnoringScale("0")
    }

    // categories and transfers

    private val categoryAndSub = intArrayOf(R.string.category, R.string.subcategory)

    @Test
    fun categoryAndSubcategoryAreJoinedToAPath() {
        val parser = parse(
            listOf(
                row("Food", "Baker"),
                row("Food", ""),
                row("Food:Baker", ""),
                row("Food:Baker", "Bread"),
                row("", "Orphan")
            ).joinToString("\n"),
            categoryAndSub
        )
        assertThat(parser.accounts.single().transactions.map { it.category })
            .containsExactly("Food:Baker", "Food", "Food:Baker", "Food:Baker:Bread", null)
            .inOrder()
        assertThat(parser.categories).containsExactly(
            CategoryInfo("Food:Baker"),
            CategoryInfo("Food"),
            CategoryInfo("Food:Baker:Bread")
        )
    }

    @Test
    fun categoryInSquareBracketsIsATransfer() {
        val transaction = single(row("[Savings]", "ignored"), categoryAndSub)
        assertThat(transaction.toAccount).isEqualTo("Savings")
        assertThat(transaction.category).isNull()
        assertThat(transaction.isTransfer).isTrue()
    }

    @Test
    fun localizedTransferWithSubcategoryInSquareBracketsIsATransfer() {
        val transaction = single(row("Transfer", "[Savings]"), categoryAndSub)
        assertThat(transaction.toAccount).isEqualTo("Savings")
        assertThat(transaction.category).isNull()
    }

    @Test
    fun otherBracketedOrTransferNamedValuesAreCategories() {
        assertThat(single(row("Transfer", "Savings"), categoryAndSub).category)
            .isEqualTo("Transfer:Savings")
        assertThat(single(row("Food", "[Savings]"), categoryAndSub).category)
            .isEqualTo("Food:[Savings]")
        assertThat(single(row("[Savings", ""), categoryAndSub).category)
            .isEqualTo("[Savings")
    }

    // payees and tags

    @Test
    fun collectsDistinctPayeesAndSkipsEmptyOnes() {
        val parser = parse(
            listOf(row("Peter"), row(""), row("Peter"), row("Paul")).joinToString("\n"),
            intArrayOf(R.string.payer_or_payee)
        )
        assertThat(parser.payees).containsExactly("Peter", "Paul")
        assertThat(parser.accounts.single().transactions.map { it.payee })
            .containsExactly("Peter", null, "Peter", "Paul").inOrder()
    }

    @Test
    fun tagCellIsSplitByCommaWithQuotes() {
        val parser = parse(
            "\"\"\"x,y\"\",z\"\n\"a,b\"\n\"\"",
            intArrayOf(R.string.tags)
        )
        assertThat(parser.accounts.single().transactions.map { it.tags })
            .containsExactly(listOf("x,y", "z"), listOf("a", "b"), null).inOrder()
        assertThat(parser.tags).containsExactly("x,y", "z", "a", "b")
    }

    @Test
    fun spaceAfterTheCommaBecomesPartOfTheTag() {
        // Looks like a bug: "a, b" gives the tag " b" with the leading space
        assertThat(single(row("a, b"), intArrayOf(R.string.tags)).tags)
            .containsExactly("a", " b").inOrder()
    }

    // splits

    private val splitDateAmountCategory =
        intArrayOf(R.string.split_transaction, R.string.date, R.string.amount, R.string.category)

    @Test
    fun splitPartsTakeTheDateOfTheParentAndParentIgnoresItsCategory() {
        val transaction = single(
            listOf(
                row("*", "01.01.2023", "3", "ParentCategory"),
                row("-", "15.06.2023", "1", "Food"),
                row("-", "", "2", "Sports")
            ).joinToString("\n"),
            splitDateAmountCategory
        )
        assertThat(transaction.category).isNull()
        assertThat(transaction.isSplit).isTrue()
        assertThat(transaction.splits!!.map { it.category })
            .containsExactly("Food", "Sports").inOrder()
        transaction.splits.forEach {
            assertThat(it.date.toLocalDateTime()).isEqualTo(at(2023, 1, 1))
        }
    }

    @Test
    fun splitParentWithoutPartsIsAPlainRowWithoutCategory() {
        val transaction = single(row("*", "01.01.2023", "3", "Food"), splitDateAmountCategory)
        assertThat(transaction.isSplit).isFalse()
        assertThat(transaction.category).isNull()
    }

    @Test
    fun splitPartWithoutParentIsDropped() {
        // Looks like a bug: the row disappears, and its amount with it
        val parser = parse(row("-", "01.01.2023", "3", "Food"), splitDateAmountCategory)
        assertThat(parser.accounts.single().transactions).isEmpty()
        // Its category was collected nevertheless
        assertThat(parser.categories).containsExactly(CategoryInfo("Food"))
    }

    @Test
    fun splitPartAfterAPlainRowJoinsTheLastParent() {
        // Looks like a bug: a plain row does not reset the parent, so the sum check at import fails
        val result = transactions(
            listOf(
                row("*", "01.01.2023", "3", ""),
                row("-", "01.01.2023", "3", "Food"),
                row("", "02.01.2023", "5", "Sports"),
                row("-", "03.01.2023", "7", "Late")
            ).joinToString("\n"),
            splitDateAmountCategory
        )
        assertThat(result).hasSize(2)
        assertThat(result[0].splits!!.map { it.category }).containsExactly("Food", "Late").inOrder()
        assertThat(result[1].splits).isNull()
    }

    // accounts

    @Test
    fun withoutAccountColumnAllRowsGoToOneUnnamedAccountWithoutType() {
        val parser = parse(row("1") + "\n" + row("2"), intArrayOf(R.string.amount))
        with(parser.accounts.single()) {
            assertThat(memo).isEmpty()
            assertThat(type).isNull()
            assertThat(transactions).hasSize(2)
        }
    }

    @Test
    fun accountColumnGroupsRowsByLabelInOrderOfFirstAppearance() {
        val parser = parse(
            listOf(row("B", "1"), row("A", "2"), row("B", "3"), row("", "4")).joinToString("\n"),
            intArrayOf(R.string.account, R.string.amount)
        )
        assertThat(parser.accounts.map { it.memo }).containsExactly("B", "A", "").inOrder()
        assertThat(parser.accounts.map { it.transactions.size }).containsExactly(2, 1, 1).inOrder()
        assertThat(parser.accounts.map { it.type }.distinct())
            .containsExactly(AccountType.BANK.name)
    }

    @Test
    fun splitPartsStayWithTheAccountOfTheirParent() {
        val parser = parse(
            listOf(
                row("A", "*", "3"),
                row("B", "-", "3")
            ).joinToString("\n"),
            intArrayOf(R.string.account, R.string.split_transaction, R.string.amount)
        )
        assertThat(parser.accounts.single().memo).isEqualTo("A")
        assertThat(parser.accounts.single().transactions.single().splits).hasSize(1)
    }

    @Test
    fun noDataGivesOneEmptyAccountOnlyWithoutAccountColumn() {
        assertThat(parse("", intArrayOf(R.string.amount)).accounts.single().transactions).isEmpty()
        assertThat(parse("", intArrayOf(R.string.account, R.string.amount)).accounts).isEmpty()
    }
}
