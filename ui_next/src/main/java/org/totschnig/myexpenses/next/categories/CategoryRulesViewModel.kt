package org.totschnig.myexpenses.next.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.db2.DEFAULT_CATEGORY_PATH_SEPARATOR
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.getCategoryInfoList
import org.totschnig.myexpenses.db2.getPartyName
import org.totschnig.myexpenses.next.data.categories.CategoryRule
import org.totschnig.myexpenses.next.data.categories.CategoryStore
import org.totschnig.myexpenses.next.settings.RuleItem

/** The rules of categories in the settings, each can be deleted */
class CategoryRulesViewModel(
    private val categoryStore: CategoryStore,
    private val repository: Repository,
    /** Where the database is read */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val rules: StateFlow<List<RuleItem>?> = categoryStore.decisions
        .map { decisions -> decisions.rules.map { it.toItem() } }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun CategoryRule.toItem() = RuleItem(
        counterpart = (payeeIds.mapNotNull { repository.getPartyName(it) } + merchants).joinToString(", "),
        target = repository.getCategoryInfoList(categoryId)?.joinToString(DEFAULT_CATEGORY_PATH_SEPARATOR) { it.label }.orEmpty(),
        remove = { viewModelScope.launch { categoryStore.update { it.copy(rules = it.rules - this@toItem) } } }
    )
}
