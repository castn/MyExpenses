package org.totschnig.myexpenses.viewmodel

import android.net.Uri
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.totschnig.myexpenses.db2.insertTransaction
import org.totschnig.myexpenses.db2.insertTransfer
import org.totschnig.myexpenses.export.pdf.INCOME_COLOR
import org.totschnig.myexpenses.export.pdf.PrintedPdf
import org.totschnig.myexpenses.export.pdf.TRANSFER_COLOR
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.preference.ColorSource
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.viewmodel.data.PageAccount

/**
 * FileProvider keeps the roots per authority in a static field, while the folders of Robolectric
 * change with each test.
 */
internal fun resetFileProviderCache() {
    FileProvider::class.java.getDeclaredField("sCache").apply {
        isAccessible = true
        (get(null) as MutableMap<*, *>).clear()
    }
}

internal fun awaitCondition(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5000
    while (!condition() && System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        if (!condition()) Thread.sleep(10)
    }
}

internal fun <T> StateFlow<T?>.awaitResult(): T {
    awaitCondition { value != null }
    return value!!
}

internal fun printed(result: Result<Pair<Uri, String>>) =
    result.getOrThrow().let { (uri, name) -> PrintedPdf.read(File(uri.path!!), uri, name) }

/**
 * Pins the way from the view model to the file: where it goes, how a failure is reported, and that
 * the data of the balance sheet depends on someone collecting the flows.
 */
@RunWith(AndroidJUnit4::class)
class PrintFlowTest : BaseViewModelTest() {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var viewModel: MyExpensesViewModel

    private val appDir by lazy { application.getExternalFilesDir(null)!! }

    private val accountId by lazy { insertAccount("Cash") }

    private val account by lazy {
        PageAccount(
            id = accountId,
            label = "Cash",
            currencyUnit = currencyContext[currencyContext.homeCurrencyString]
        )
    }

    @Before
    fun setUp() {
        viewModel = MyExpensesViewModel(application, SavedStateHandle())
            .also { application.injector.inject(it) }
        resetFileProviderCache()
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun printWritesIntoTheAppDirAndReportsSuccess() {
        repository.insertTransaction(accountId, -500)
        viewModel.print(account, null)
        val pdf = printed(viewModel.pdfResult.awaitResult())
        assertThat(pdf.file.parentFile).isEqualTo(appDir)
        assertThat(pdf.texts).contains("Cash")
    }

    @Test
    fun failureIsReportedInTheResult() {
        viewModel.print(account, null)
        val failure = viewModel.pdfResult.awaitResult().exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure).hasMessageThat().isEqualTo("No data")
    }

    @Test
    fun appDirOutsideOfTheFileProviderPathsFailsWithoutAFile() {
        val folder = temporaryFolder.newFolder("outside")
        prefHandler.putString(PrefKey.APP_DIR, folder.toURI().toString())
        repository.insertTransaction(accountId, -500)
        viewModel.print(account, null)
        assertThat(viewModel.pdfResult.awaitResult().exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(folder.list()).isEmpty()
    }

    @Test
    fun appDirThatIsAFileFallsBackToTheDefault() {
        val plainFile = temporaryFolder.newFile("plain")
        prefHandler.putString(PrefKey.APP_DIR, plainFile.toURI().toString())
        repository.insertTransaction(accountId, -500)
        viewModel.print(account, null)
        assertThat(printed(viewModel.pdfResult.awaitResult()).file.parentFile).isEqualTo(appDir)
    }

    @Test
    fun resultIsClearedWhenProcessed() {
        viewModel.print(account, null)
        viewModel.pdfResult.awaitResult()
        viewModel.pdfResultProcessed()
        assertThat(viewModel.pdfResult.value).isNull()
    }

    @Test
    fun colourSourceDefaultsToType() {
        repository.insertTransfer(accountId, insertAccount("Giro"), 300)
        viewModel.print(account, null)
        val pdf = printed(viewModel.pdfResult.awaitResult())
        assertThat(pdf.pieces.last { "3.00" in it.text }.color?.rgb).isEqualTo(TRANSFER_COLOR)
    }

    @Test
    fun colourSourceComesFromTheDataStore() = runBlocking<Unit> {
        dataStore.edit {
            it[prefHandler.getStringPreferencesKey(PrefKey.TRANSACTION_AMOUNT_COLOR_SOURCE)] =
                ColorSource.SIGN.name
        }
        repository.insertTransfer(accountId, insertAccount("Giro"), 300)
        viewModel.print(account, null)
        val pdf = printed(viewModel.pdfResult.awaitResult())
        assertThat(pdf.pieces.last { "3.00" in it.text }.color?.rgb).isEqualTo(INCOME_COLOR)
    }

    @Test
    fun balanceSheetWithoutACollectorPrintsNoAccounts() {
        repository.insertTransaction(accountId, 500)
        viewModel.printBalanceSheet()
        val pdf = printed(viewModel.pdfResult.awaitResult())
        // Looks like a bug: first() on the StateFlow returns its initial empty value unless a
        // screen is collecting at that moment, so the document depends on what the screen does.
        assertThat(pdf.texts).contains("Balance Sheet")
        assertThat(pdf.texts).doesNotContain("Cash")
    }

    @Test
    fun balanceSheetWithACollectorPrintsTheAccounts() {
        repository.insertTransaction(accountId, 500)
        val collecting = viewModel.viewModelScope.launch {
            viewModel.accountsForBalanceSheet.collect { }
        }
        awaitCondition { viewModel.accountsForBalanceSheet.value.second.isNotEmpty() }
        viewModel.printBalanceSheet()
        val pdf = printed(viewModel.pdfResult.awaitResult())
        collecting.cancel()
        assertThat(pdf.texts).contains("Cash")
    }
}
