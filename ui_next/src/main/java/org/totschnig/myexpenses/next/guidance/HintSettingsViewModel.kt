package org.totschnig.myexpenses.next.guidance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.data.guidance.HintStore

/** The hints the user closed, in the settings */
class HintSettingsViewModel(private val hintStore: HintStore) : ViewModel() {

    /** How many kinds of hints the user closed */
    val closedHints: StateFlow<Int> = hintStore.dismissed.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Shows the hints the user closed again */
    fun resetHints() {
        viewModelScope.launch { hintStore.reset() }
    }
}
