package org.totschnig.myexpenses.next

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.totschnig.myexpenses.util.TransactionImports
import org.totschnig.myexpenses.next.categories.ProviderCategoryWriting
import org.totschnig.myexpenses.next.data.categories.CategoryRepository
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.tags.RepositoryTagDatabase
import org.totschnig.myexpenses.next.data.tags.TagRepository
import org.totschnig.myexpenses.next.data.tags.TagStore
import kotlinx.coroutines.flow.StateFlow
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.next.data.balance.BalanceRepository
import org.totschnig.myexpenses.next.data.contracts.ContractRepository
import org.totschnig.myexpenses.next.data.contracts.ContractStore
import org.totschnig.myexpenses.next.insights.ProviderLedgerSource
import org.totschnig.myexpenses.next.insights.today
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.data.budgets.BudgetRepository
import org.totschnig.myexpenses.next.data.budgets.BudgetStore
import org.totschnig.myexpenses.next.data.tips.SavingTipsRepository
import org.totschnig.myexpenses.next.data.tips.TipStore

/**
 * The data of the new UI, once per process: the ledger and the facts of the areas, so that they
 * outlive the activity and widgets or notifications can read them without a screen. Created on
 * first use, since the dynamic feature cannot add its types to the app's dependency graph. Its
 * flows run in [scope] while they are collected, see `shareWhileShown`.
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

    val ledgerSource: LedgerSource = ProviderLedgerSource(context.contentResolver, dataStore, scope, today(context))

    val contracts = ContractRepository(ledgerSource, ContractStore(dataStore), scope)

    val tags = TagRepository(ledgerSource, TagStore(dataStore), scope, RepositoryTagDatabase(context.injector.repository()))

    val categories = CategoryRepository(ledgerSource, CategoryStore(dataStore), ProviderCategoryWriting(context.contentResolver), scope)

    /** What budgets, saving tips and hints know about receipts; [ReceiptFacts.None] without receipts */
    val receiptFacts: Flow<ReceiptFacts> = flowOf(ReceiptFacts.None)

    val budgets = BudgetRepository(
        ledgerSource, contracts.facts, tags.facts, BudgetStore(dataStore), scope, receiptFacts
    )

    val balance = BalanceRepository(ledgerSource, contracts.facts, scope)

    val savingTips = SavingTipsRepository(
        ledgerSource, TipStore(dataStore), contracts.facts, tags.facts, receiptFacts, scope, budgets.facts
    )

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
