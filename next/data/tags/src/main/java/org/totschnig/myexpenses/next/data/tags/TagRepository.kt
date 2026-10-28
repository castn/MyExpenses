package org.totschnig.myexpenses.next.data.tags

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.ledger.Memo
import org.totschnig.myexpenses.next.core.common.ledger.mapMemo
import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.common.shareWhileShown

/**
 * What the tags of the user mean for the analyses, once for the process: the rows the analyses of
 * tags read and the [TagFacts] other features read. The meanings and rules are kept in [store],
 * the rules are applied by [appliedRules]. The flows run in [scope] while they are collected.
 */
class TagRepository(
    val ledgerSource: LedgerSource,
    val store: TagStore,
    scope: CoroutineScope,
    private val tagDatabase: TagDatabase,
) {
    /**
     * The transactions as the analyses of tags read them, computed once for suggestions, trips and
     * refunds. Null while loading or without consent.
     */
    val rows: SharedFlow<List<TagRow>?> =
        ledgerSource.ledger.mapMemo(Memo("tags: rows", ::tagRows))
            .flowOn(Dispatchers.Default)
            .shareWhileShown(scope)

    private data class ExcludedInput(val ledger: Ledger, val meanings: Map<Long, TagMeaning>)

    private val excludedMemo = Memo<ExcludedInput, Set<Long>>("tags: facts") { (ledger, meanings) ->
        TagMeaning.excludedFromSpending(ledger, meanings)
    }

    /** What other features know about tags */
    val facts: SharedFlow<TagFacts> =
        combine(ledgerSource.ledger, store.meanings) { ledger, meanings ->
            // Without consent there is nothing to exclude, and tips are not shown
            TagFacts(ledger?.let { excludedMemo.of(ExcludedInput(it, meanings)) }.orEmpty(), meanings)
        }
            .distinctUntilChanged()
            .logTimings("tags: facts")
            .shareWhileShown(scope)

    /** What [TagLearner] learns from and the rules are applied to, null while loading or without consent */
    val tagInputs: SharedFlow<List<TagInput>?> =
        rows.mapMemo(Memo("tags: learning inputs") { it.map(TagRow::toTagInput) })
            .shareWhileShown(scope)

    /** The inputs and rules the rules were last applied to */
    @Volatile
    private var applied: Pair<List<TagInput>, List<TagRule>>? = null

    /**
     * Gives new transactions the tags of the [TagRule]s while it is collected: while the app is
     * shown and right after an import, never in the background otherwise. Emits the tags given,
     * by transaction.
     */
    val appliedRules: SharedFlow<Map<Long, Set<Long>>> =
        combine(tagInputs.filterNotNull(), store.rules) { inputs, rules ->
            // Inputs reused after a restart were applied already
            if (applied?.let { it.first === inputs && it.second == rules } == true) emptyMap()
            else TagLearner.applyRules(inputs, rules).also { applied = inputs to rules }
        }
            .distinctUntilChanged()
            .onEach { additions -> additions.forEach { (transactionId, tagIds) -> tagDatabase.addTags(transactionId, tagIds) } }
            .flowOn(Dispatchers.IO)
            .shareWhileShown(scope)
}
