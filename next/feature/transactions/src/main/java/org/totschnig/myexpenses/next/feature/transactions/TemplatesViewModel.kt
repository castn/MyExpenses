package org.totschnig.myexpenses.next.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.transactions.TemplateApplied
import org.totschnig.myexpenses.next.data.transactions.TemplateStore
import org.totschnig.myexpenses.next.data.transactions.TemplateSummary

/**
 * What saving from a template at once ended with, until it was shown
 *
 * @param title of the template
 */
data class TemplateMessage(val title: String, val result: TemplateApplied)

/** What the user can do in the list of templates, see [TemplatesFlow] */
interface TemplateListActions {
    /** Saves a transaction from [template] at once */
    fun apply(template: TemplateSummary)

    fun delete(template: TemplateSummary)

    fun messageShown()
}

/**
 * The list of templates: saves from them at once and deletes them. Opening one in the form and
 * changing one is left to the screens, see [TemplatesFlow].
 */
class TemplatesViewModel(
    private val store: TemplateStore,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel(),
    TemplateListActions {

    /** Null while they load */
    val templates: StateFlow<List<TemplateSummary>?> =
        store.templates().stateWhileShown(viewModelScope, null)

    private val _message = MutableStateFlow<TemplateMessage?>(null)
    val message: StateFlow<TemplateMessage?> = _message.asStateFlow()

    override fun apply(template: TemplateSummary) {
        viewModelScope.launch {
            val result = withContext(io) {
                runCatching { store.apply(template.id) }.getOrDefault(TemplateApplied.Failed)
            }
            _message.value = TemplateMessage(template.title, result)
        }
    }

    override fun delete(template: TemplateSummary) {
        viewModelScope.launch { withContext(io) { store.delete(template.id) } }
    }

    override fun messageShown() = _message.update { null }
}
