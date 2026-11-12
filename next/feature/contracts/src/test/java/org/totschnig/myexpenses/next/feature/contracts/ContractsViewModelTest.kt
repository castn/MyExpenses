package org.totschnig.myexpenses.next.feature.contracts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.next.core.common.withTestDataStore
import org.totschnig.myexpenses.next.core.model.FakeLedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.ledger.OwnAccounts
import org.totschnig.myexpenses.next.data.contracts.AreaChoice
import org.totschnig.myexpenses.next.data.contracts.BuiltInArea
import org.totschnig.myexpenses.next.data.contracts.Contract
import org.totschnig.myexpenses.next.data.contracts.ContractDirection
import org.totschnig.myexpenses.next.data.contracts.ContractInterval
import org.totschnig.myexpenses.next.data.contracts.ContractNews
import org.totschnig.myexpenses.next.data.contracts.ContractRepository
import org.totschnig.myexpenses.next.data.contracts.ContractRule
import org.totschnig.myexpenses.next.data.contracts.ContractSettings
import org.totschnig.myexpenses.next.data.contracts.ContractStore
import org.totschnig.myexpenses.next.data.contracts.ContractTransaction
import org.totschnig.myexpenses.next.data.contracts.ContractsOverview
import org.totschnig.myexpenses.next.data.contracts.CustomArea
import org.totschnig.myexpenses.next.data.contracts.PaymentContractInfo
import org.totschnig.myexpenses.next.data.contracts.SalaryChoice

@OptIn(ExperimentalCoroutinesApi::class)
class ContractsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val today: LocalDate = LocalDate.now()
    private val giro = 1L
    private val savings = 9L
    private var nextId = 1L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun monthly(
        count: Int,
        amount: Long,
        payeeId: Long?,
        payeeName: String?,
        daysAgo: Long,
        categoryPath: String? = null,
        targetAccountId: Long? = null
    ) = (0 until count).map {
        LedgerTransaction(
            id = nextId++,
            date = today.minusDays(daysAgo).minus(Period.ofMonths(count - 1 - it)),
            amount = amount,
            accountId = giro,
            payeeId = payeeId,
            payeeName = payeeName,
            categoryPath = categoryPath,
            targetAccountId = targetAccountId
        )
    }

    /** Regular enough to be confirmed without asking */
    private val rent =
        monthly(12, -80000, 1, "Vermieter", daysAgo = 3, categoryPath = "Wohnen:Miete")

    /** Too few payments to be confirmed without asking, a suggestion */
    private val gym =
        monthly(4, -3000, 2, "Fitnessstudio", daysAgo = 5, categoryPath = "Freizeit:Fitness")
    private val salary = monthly(4, 250000, 3, "Arbeitgeber", daysAgo = 1)
    private val sideJob = monthly(4, 50000, 4, "Nebenjob", daysAgo = 2)

    /** A standing order to the savings account, confirmed without asking */
    private val saving = monthly(12, -10000, null, null, daysAgo = 2, targetAccountId = savings)
    private val oneOff =
        LedgerTransaction(900, today.minusDays(10), -50000, accountId = giro, payeeId = 5)
    private val withoutPayee =
        LedgerTransaction(901, today.minusDays(11), -2000, accountId = giro, comment = "Bar")

    private val ledger = Ledger(
        rent + gym + salary + sideJob + saving + oneOff + withoutPayee,
        OwnAccounts(setOf(giro), mapOf(savings to "Sparkonto"))
    )

    private class Fixture(
        val viewModel: ContractsViewModel,
        val store: ContractStore,
        val dataStore: DataStore<Preferences>,
        val source: FakeLedgerSource
    )

    private fun withViewModel(
        source: FakeLedgerSource = FakeLedgerSource(ledger = ledger, today = today),
        block: suspend Fixture.() -> Unit
    ) = withTestDataStore { dataStore ->
        runTest(dispatcher) {
            val store = ContractStore(dataStore)
            val viewModel = ContractsViewModel(
                ContractRepository(source, store, backgroundScope),
                MutableStateFlow(CurrencyInfo.Preview)
            )
            // Like a shown screen, which keeps the analysis running
            backgroundScope.launch { viewModel.state.collect {} }
            backgroundScope.launch { viewModel.incomeState.collect {} }
            try {
                Fixture(viewModel, store, dataStore, source).block()
            } finally {
                // Its coroutines must not outlive the test dispatcher set as Main
                viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    private fun Flow<AnalysisState<ContractsOverview>>.ready() =
        mapNotNull { (it as? AnalysisState.Ready)?.value }

    private suspend fun Fixture.expenses(predicate: (ContractsOverview) -> Boolean = { true }) =
        viewModel.state.ready().first(predicate)

    private suspend fun Fixture.incomes(predicate: (ContractsOverview) -> Boolean = { true }) =
        viewModel.incomeState.ready().first(predicate)

    private suspend fun Fixture.rules(predicate: (List<ContractRule>) -> Boolean) =
        store.settings.first { predicate(it.rules) }.rules

    /** After the rent was confirmed without asking */
    private suspend fun Fixture.loaded() = expenses { overview ->
        overview.active.any { it.name == "Vermieter" } &&
            overview.suggestions.any { it.name == "Fitnessstudio" }
    }

    private fun List<Contract>.named(name: String) = single { it.displayName == name }

    private fun List<ContractRule>.withPayee(payeeId: Long) = single { payeeId in it.payeeIds }

    @Test
    fun showsConfidentContractsConfirmedAndOthersAsSuggestions() = withViewModel {
        val overview = loaded()
        assertEquals(listOf("Vermieter", "→ Sparkonto"), overview.active.map { it.name })
        assertTrue(overview.active.all { it.isConfirmed })
        assertEquals(listOf("Fitnessstudio"), overview.suggestions.map { it.name })
        val incomes = incomes { it.suggestions.size == 2 }
        assertEquals(listOf("Arbeitgeber", "Nebenjob"), incomes.suggestions.map { it.name })
        assertTrue(incomes.active.isEmpty())
        assertSame(source, viewModel.ledgerSource)
        assertEquals(CurrencyInfo.Preview, viewModel.homeCurrency.value)
    }

    @Test
    fun showsDeclinedConsent() = withViewModel(FakeLedgerSource(consent = false, ledger = null)) {
        assertEquals(AnalysisState.Declined, viewModel.state.first { it != AnalysisState.Loading })
        assertEquals(
            AnalysisState.Declined,
            viewModel.incomeState.first { it != AnalysisState.Loading }
        )
        assertEquals(PaymentContractInfo.Unavailable, viewModel.paymentInfo(1).first())
    }

    @Test
    fun actionsDoNothingBeforeAnalysis() = withViewModel(FakeLedgerSource(ledger = null)) {
        assertEquals(AnalysisState.Loading, viewModel.state.value)
        assertEquals(PaymentContractInfo.Loading, viewModel.paymentInfo(1).first())
        val contract = Contract(
            signature = "p1|MONTHLY",
            name = "Vermieter",
            interval = ContractInterval.MONTHLY,
            transactions = listOf(
                ContractTransaction(1, today, -80000, accountId = giro, payeeId = 1)
            ),
            nextExpectedDate = today.plusMonths(1),
            isActive = true
        )
        viewModel.confirm(contract)
        viewModel.dismiss(contract)
        viewModel.markAsContract(1, ContractInterval.MONTHLY)
        viewModel.setSalary(SalaryChoice.None)
        // Without analysis, the actions return without starting to edit the store
        assertEquals(ContractSettings(), store.settings.first())
    }

    @Test
    fun confirmsSuggestion() = withViewModel {
        viewModel.confirm(loaded().suggestions.named("Fitnessstudio"))
        val overview = expenses { it.suggestions.isEmpty() }
        assertTrue(overview.active.named("Fitnessstudio").isConfirmed)
        val rule = rules { it.size == 3 }.withPayee(2)
        assertEquals(ContractRule.Kind.CONTRACT, rule.kind)
        assertEquals(ContractInterval.MONTHLY, rule.interval)
    }

    @Test
    fun dismissesAndRestoresSuggestion() = withViewModel {
        viewModel.dismiss(loaded().suggestions.named("Fitnessstudio"))
        val dismissed = expenses { it.dismissed.isNotEmpty() }
        assertTrue(dismissed.suggestions.isEmpty())
        assertEquals(
            ContractRule.Kind.IGNORE,
            rules { it.size == 3 }.withPayee(2).kind
        )

        viewModel.restore(dismissed.dismissed.named("Fitnessstudio"))
        val restored = expenses { it.dismissed.isEmpty() }
        assertTrue(restored.active.any { it.displayName == "Fitnessstudio" })
        assertEquals(
            ContractRule.Kind.CONTRACT,
            rules { it.withPayee(2).kind == ContractRule.Kind.CONTRACT }.withPayee(2).kind
        )
    }

    @Test
    fun renamesTrimmedAndGoesBackToDetectedName() = withViewModel {
        viewModel.rename(loaded().active.named("Vermieter"), "  Wohnung ")
        val renamed = expenses { overview -> overview.active.any { it.displayName == "Wohnung" } }
        assertEquals("Wohnung", rules { it.withPayee(1).name != null }.withPayee(1).name)

        viewModel.rename(renamed.active.named("Wohnung"), "   ")
        expenses { overview -> overview.active.any { it.displayName == "Vermieter" } }
        assertNull(rules { it.withPayee(1).name == null }.withPayee(1).name)

        viewModel.rename(loaded().suggestions.named("Fitnessstudio"), "Fitnessstudio")
        // The detected name is no custom name, but the suggestion is confirmed
        assertNull(rules { it.size == 3 }.withPayee(2).name)
    }

    @Test
    fun storesSettingsOfRule() = withViewModel {
        val rent = loaded().active.named("Vermieter")
        viewModel.setIcon(rent, "home")
        viewModel.setInterval(rent, ContractInterval.QUARTERLY)
        viewModel.setAmountRange(rent, 70000L..90000L)
        viewModel.setReserve(rent, true)
        val rule = rules {
            it.withPayee(1).run {
                icon == "home" && interval == ContractInterval.QUARTERLY &&
                    amountRange == 70000L..90000L && reserve == true
            }
        }.withPayee(1)
        assertEquals("home", rule.icon)

        viewModel.setIcon(rent, null)
        viewModel.setAmountRange(rent, null)
        rules { it.withPayee(1).run { icon == null && amountRange == null } }
    }

    @Test
    fun setReserveConfirmsSuggestion() = withViewModel {
        viewModel.setReserve(loaded().suggestions.named("Fitnessstudio"), true)
        val overview = expenses { it.suggestions.isEmpty() }
        assertTrue(overview.active.named("Fitnessstudio").isReserve)
        assertEquals(true, rules { it.size == 3 }.withPayee(2).reserve)
    }

    @Test
    fun choosesArea() = withViewModel {
        val gym = loaded().suggestions.named("Fitnessstudio")
        assertEquals(BuiltInArea.FITNESS, gym.area)

        viewModel.setArea(gym, AreaChoice.Fixed(BuiltInArea.INSURANCE))
        val insured = expenses { it.suggestions.isEmpty() }.active.named("Fitnessstudio")
        assertEquals(BuiltInArea.INSURANCE, insured.area)
        assertEquals(BuiltInArea.INSURANCE.key, rules { it.size == 3 }.withPayee(2).areaKey)

        viewModel.setArea(insured, AreaChoice.Fixed(null))
        rules { it.withPayee(2).areaKey == ContractSettings.AREA_NONE }
        expenses { it.active.named("Fitnessstudio").area == null }

        viewModel.setArea(insured, AreaChoice.Automatic)
        rules { it.withPayee(2).areaKey == null }
        expenses { it.active.named("Fitnessstudio").area == BuiltInArea.FITNESS }
    }

    @Test
    fun cancelsAndRevokesCancellation() = withViewModel {
        val rent = loaded().active.named("Vermieter")
        viewModel.cancel(rent)
        val cancelled = expenses { it.cancelled.isNotEmpty() }.cancelled.named("Vermieter")
        assertTrue(expenses().active.none { it.name == "Vermieter" })
        assertEquals(today, cancelled.cancelledOn)
        val rule = rules { it.withPayee(1).cancelledOn != null }.withPayee(1)
        assertEquals(ContractRule.Snapshot("Vermieter", 80000, rent.lastDate), rule.snapshot)

        viewModel.revokeCancellation(cancelled)
        expenses { overview ->
            overview.cancelled.isEmpty() && overview.active.any { it.name == "Vermieter" }
        }
        val revoked = rules { it.withPayee(1).cancelledOn == null }.withPayee(1)
        assertNull(revoked.snapshot)
    }

    @Test
    fun cancelsOnTheDayOfTheLedgerSource() {
        val yearAgo = today.minusYears(1)
        val payments = (0 until 12).map {
            LedgerTransaction(
                id = 100L + it,
                date = yearAgo.minusDays(3).minus(Period.ofMonths(11 - it)),
                amount = -80000,
                accountId = giro,
                payeeId = 1,
                payeeName = "Vermieter"
            )
        }
        val source = FakeLedgerSource(
            ledger = Ledger(payments, OwnAccounts(setOf(giro), emptyMap())),
            today = yearAgo
        )
        withViewModel(source) {
            viewModel.cancel(expenses { it.active.isNotEmpty() }.active.named("Vermieter"))
            val rule = rules { it.singleOrNull()?.cancelledOn != null }.single()
            assertEquals(yearAgo, rule.cancelledOn)
        }
    }

    @Test
    fun mergesSuggestionIntoContractAndRemovesPayeeAgain() = withViewModel {
        val overview = loaded()
        viewModel.merge(
            overview.active.named("Vermieter"),
            overview.suggestions.named("Fitnessstudio")
        )
        val merged = rules { rules -> rules.any { it.payeeIds == setOf(1L, 2L) } }
        assertEquals(2, merged.size)
        val rent = expenses { it.suggestions.isEmpty() }.active.named("Vermieter")
        assertEquals(16, rent.transactions.size)

        viewModel.removePayee(rent, 2)
        rules { rules -> rules.any { it.payeeIds == setOf(1L) } }
        expenses { it.suggestions.any { contract -> contract.name == "Fitnessstudio" } }
    }

    @Test
    fun removesTargetAccount() = withViewModel {
        val reserve = loaded().active.named("→ Sparkonto")
        val ruleId = rules { rules -> rules.any { savings in it.targetAccountIds } }
            .single { savings in it.targetAccountIds }.id
        viewModel.removeTargetAccount(reserve, savings)
        rules { rules -> rules.any { it.id == ruleId && it.targetAccountIds.isEmpty() } }
    }

    @Test
    fun choosesSalaryAndConfirmsChosenSuggestion() = withViewModel {
        loaded()
        val employer = incomes { it.suggestions.size == 2 }.suggestions.named("Arbeitgeber")
        viewModel.setSalary(SalaryChoice.Fixed(setOf(employer.signature, "rule:unknown")))
        val settings = store.settings.first { it.salary is SalaryChoice.Fixed }
        val key = settings.rules.withPayee(3).key
        // The suggestion is referred to by its new rule, other signatures stay as they are
        assertEquals(SalaryChoice.Fixed(setOf(key, "rule:unknown")), settings.salary)
        val incomes = incomes { it.active.isNotEmpty() }
        assertEquals(listOf("Arbeitgeber"), incomes.salaries.map { it.name })
        assertEquals(listOf("Nebenjob"), incomes.suggestions.map { it.name })

        viewModel.setSalary(SalaryChoice.None)
        store.settings.first { it.salary == SalaryChoice.None }
        assertTrue(incomes { it.salaryChoice == SalaryChoice.None }.salaries.isEmpty())
    }

    @Test
    fun mergeTakesOverSalaryChoice() = withViewModel {
        loaded()
        val employer = incomes { it.suggestions.size == 2 }.suggestions.named("Arbeitgeber")
        viewModel.setSalary(SalaryChoice.Fixed(setOf(employer.signature)))
        val confirmed = incomes { it.active.isNotEmpty() }
        viewModel.merge(
            confirmed.suggestions.named("Nebenjob"),
            confirmed.active.named("Arbeitgeber")
        )
        val settings = store.settings.first { settings ->
            settings.rules.any { it.payeeIds == setOf(3L, 4L) }
        }
        val merged = settings.rules.single { it.payeeIds == setOf(3L, 4L) }
        assertEquals(SalaryChoice.Fixed(setOf(merged.key)), settings.salary)
        assertTrue(settings.rules.none { it.payeeIds == setOf(3L) })
    }

    @Test
    fun tellsWhatAnalysisKnowsAboutPayment() = withViewModel {
        loaded()
        assertTrue(viewModel.paymentInfo(rent.last().id).first() is PaymentContractInfo.Confirmed)
        assertTrue(viewModel.paymentInfo(gym.last().id).first() is PaymentContractInfo.Suggested)
        assertEquals(PaymentContractInfo.NoPayee, viewModel.paymentInfo(withoutPayee.id).first())
        assertEquals(PaymentContractInfo.Markable(false), viewModel.paymentInfo(oneOff.id).first())
        assertEquals(PaymentContractInfo.Unavailable, viewModel.paymentInfo(12345).first())
    }

    @Test
    fun marksPaymentAsContract() = withViewModel {
        loaded()
        viewModel.markAsContract(oneOff.id, ContractInterval.YEARLY)
        val rule = rules { rules -> rules.any { 5L in it.payeeIds } }.withPayee(5)
        assertEquals(ContractInterval.YEARLY, rule.interval)
        assertEquals(ContractRule.Kind.CONTRACT, rule.kind)
        val info = viewModel.paymentInfo(oneOff.id).first { it is PaymentContractInfo.Confirmed }
        val contract = (info as PaymentContractInfo.Confirmed).contract
        assertEquals(ContractInterval.YEARLY, contract.interval)
    }

    @Test
    fun marksNewsRead() = withViewModel(FakeLedgerSource(ledger = null)) {
        fun news(id: String) = ContractNews(
            id = id,
            type = ContractNews.Type.PRICE_CHANGE,
            ruleId = "r",
            transactionId = 1,
            date = today,
            createdAt = today,
            contractName = "Vermieter",
            isIncome = false,
            amount = 81000,
            previousAmount = 80000
        )
        store.edit { it.news = listOf(news("a"), news("b")) }
        assertEquals(listOf("a", "b"), viewModel.news.first { it.size == 2 }.map { it.id })

        viewModel.markNewsRead(setOf("a"))
        val read = viewModel.news.first { list -> list.any { it.isRead } }
        assertEquals(listOf(true, false), read.map { it.isRead })
    }

    @Test
    fun createsAndRenamesArea() = withViewModel(FakeLedgerSource(ledger = null)) {
        val area = viewModel.createArea("  Haustiere ")
        assertEquals("Haustiere", area.name)
        assertEquals(listOf(area), store.settings.first { it.customAreas.isNotEmpty() }.customAreas)

        viewModel.renameArea(area, " Tiere ")
        store.settings.first { it.customAreas == listOf(CustomArea(area.id, "Tiere")) }
    }

    @Test
    fun deletingAreaPutsContractsBackIntoSuggestedArea() =
        withViewModel(FakeLedgerSource(ledger = null)) {
            val pets = viewModel.createArea("Haustiere")
            val other = viewModel.createArea("Garten")
            store.settings.first { it.customAreas.size == 2 }
            fun rule(id: String, payeeId: Long, areaKey: String?) = ContractRule(
                id = id,
                kind = ContractRule.Kind.CONTRACT,
                direction = ContractDirection.EXPENSE,
                payeeIds = setOf(payeeId),
                interval = ContractInterval.MONTHLY,
                areaKey = areaKey
            )
            store.edit { it.rules = listOf(rule("a", 1, pets.key), rule("b", 2, other.key)) }
            // Categories chosen before there were rules
            dataStore.edit {
                it[stringPreferencesKey("next_contracts_areas")] =
                    """{"p1|MONTHLY":"${pets.key}","p2|MONTHLY":"${other.key}"}"""
            }

            viewModel.deleteArea(pets)
            val settings = store.settings.first { it.customAreas.size == 1 }
            assertEquals(listOf(other), settings.customAreas)
            assertEquals(listOf(null, other.key), settings.rules.map { it.areaKey })
            assertEquals(mapOf("p2|MONTHLY" to other.key), settings.areas)
        }
}
