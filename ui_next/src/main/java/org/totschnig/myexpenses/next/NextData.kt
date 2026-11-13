package org.totschnig.myexpenses.next

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.myApplication
import org.totschnig.myexpenses.next.categories.ProviderCategoryWriting
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.model.ledger.LedgerTransaction
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.data.balance.BalanceRepository
import org.totschnig.myexpenses.next.data.budgets.BudgetRepository
import org.totschnig.myexpenses.next.data.budgets.BudgetStore
import org.totschnig.myexpenses.next.data.categories.CategoryRepository
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.data.categories.categoryInput
import org.totschnig.myexpenses.next.data.contracts.ContractRepository
import org.totschnig.myexpenses.next.data.contracts.ContractStore
import org.totschnig.myexpenses.next.data.guidance.DataReadinessRepository
import org.totschnig.myexpenses.next.data.merchants.MerchantLogoConsent
import org.totschnig.myexpenses.next.data.merchants.MerchantLogoStore
import org.totschnig.myexpenses.next.data.merchants.PayeeDomainStore
import org.totschnig.myexpenses.next.data.merchants.keepLogosOnlyWithConsent
import org.totschnig.myexpenses.next.data.receipts.ReceiptRepository
import org.totschnig.myexpenses.next.data.setup.SetupContracts
import org.totschnig.myexpenses.next.data.setup.SetupRepository
import org.totschnig.myexpenses.next.data.setup.SetupStore
import org.totschnig.myexpenses.next.data.tags.TagRepository
import org.totschnig.myexpenses.next.data.tags.TagStore
import org.totschnig.myexpenses.next.data.tips.SavingTipsRepository
import org.totschnig.myexpenses.next.data.tips.TipStore
import org.totschnig.myexpenses.next.data.transactions.CategorySuggestions
import org.totschnig.myexpenses.next.insights.ProviderLedgerSource
import org.totschnig.myexpenses.next.insights.today
import org.totschnig.myexpenses.next.receipts.ProviderReceiptDatabase
import org.totschnig.myexpenses.next.tags.RepositoryTagDatabase
import org.totschnig.myexpenses.next.transactions.AnalysisCategorySuggestions
import org.totschnig.myexpenses.util.TransactionImports

/**
 * The data of the new UI, once per process: the ledger and the facts of the areas, so that they
 * outlive the activity and widgets or notifications can read them without a screen. Created by the
 * app when it starts, so that it hears about imports that run before a screen is shown; not yet
 * part of the app's dependency graph, until the new UI moves to Hilt. Its flows run in [scope]
 * while they are collected, see `shareWhileShown`.
 *
 * Each area has one repository here, in the order they build on each other, and a line in
 * [applyAutomaticDecisions] if it decides something by itself. An area that reads the facts of an
 * area that may be left out reads them through one value here, e.g. [receiptFacts], which then is
 * the empty value of those facts.
 */
class NextData private constructor(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dataStore = context.injector.preferencesDataStore()

    /** The currency all amounts of the analyses are in, which the user can change */
    val homeCurrency: StateFlow<CurrencyInfo> = homeCurrencyOf(context, scope)

    val ledgerSource: LedgerSource =
        ProviderLedgerSource(context.contentResolver, dataStore, scope, today(context))

    val contracts = ContractRepository(ledgerSource, ContractStore(dataStore), scope)

    val tags =
        TagRepository(
            ledgerSource,
            TagStore(dataStore),
            scope,
            RepositoryTagDatabase(context.injector.repository())
        )

    val categories = CategoryRepository(
        ledgerSource,
        CategoryStore(dataStore),
        ProviderCategoryWriting(context.contentResolver),
        scope
    )

    /** Categories for a transaction that is typed; [CategorySuggestions.None] without categories */
    val categorySuggestions: CategorySuggestions = AnalysisCategorySuggestions(categories)

    /**
     * Ids of the transactions the user knows no category for ("Weiß ich nicht"), see
     * [CategoryRepository.unclear]; `flowOf(emptySet())` without categories
     */
    val unclearTransactions: Flow<Set<Long>> = categories.unclear

    /** Whether the user knows no category for the counterpart of a transaction; `{ false }` without categories */
    val isCategoryUnclear: suspend (LedgerTransaction) -> Boolean = {
        categories.isUnclear(it.categoryInput())
    }

    val receipts = ReceiptRepository(
        ProviderReceiptDatabase(
            context.myApplication,
            context.injector.repository(),
            context.injector.currencyContext()
        ),
        scope
    )

    /** What budgets, saving tips and hints know about receipts; [ReceiptFacts.None] without receipts */
    val receiptFacts: Flow<ReceiptFacts> = receipts.facts

    val budgets = BudgetRepository(
        ledgerSource,
        contracts.facts,
        tags.facts,
        BudgetStore(dataStore),
        scope,
        receiptFacts
    )

    val balance = BalanceRepository(ledgerSource, contracts.facts, scope)

    val savingTips = SavingTipsRepository(
        ledgerSource,
        TipStore(dataStore),
        contracts.facts,
        tags.facts,
        receiptFacts,
        scope,
        budgets.facts
    )

    val readiness = DataReadinessRepository(ledgerSource, receiptFacts, scope, unclearTransactions)

    /**
     * What the setup knows about contracts, nothing while loading; `flowOf(SetupContracts.None)`
     * without contracts
     */
    val setupContracts: Flow<SetupContracts> = contracts.expenses.mapNotNull { state ->
        when (state) {
            AnalysisState.Loading -> null
            AnalysisState.Declined -> SetupContracts.None
            is AnalysisState.Ready -> SetupContracts.Available(state.value.suggestions.size)
        }
    }

    val setup =
        SetupRepository(ledgerSource, categories, SetupStore(dataStore), scope, setupContracts)

    /** The logos of payees, loaded only with consent, see [keepLogosOnlyWithConsent] */
    val merchantLogos = MerchantLogoStore(context.noBackupFilesDir.resolve("merchant-logos"))

    /**
     * Makes the automatic decisions of the areas while it runs: contracts, the rules of tags and
     * certain categories for new transactions. Called while the app is shown and after an import,
     * so that nothing is analysed in the background otherwise.
     */
    suspend fun applyAutomaticDecisions(): Nothing = coroutineScope {
        launch { contracts.analysis.collect() }
        launch { tags.appliedRules.collect() }
        launch { categories.appliedAutomatically.collect() }
        awaitCancellation()
    }

    init {
        // Logos are removed when the consent is withdrawn, whatever is shown at that moment
        scope.launch {
            keepLogosOnlyWithConsent(
                MerchantLogoConsent(dataStore),
                PayeeDomainStore(dataStore),
                merchantLogos
            )
        }
        // Imported transactions get their tags and categories right away, also when the app is not shown
        scope.launch {
            TransactionImports.imported.collectLatest {
                withTimeoutOrNull(AFTER_IMPORT_MILLIS) { applyAutomaticDecisions() }
            }
        }
    }

    companion object {
        /** Long enough to read the ledger and analyse it after an import */
        private const val AFTER_IMPORT_MILLIS = 60_000L

        @Volatile
        private var instance: NextData? = null

        fun of(context: Context): NextData = instance ?: synchronized(this) {
            instance ?: NextData(context.applicationContext).also { instance = it }
        }
    }
}
