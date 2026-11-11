package org.totschnig.myexpenses.next.feature.portfolio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioTradesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val store = FakeTradeStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun tradesOfAPortfolioFollowTheStore() = runTest(dispatcher) {
        val vm = PortfolioTradesViewModel(store, dispatcher)
        val trades = vm.trades(1)
        backgroundScope.launch { trades.collect {} }
        assertEquals(listOf(5L), trades.value?.trades?.map { it.id })
        assertEquals("Depot", trades.value?.portfolio?.label)
        store.trades.value = emptyList()
        assertEquals(emptyList<Long>(), trades.value?.trades?.map { it.id })
        // One state per portfolio
        assertSame(trades, vm.trades(1))
    }

    @Test
    fun noPortfolioNoTrades() = runTest(dispatcher) {
        val vm = PortfolioTradesViewModel(store, dispatcher)
        val trades = vm.trades(2)
        backgroundScope.launch { trades.collect {} }
        assertNull(trades.value)
    }
}
