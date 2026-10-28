package org.totschnig.myexpenses.next.feature.tips

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.data.tags.TagFacts
import org.totschnig.myexpenses.next.data.tips.SavingTipsRepository
import org.totschnig.myexpenses.next.data.tips.SmallPurchases
import org.totschnig.myexpenses.next.data.tips.TipStore

@OptIn(ExperimentalCoroutinesApi::class)
class SavingTipsViewModelTest {

    private val tip = SmallPurchases(count = 60, yearlyAmount = 30000, payees = emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun keepsWhatTheUserDecidesInTheStore() = withTestDataStore { dataStore ->
        runTest {
            val store = TipStore(dataStore)
            val repository = SavingTipsRepository(
                FakeLedgerSource(), store, flowOf(null), flowOf(TagFacts()), emptyFlow(), backgroundScope
            )
            val viewModel = SavingTipsViewModel(repository)
            viewModel.markTipsSeen(listOf(tip))
            viewModel.dismissTip(tip)
            // Written by the data store in its own scope
            val decisions = store.decisions.first { it.isDismissed(tip) }
            assertFalse(decisions.isNew(tip))
            viewModel.restoreTip(tip)
            store.decisions.first { !it.isDismissed(tip) }
        }
    }
}
