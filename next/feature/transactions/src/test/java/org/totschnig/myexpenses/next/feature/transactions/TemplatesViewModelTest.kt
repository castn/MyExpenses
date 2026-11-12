package org.totschnig.myexpenses.next.feature.transactions

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
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.data.transactions.TemplateApplied

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listsTheTemplatesWhileShown() = runTest(dispatcher) {
        val store = FakeTemplateStore(mapOf(1L to templateValues()))
        val viewModel = TemplatesViewModel(store, dispatcher)
        assertNull(viewModel.templates.value)
        val job = launch { viewModel.templates.collect {} }
        assertEquals(listOf("Miete"), viewModel.templates.value!!.map { it.title })
        viewModel.delete(viewModel.templates.value!!.single())
        assertEquals(emptyList<Any>(), viewModel.templates.value)
        job.cancel()
    }

    @Test
    fun savingAtOnceTellsTheResultOnce() = runTest(dispatcher) {
        val store =
            FakeTemplateStore(
                mapOf(1L to templateValues(), 2L to templateValues("Alt")),
                closed = setOf(2)
            )
        val viewModel = TemplatesViewModel(store, dispatcher)
        val job = launch { viewModel.templates.collect {} }
        val (rent, old) = viewModel.templates.value!!
        viewModel.apply(rent)
        assertEquals(TemplateMessage("Miete", TemplateApplied.Saved(201)), viewModel.message.value)
        viewModel.messageShown()
        assertNull(viewModel.message.value)
        viewModel.apply(old)
        assertEquals(TemplateApplied.AccountClosed, viewModel.message.value!!.result)
        job.cancel()
    }
}
