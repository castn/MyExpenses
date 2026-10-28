package org.totschnig.myexpenses.next.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.getPartyName
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.data.tags.TagRule
import org.totschnig.myexpenses.next.data.tags.TagStore
import org.totschnig.myexpenses.next.settings.RuleItem
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider.TAGS_URI
import org.totschnig.myexpenses.provider.getString

/**
 * The rules of tags in the settings, and what the user said no to, for tags and for the categories
 * the tags build on
 */
class TagSettingsViewModel(
    private val tagStore: TagStore,
    private val categoryStore: CategoryStore,
    private val repository: Repository,
    /** Where the database is read */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val rules: StateFlow<List<RuleItem>?> = tagStore.rules
        .map { rules ->
            val tags = tagLabels()
            rules.map { it.toItem(tags) }
        }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** How many suggestions the user said no to */
    val rejectedCount: StateFlow<Int> = combine(
        listOf(categoryStore.decisions.map { it.rejected.size }) +
                TagStore.Rejected.entries.map { kind -> tagStore.rejected(kind).map { it.size } }
    ) { it.sum() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun resetRejected() {
        viewModelScope.launch {
            categoryStore.update { it.copy(rejected = emptyMap()) }
            tagStore.clearRejected()
        }
    }

    /** See [TagRule.decode] for the counterpart: "p:" and the id of a payee or "m:" and a merchant */
    private fun TagRule.toItem(tags: Map<Long, String>) = RuleItem(
        counterpart = when {
            counterpart.startsWith("p:") -> counterpart.removePrefix("p:").toLongOrNull()?.let(repository::getPartyName)
            else -> null
        } ?: counterpart.substringAfter(':'),
        target = tags[tagId] ?: "",
        remove = { viewModelScope.launch { tagStore.removeRule(this@toItem) } }
    )

    private fun tagLabels(): Map<Long, String> =
        repository.contentResolver.query(TAGS_URI, arrayOf(KEY_ROWID, KEY_LABEL), null, null, null)?.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getLong(0), cursor.getString(KEY_LABEL))
            }
        } ?: emptyMap()
}
