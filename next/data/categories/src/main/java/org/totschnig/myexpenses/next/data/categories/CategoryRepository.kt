package org.totschnig.myexpenses.next.data.categories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.totschnig.myexpenses.next.core.common.ledger.Memo
import org.totschnig.myexpenses.next.core.common.ledger.mapMemo
import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.common.shareWhileShown
import org.totschnig.myexpenses.next.core.model.ledger.CategoryInfo
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource

/**
 * The suggestions of categories for transactions without one, once for the process, see
 * [CategorySuggester]. Certain suggestions for new transactions are applied without asking by
 * [appliedAutomatically]; the decisions of the user are kept in [store]. The flows run in [scope]
 * while they are collected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryRepository(
    val ledgerSource: LedgerSource,
    val store: CategoryStore,
    val writing: CategoryWriting,
    scope: CoroutineScope,
) {
    private val decisions: Flow<CategoryDecisions> = store.decisions

    private val categories: Flow<List<CategoryInfo>> = ledgerSource.catalog.map { it.categories }.distinctUntilChanged()

    private val inputs: Flow<List<CategoryInput>> =
        ledgerSource.ledger.mapMemo(Memo("categories: inputs", ::categoryInputs)).filterNotNull()

    /** Everything the suggestions depend on; decisions other than the rules only change the plan */
    private data class SuggestionInput(
        val inputs: List<CategoryInput>,
        val rules: List<CategoryRule>,
        val categories: List<CategoryInfo>,
    )

    private val suggestionMemo = Memo<SuggestionInput, List<CategorySuggestion>>("categories: suggestions") { (inputs, rules, categories) ->
        CategorySuggester(inputs, rules, categories).suggestAll(inputs.filter { it.categoryId == null })
    }

    /** Null without consent or while loading */
    val analysis: SharedFlow<CategoryAnalysis?> =
        ledgerSource.consent.map { it == true }.distinctUntilChanged()
            .flatMapLatest { hasConsent ->
                if (hasConsent) combine(inputs, categories, decisions) { inputs, categories, decisions ->
                    val suggestions = suggestionMemo.of(SuggestionInput(inputs, decisions.rules, categories))
                    CategoryAnalysis(inputs, decisions, decisions.plan(suggestions), categories.associate { it.id to it.path })
                } else flowOf(null)
            }
            .logTimings("categories: suggestions")
            .flowOn(Dispatchers.Default)
            .shareWhileShown(scope)

    /** The analysis the automatic decisions were last made for */
    @Volatile
    private var decidedAnalysis: CategoryAnalysis? = null

    /**
     * Applies certain suggestions for new transactions while it is collected: while the app is
     * shown and right after an import, never in the background otherwise. On the first analysis,
     * the transactions so far become the backlog.
     */
    val appliedAutomatically: SharedFlow<CategoryAnalysis> =
        analysis.filterNotNull()
            .onEach { analysis ->
                // A reused analysis was decided on already, e.g. the last one after a restart
                if (analysis === decidedAnalysis) return@onEach
                decidedAnalysis = analysis
                if (analysis.decisions.firstNewId == null) {
                    val firstNewId = (analysis.inputs.maxOfOrNull { it.id } ?: 0) + 1
                    store.update { it.copy(firstNewId = firstNewId) }
                } else if (analysis.plan.apply.isNotEmpty()) {
                    applyAutomatically(analysis.plan.apply)
                }
            }
            .shareWhileShown(scope)

    /** Gives the transactions the categories suggested for sure and remembers it was done without asking */
    suspend fun applyAutomatically(suggestions: List<CategorySuggestion>) {
        suggestions.groupBy { it.categoryId }.forEach { (categoryId, group) ->
            writing.setMissingCategory(group.map { it.transactionId }, categoryId)
        }
        store.update { it.withAutomatic(suggestions) }
    }
}

/**
 * The suggestions for the transactions analysed
 *
 * @param inputs the transactions analysed, to find those of a payee
 * @param paths of the categories by id, to show suggestions
 */
class CategoryAnalysis(
    val inputs: List<CategoryInput>,
    val decisions: CategoryDecisions,
    val plan: CategoryPlan,
    val paths: Map<Long, String>,
)
