package org.totschnig.myexpenses.next.feature.guidance

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import java.time.LocalDate
import org.totschnig.myexpenses.next.data.guidance.HintStore
import org.totschnig.myexpenses.next.data.guidance.HintTopic

@OptIn(ExperimentalCoroutinesApi::class)
class HintsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ledger(categoryId: Long?) = Ledger(
        listOf(LedgerTransaction(7, LocalDate.now(), -2500, accountId = 1, payeeName = "REWE", categoryId = categoryId)),
        OwnAccounts(setOf(1), emptyMap())
    )

    @Test
    fun waitsForTheLedgerWithTheChange() = withTestDataStore { dataStore ->
        // The ledger still shows the transaction as before the user gave it a category
        val source = FakeLedgerSource(ledger = ledger(categoryId = null))
        val viewModel = HintsViewModel(
            source, MutableStateFlow(ReceiptFacts(emptyMap())), MutableStateFlow(null), HintStore(dataStore)
        )
        val shown = CompletableDeferred<HintTopic>()
        viewModel.afterSave(7) { shown.complete(it) }
        source.ledger.value = ledger(categoryId = 1)
        assertEquals(HintTopic.RECEIPT, withTimeout(5000) { shown.await() })
    }
}
