package org.totschnig.myexpenses.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins that the debt overview prints what the screen has loaded, not what is in the database.
 */
@RunWith(AndroidJUnit4::class)
class DebtOverviewPrintTest : BaseDebtTest() {

    @Before
    fun setUp() {
        resetFileProviderCache()
    }

    @Test
    fun withoutACollectorNoDebtIsPrinted() {
        saveDebt(amount = 1000, label = "Rent")
        viewModel.print()
        // Looks like a bug: print reads debts.value, which stays at its initial empty value unless
        // a screen is collecting, so the document depends on what the screen does.
        assertThat(printed(viewModel.pdfResult.awaitResult()).lines.last()).endsWith("0.00")
    }

    @Test
    fun withACollectorTheDebtsAreGroupedByPayee() {
        saveDebt(amount = 1000, label = "Rent")
        saveDebt(amount = 500, label = "Car")
        val collecting = viewModel.viewModelScope.launch { viewModel.debts.collect { } }
        awaitCondition { viewModel.debts.value.second.size == 2 }
        viewModel.print()
        val lines = printed(viewModel.pdfResult.awaitResult()).lines
        collecting.cancel()
        assertThat(lines.map { it.substringBefore(" |") })
            .containsAtLeast("Alice", "Rent", "Car", "Total")
    }
}
