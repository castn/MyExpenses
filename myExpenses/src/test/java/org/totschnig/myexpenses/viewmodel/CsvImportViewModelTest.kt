package org.totschnig.myexpenses.viewmodel

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.entities.Transaction
import org.totschnig.myexpenses.db2.findAccountType
import org.totschnig.myexpenses.db2.findAnyOpenByLabel
import org.totschnig.myexpenses.db2.findCategory
import org.totschnig.myexpenses.db2.findParty
import org.totschnig.myexpenses.db2.findPaymentMethod
import org.totschnig.myexpenses.db2.loadAccount
import org.totschnig.myexpenses.db2.loadTagsForTransaction
import org.totschnig.myexpenses.db2.loadTransaction
import org.totschnig.myexpenses.db2.loadTransactions
import org.totschnig.myexpenses.io.ImportDateFormat
import org.totschnig.myexpenses.model.ContribFeatureNotAvailableException
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.PREDEFINED_NAME_BANK
import org.totschnig.myexpenses.model.PreDefinedPaymentMethod
import org.totschnig.myexpenses.util.TransactionImports
import org.totschnig.myexpenses.viewmodel.CsvImportViewModel.Companion.KEY_HEADER_LINE_POSITION
import org.totschnig.myexpenses.viewmodel.CsvImportViewModel.Companion.KEY_MAPPING
import org.totschnig.myexpenses.viewmodel.CsvImportViewModel.Companion.KEY_SELECTED_ROWS

/**
 * Pins what the CSV import does today from the bytes of a file to the rows in the database, so that
 * moving the code can be checked. Mapping columns in the UI is not covered, so the tests pass the
 * mapping the way CsvImportDataFragment does. Comments starting with "Looks like a bug" mark
 * behaviour that is pinned, not endorsed.
 */
@RunWith(AndroidJUnit4::class)
class CsvImportViewModelTest : BaseViewModelTest() {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var viewModel: CsvImportViewModel

    private val bankType by lazy { repository.findAccountType(PREDEFINED_NAME_BANK)!! }

    @Before
    fun setUp() {
        viewModel = newViewModel()
    }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        CsvImportViewModel(application, handle).also { application.appComponent.inject(it) }

    private fun file(
        content: String,
        name: String = "bank-export.csv",
        charset: Charset = Charsets.UTF_8
    ): Uri = File(folder.root, name).apply { writeBytes(content.toByteArray(charset)) }
        .let { Uri.fromFile(it) }

    /**
     * parseFile switches to the main thread for emitting, so the looper must run while waiting.
     */
    private fun <T> LiveData<T>.await(): T {
        var value: T? = null
        val observer = Observer<T> { value = it }
        observeForever(observer)
        try {
            val deadline = System.currentTimeMillis() + 10_000
            while (value == null && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                if (value == null) Thread.sleep(10)
            }
        } finally {
            removeObserver(observer)
        }
        return checkNotNull(value) { "LiveData value was never set." }
    }

    private fun parsed(uri: Uri, delimiter: Char = ',', encoding: String = "UTF-8") =
        viewModel.parseFile(uri, delimiter, encoding).await().let {
            assertThat(it.exceptionOrNull()).isNull()
            viewModel.dataFlow.value.map { record -> record.toList() }
        }

    private fun import(
        content: String,
        columnToFieldMap: IntArray,
        accountId: Long = 0,
        currency: String = currencyContext.homeCurrencyString,
        dateFormat: ImportDateFormat = ImportDateFormat.EU,
        autoFill: Boolean = false
    ): Result<List<ImportResult>> {
        val uri = file(content)
        parsed(uri)
        return viewModel.importData(
            viewModel.dataFlow.value,
            columnToFieldMap,
            dateFormat,
            autoFill,
            AccountConfiguration(accountId, currency, bankType),
            uri
        ).await()
    }

    private fun importIntoNewAccount(
        content: String,
        columnToFieldMap: IntArray,
        autoFill: Boolean = false
    ): List<Transaction> {
        val accountId = insertAccount("Target")
        import(content, columnToFieldMap, accountId, autoFill = autoFill).getOrThrow()
        return rowsOf(accountId)
    }

    private fun rowsOf(accountId: Long) = runBlocking { repository.loadTransactions(accountId) }

    private fun Long.toLocalDateTime() = Instant.ofEpochSecond(this)
        .atZone(ZoneId.systemDefault()).toLocalDateTime()

    // reading the file

    @Test
    fun splitsColumnsByTheChosenDelimiter() {
        listOf(',', ';', '\t').forEach { delimiter ->
            val expected = listOf("a", "b,c;d".replace(delimiter.toString(), ""), "e")
            val content = expected.joinToString(delimiter.toString())
            assertThat(parsed(file(content), delimiter)).containsExactly(expected)
        }
    }

    @Test
    fun wrongDelimiterGivesOneColumn() {
        assertThat(parsed(file("a;b"), ',')).containsExactly(listOf("a;b"))
    }

    @Test
    fun readsFileInTheChosenEncoding() {
        val latin1 = file("Müller,Bäcker", charset = Charsets.ISO_8859_1)
        assertThat(parsed(latin1, encoding = "ISO-8859-1"))
            .containsExactly(listOf("Müller", "Bäcker"))
        assertThat(parsed(file("Müller"), encoding = "ISO-8859-1"))
            .containsExactly(listOf("MÃ¼ller"))
    }

    @Test
    fun byteOrderMarkStaysInTheFirstCell() {
        // Looks like a bug: Excel writes this for UTF-8, it is not removed
        assertThat(parsed(file("﻿a,b"))).containsExactly(listOf("﻿a", "b"))
    }

    @Test
    fun byteOrderMarkMakesTheFirstDateUnreadable() {
        // Looks like a bug: first row of a file with mark gets today's date, if date comes first
        val rows = importIntoNewAccount(
            "﻿01.02.2023,1\n01.02.2023,2",
            intArrayOf(R.string.date, R.string.amount)
        )
        val byAmount = rows.associateBy { it.amount }
        assertThat(byAmount[100]!!.date.toLocalDateTime().toLocalDate()).isEqualTo(LocalDate.now())
        assertThat(byAmount[200]!!.date.toLocalDateTime().toLocalDate())
            .isEqualTo(LocalDate.of(2023, 2, 1))
    }

    @Test
    fun handlesQuotingLineBreaksAndEmptyLines() {
        val content = "\"a,b\",\"c\"\"d\",\"e\nf\"\r\n\r\n\ng,  h ,\ni\n"
        assertThat(parsed(file(content))).containsExactly(
            listOf("a,b", "c\"d", "e\nf"),
            listOf("g", "  h ", ""),
            listOf("i")
        ).inOrder()
    }

    @Test
    fun emptyFileGivesNoRecords() {
        assertThat(parsed(file(""))).isEmpty()
    }

    @Test
    fun missingFileIsAFailure() {
        val result = viewModel
            .parseFile(Uri.fromFile(File(folder.root, "missing.csv")), ',', "UTF-8").await()
        assertThat(result.exceptionOrNull()).isInstanceOf(FileNotFoundException::class.java)
    }

    @Test
    fun unknownEncodingIsAFailure() {
        assertThat(viewModel.parseFile(file("a"), ',', "NO-SUCH-CHARSET").await().isFailure)
            .isTrue()
    }

    @Test
    fun brokenQuotingIsAFailure() {
        assertThat(viewModel.parseFile(file("\"a\"b,c"), ',', "UTF-8").await().isFailure).isTrue()
    }

    // selection and saved state

    @Test
    fun parsingSelectsAllRowsAndResetsHeaderAndMapping() {
        viewModel.headerLine = 1
        viewModel.mapping = intArrayOf(1, 2)
        parsed(file("a\nb"))
        assertThat(viewModel.selectedRows.toList()).containsExactly(true, true)
        assertThat(viewModel.headerLine).isEqualTo(-1)
        assertThat(viewModel.mapping).isNull()
        viewModel.unselectRow(0)
        assertThat(viewModel.isSelected(0)).isFalse()
        assertThat(viewModel.isSelected(1)).isTrue()
        viewModel.selectRow(0)
        assertThat(viewModel.isSelected(0)).isTrue()
    }

    @Test
    fun restoresFileAndSelectionAfterProcessDeath() {
        val restored = newViewModel(
            SavedStateHandle(
                mapOf(
                    "uri" to file("a;1\nb;2"),
                    "delimiter" to ';',
                    "encoding" to "UTF-8",
                    KEY_SELECTED_ROWS to Bundle().apply {
                        putBooleanArray("selection", booleanArrayOf(false, true))
                    },
                    KEY_HEADER_LINE_POSITION to 0,
                    KEY_MAPPING to intArrayOf(3, 4)
                )
            )
        )
        assertThat(restored.dataFlow.value.map { it.toList() })
            .containsExactly(listOf("a", "1"), listOf("b", "2")).inOrder()
        assertThat(restored.selectedRows.toList()).containsExactly(false, true).inOrder()
        assertThat(restored.headerLine).isEqualTo(0)
        assertThat(restored.mapping!!.toList()).containsExactly(3, 4).inOrder()
    }

    @Test
    fun rememberedSettingsAreKeptInSavedState() {
        val uri = file("a")
        parsed(uri, ',', "UTF-8")
        viewModel.withAccountColumn = true
        with(viewModel.savedStateHandle) {
            assertThat(get<Uri>("uri")).isEqualTo(uri)
            assertThat(get<Char>("delimiter")).isEqualTo(',')
            assertThat(get<String>("encoding")).isEqualTo("UTF-8")
            assertThat(get<Boolean>(CsvImportViewModel.KEY_WITH_ACCOUNT_COLUMN)).isTrue()
        }
    }

    // rows in the database

    @Test
    fun writesAllFieldsOfARow() {
        val cheque = application.getString(R.string.pm_cheque)
        val row = importIntoNewAccount(
            "\"31.12.2023\",\" -12,50 \",\"Peter\",\"Note\",\"Food\",\"Baker\",\"X\",\"42\"," +
                "\"$cheque\",\"t1,t2\"",
            intArrayOf(
                R.string.date,
                R.string.amount,
                R.string.payer_or_payee,
                R.string.notes,
                R.string.category,
                R.string.subcategory,
                R.string.status,
                R.string.reference_number,
                R.string.method,
                R.string.tags
            )
        ).single()
        assertThat(row.date.toLocalDateTime()).isEqualTo(LocalDateTime.of(2023, 12, 31, 0, 0))
        assertThat(row.amount).isEqualTo(-1250)
        assertThat(row.payeeId).isEqualTo(repository.findParty("Peter"))
        assertThat(row.comment).isEqualTo("Note")
        val parent = repository.findCategory("Food")
        assertThat(parent).isNotEqualTo(-1L)
        assertThat(row.categoryId).isEqualTo(repository.findCategory("Baker", parent))
        assertThat(row.crStatus).isEqualTo(CrStatus.RECONCILED)
        assertThat(row.referenceNumber).isEqualTo("42")
        assertThat(row.methodId)
            .isEqualTo(repository.findPaymentMethod(PreDefinedPaymentMethod.CHEQUE.name))
        assertThat(row.methodId).isNotNull()
        assertThat(repository.loadTagsForTransaction(row.id).map { it.label })
            .containsExactly("t1", "t2")
    }

    @Test
    fun emptyOptionalCellsAreStoredLikeThis() {
        val row = importIntoNewAccount(
            ",,,,,,",
            intArrayOf(
                R.string.amount,
                R.string.payer_or_payee,
                R.string.notes,
                R.string.category,
                R.string.status,
                R.string.reference_number,
                R.string.method
            )
        ).single()
        assertThat(row.amount).isEqualTo(0)
        assertThat(row.payeeId).isNull()
        // Empty comment and reference number read back as null
        assertThat(row.comment).isNull()
        assertThat(row.categoryId).isNull()
        assertThat(row.crStatus).isEqualTo(CrStatus.UNRECONCILED)
        assertThat(row.referenceNumber).isNull()
        assertThat(row.methodId).isNull()
    }

    @Test
    fun onlySelectedRowsAreImportedTheWayTheFragmentPassesThem() {
        val accountId = insertAccount("Target")
        val uri = file("Date,Amount\n01.02.2023,1\n01.02.2023,2")
        parsed(uri)
        viewModel.unselectRow(0)
        val selected = viewModel.dataFlow.value
            .filterIndexed { index, _ -> viewModel.isSelected(index) }
        val result = viewModel.importData(
            selected,
            intArrayOf(R.string.date, R.string.amount),
            ImportDateFormat.EU,
            false,
            AccountConfiguration(accountId, currencyContext.homeCurrencyString, bankType),
            uri
        ).await().getOrThrow()
        assertThat(result).containsExactly(ImportResult("Target", 2))
        assertThat(rowsOf(accountId).map { it.amount }).containsExactly(100L, 200L)
    }

    @Test
    fun invalidRowsAreImportedWithZeroAmountAndToday() {
        // Looks like a bug: there is no row validation, garbage gives a row of zero today
        val rows = importIntoNewAccount(
            "garbage,garbage\n,\nonly one column",
            intArrayOf(R.string.date, R.string.amount)
        )
        assertThat(rows).hasSize(3)
        rows.forEach {
            assertThat(it.amount).isEqualTo(0)
            assertThat(it.date.toLocalDateTime().toLocalDate()).isEqualTo(LocalDate.now())
        }
    }

    @Test
    fun importingTwiceDuplicatesRows() {
        val accountId = insertAccount("Target")
        val map = intArrayOf(R.string.date, R.string.amount)
        import("01.02.2023,1", map, accountId).getOrThrow()
        import("01.02.2023,1", map, accountId).getOrThrow()
        assertThat(rowsOf(accountId)).hasSize(2)
    }

    @Test
    fun incomeAndExpenseColumnsAreWrittenSigned() {
        val rows = importIntoNewAccount(
            "\"10,50\",\n,\"4,25\"",
            intArrayOf(R.string.income, R.string.expense)
        )
        assertThat(rows.map { it.amount }).containsExactly(1050L, -425L)
    }

    @Test
    fun amountIsRoundedHalfEvenToTheFractionDigitsOfTheCurrency() {
        val eurRows = importIntoNewAccount(
            "1.005\n1.015\n1.0151",
            intArrayOf(R.string.amount)
        )
        assertThat(eurRows.map { it.amount }).containsExactly(100L, 102L, 102L)
        val yenAccount = insertAccount("Yen", currency = "JPY")
        import("1000.5\n1001.5", intArrayOf(R.string.amount), yenAccount, "JPY").getOrThrow()
        assertThat(rowsOf(yenAccount).map { it.amount }).containsExactly(1000L, 1002L)
    }

    @Test
    fun statusIsMappedFromQifLetters() {
        val rows = importIntoNewAccount(
            listOf("1,X", "2,x", "3,R", "4,C", "5,*", "6,V", "7,bogus").joinToString("\n"),
            intArrayOf(R.string.amount, R.string.status)
        )
        assertThat(rows.sortedBy { it.amount }.map { it.crStatus }).containsExactly(
            CrStatus.RECONCILED,
            CrStatus.RECONCILED,
            CrStatus.RECONCILED,
            CrStatus.CLEARED,
            CrStatus.CLEARED,
            CrStatus.VOID,
            CrStatus.UNRECONCILED
        ).inOrder()
    }

    @Test
    fun unknownMethodIsDropped() {
        val row = importIntoNewAccount(
            "1,Bitcoin",
            intArrayOf(R.string.amount, R.string.method)
        ).single()
        assertThat(row.methodId).isNull()
    }

    @Test
    fun valueDateIsNotWritten() {
        // Looks like a bug: CSVParser reads the column, ImportDataViewModel does not pass it on
        val row = importIntoNewAccount(
            "01.02.2023,03.04.2023,1",
            intArrayOf(R.string.booking_date, R.string.value_date, R.string.amount)
        ).single()
        assertThat(row.date.toLocalDateTime().toLocalDate()).isEqualTo(LocalDate.of(2023, 2, 1))
        assertThat(row.valueDate).isEqualTo(row.date)
    }

    @Test
    fun datesFollowTheChosenFormat() {
        val accountId = insertAccount("Target")
        import(
            "03/04/2023,1",
            intArrayOf(R.string.date, R.string.amount),
            accountId,
            dateFormat = ImportDateFormat.US
        ).getOrThrow()
        assertThat(rowsOf(accountId).single().date.toLocalDateTime().toLocalDate())
            .isEqualTo(LocalDate.of(2023, 3, 4))
    }

    // categories and payees

    @Test
    fun buildsCategoryHierarchyAndReusesExistingCategories() {
        val existingParent = writeCategory("Food")
        val existingChild = writeCategory("Baker", existingParent)
        val rows = importIntoNewAccount(
            listOf(
                "1,Food,Baker",
                "2,Food,Butcher",
                "3,Food:Butcher:Beef,",
                "4,Fun,",
                "5,Fun:Games,Chess",
                "6,Caf\\u00e9,"
            ).joinToString("\n"),
            intArrayOf(R.string.amount, R.string.category, R.string.subcategory)
        ).sortedBy { it.amount }
        val butcher = repository.findCategory("Butcher", existingParent)
        val fun1 = repository.findCategory("Fun")
        val games = repository.findCategory("Games", fun1)
        assertThat(rows.map { it.categoryId }).containsExactly(
            existingChild,
            butcher,
            repository.findCategory("Beef", butcher),
            fun1,
            repository.findCategory("Chess", games),
            repository.findCategory("Café")
        ).inOrder()
        assertThat(rows.map { it.categoryId }.none { it == -1L }).isTrue()
    }

    @Test
    fun reusesExistingPayee() {
        val map = intArrayOf(R.string.amount, R.string.payer_or_payee)
        val first = importIntoNewAccount("1,Peter", map)
        val partyId = repository.findParty("Peter")
        assertThat(first.single().payeeId).isEqualTo(partyId)
        val otherAccount = insertAccount("Other")
        import("2,Peter", map, otherAccount).getOrThrow()
        assertThat(rowsOf(otherAccount).single().payeeId).isEqualTo(partyId)
    }

    @Test
    fun autoFillTakesTheCategoryOfThePayeeOnlyWhenTheRowHasNone() {
        val map = intArrayOf(R.string.amount, R.string.payer_or_payee, R.string.category)
        val seed = importIntoNewAccount("1,Peter,Food", map).single()
        val account = insertAccount("Filled")
        import("2,Peter,\n3,Peter,Sports\n4,Nobody,", map, account, autoFill = true).getOrThrow()
        val rows = rowsOf(account).sortedBy { it.amount }
        assertThat(rows[0].categoryId).isEqualTo(seed.categoryId)
        assertThat(rows[1].categoryId).isEqualTo(repository.findCategory("Sports"))
        assertThat(rows[2].categoryId).isNull()
        val plain = insertAccount("Plain")
        import("5,Peter,", map, plain, autoFill = false).getOrThrow()
        assertThat(rowsOf(plain).single().categoryId).isNull()
    }

    // accounts

    @Test
    fun withoutChosenAccountCreatesOneNamedAfterTheImport() {
        val result = import("1", intArrayOf(R.string.amount)).getOrThrow()
        val label = application.getString(R.string.pref_import_title, "CSV")
        assertThat(result).containsExactly(ImportResult(label, 1))
        val account = repository.loadAccount(repository.findAnyOpenByLabel(label)!!)!!
        assertThat(account.currency).isEqualTo(currencyContext.homeCurrencyString)
        assertThat(account.type!!.name).isEqualTo(PREDEFINED_NAME_BANK)
        assertThat(account.openingBalance).isEqualTo(0)
        assertThat(rowsOf(account.id).single().amount).isEqualTo(100)
    }

    @Test
    fun accountColumnCreatesMissingAccountsAndReusesExistingOnes() {
        val existing = insertAccount("Existing")
        val result = import(
            "Existing,1\nFresh,2\nExisting,3",
            intArrayOf(R.string.account, R.string.amount)
        ).getOrThrow()
        assertThat(result).containsExactly(ImportResult("Existing", 2), ImportResult("Fresh", 1))
        assertThat(rowsOf(existing).map { it.amount }).containsExactly(100L, 300L)
        val fresh = repository.loadAccount(repository.findAnyOpenByLabel("Fresh")!!)!!
        assertThat(fresh.type!!.name).isEqualTo(PREDEFINED_NAME_BANK)
        assertThat(fresh.currency).isEqualTo(currencyContext.homeCurrencyString)
        assertThat(rowsOf(fresh.id).single().amount).isEqualTo(200)
    }

    @Test
    fun accountColumnTakesPrecedenceOverChosenAccount() {
        val chosen = insertAccount("Chosen")
        import("Other,1", intArrayOf(R.string.account, R.string.amount), chosen).getOrThrow()
        assertThat(rowsOf(chosen)).isEmpty()
        assertThat(repository.findAnyOpenByLabel("Other")).isNotNull()
    }

    @Test
    fun emptyAccountCellIsNamedAfterTheFile() {
        // Looks like a bug: unlike .qif, the extension .csv stays in the name of the account
        import(",1", intArrayOf(R.string.account, R.string.amount)).getOrThrow()
        assertThat(repository.findAnyOpenByLabel("bank export.csv")).isNotNull()
    }

    @Test
    fun tooManyNewAccountsWithoutLicenceAreAFailure() {
        val result = import(
            (1..6).joinToString("\n") { "Account$it,1" },
            intArrayOf(R.string.account, R.string.amount)
        )
        assertThat(result.exceptionOrNull())
            .isInstanceOf(ContribFeatureNotAvailableException::class.java)
        // Accounts up to the limit have been written at that point
        assertThat(repository.findAnyOpenByLabel("Account5")).isNotNull()
        assertThat(repository.findAnyOpenByLabel("Account6")).isNull()
        assertThat(rowsOf(repository.findAnyOpenByLabel("Account1")!!)).isEmpty()
    }

    @Test
    fun noDataWithChosenAccountReportsZeroRows() {
        val accountId = insertAccount("Target")
        assertThat(import("", intArrayOf(R.string.amount), accountId).getOrThrow())
            .containsExactly(ImportResult("Target", 0))
    }

    @Test
    fun tellsOthersOnSuccess() {
        var notified = false
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            TransactionImports.imported.first()
            notified = true
        }
        importIntoNewAccount("1", intArrayOf(R.string.amount))
        job.cancel()
        assertThat(notified).isTrue()
    }

    // transfers

    private val accountDateAmountCategory =
        intArrayOf(R.string.account, R.string.date, R.string.amount, R.string.category)

    @Test
    fun bothSidesOfATransferBecomeOnePair() {
        val result = import(
            "A,01.02.2023,-5,[B]\nB,01.02.2023,5,[A]",
            accountDateAmountCategory
        ).getOrThrow()
        // Expense side is dropped before writing, the count only has the kept side
        assertThat(result).containsExactly(ImportResult("A", 0), ImportResult("B", 1))
        val a = repository.findAnyOpenByLabel("A")!!
        val b = repository.findAnyOpenByLabel("B")!!
        val rowA = rowsOf(a).single()
        val rowB = rowsOf(b).single()
        assertThat(rowB.amount).isEqualTo(500)
        assertThat(rowB.transferAccountId).isEqualTo(a)
        assertThat(rowB.transferPeerId).isEqualTo(rowA.id)
        assertThat(rowA.amount).isEqualTo(-500)
        assertThat(rowA.transferAccountId).isEqualTo(b)
        assertThat(rowA.transferPeerId).isEqualTo(rowB.id)
    }

    @Test
    fun transferSidesOnDifferentDaysAreNotMatched() {
        import("A,01.02.2023,-5,[B]\nB,02.02.2023,5,[A]", accountDateAmountCategory).getOrThrow()
        val rowA = rowsOf(repository.findAnyOpenByLabel("A")!!).single()
        val rowB = rowsOf(repository.findAnyOpenByLabel("B")!!).single()
        assertThat(rowA.transferAccountId).isNull()
        assertThat(rowB.transferAccountId).isNull()
    }

    @Test
    fun transferToAccountNotInTheFileBecomesARegularRow() {
        // Looks like a bug: with the account already in the database this could be a transfer
        insertAccount("Savings")
        val row = importIntoNewAccount(
            "5,[Savings],Note",
            intArrayOf(R.string.amount, R.string.category, R.string.notes)
        ).single()
        assertThat(row.transferAccountId).isNull()
        assertThat(row.categoryId).isNull()
        assertThat(row.amount).isEqualTo(500)
    }

    @Test
    fun commentOfUnmatchedTransferIsMangled() {
        // Looks like a bug: ImportUtils.prependMemo has its two cases swapped, so an existing comment
        // is lost, whereas an empty one is kept behind a separator
        val map = intArrayOf(R.string.amount, R.string.category, R.string.notes)
        assertThat(importIntoNewAccount("5,[Savings],Note", map).single().comment)
            .isEqualTo("Transfer: Savings")
        val empty = insertAccount("Empty")
        import("5,[Savings],", map, empty).getOrThrow()
        assertThat(rowsOf(empty).single().comment).isEqualTo("Transfer: Savings | ")
        val none = insertAccount("None")
        import("5,[Savings]", intArrayOf(R.string.amount, R.string.category), none).getOrThrow()
        assertThat(rowsOf(none).single().comment).isEqualTo("Transfer: Savings | null")
    }

    // splits

    private val splitAmountCategory =
        intArrayOf(R.string.split_transaction, R.string.amount, R.string.category)

    @Test
    fun writesSplitWithItsParts() {
        val parent = importIntoNewAccount(
            "*,-3,\n-,-1,Food\n-,-2,Sports\n,7,Fun",
            splitAmountCategory
        ).sortedBy { it.amount }.first()
        assertThat(parent.isSplit).isTrue()
        assertThat(parent.amount).isEqualTo(-300)
        val parts = repository.loadTransaction(parent.id).splitParts!!
        assertThat(parts.map { it.data.amount to it.data.categoryId }).containsExactly(
            -100L to repository.findCategory("Food"),
            -200L to repository.findCategory("Sports")
        )
    }

    @Test
    fun splitWhoseSumDiffersFailsTheImportAndKeepsWhatWasWritten() {
        // Looks like a bug: there is no rollback, rows before the bad split stay in the database
        val accountId = insertAccount("Target")
        val result = import(
            ",1,\n*,-3,\n-,-1,Food\n,7,",
            splitAmountCategory,
            accountId
        )
        assertThat(result.exceptionOrNull()).isInstanceOf(InvalidDataException::class.java)
        assertThat(rowsOf(accountId).map { it.amount }).containsExactly(100L)
    }
}
