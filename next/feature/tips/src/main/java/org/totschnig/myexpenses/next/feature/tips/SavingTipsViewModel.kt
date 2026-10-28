package org.totschnig.myexpenses.next.feature.tips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.next.core.model.ledger.AnalysisState
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.tips.SavingTip
import org.totschnig.myexpenses.next.data.tips.SavingTipsOverview
import org.totschnig.myexpenses.next.data.tips.SavingTipsRepository
import org.totschnig.myexpenses.next.data.tips.TipStore
import org.totschnig.myexpenses.next.data.tips.savingTips

/**
 * The saving tips of [repository] for the tab, and what the user decides about them, which goes
 * into its store
 */
class SavingTipsViewModel(private val repository: SavingTipsRepository) : ViewModel() {

    val ledgerSource: LedgerSource
        get() = repository.ledgerSource

    private val store: TipStore
        get() = repository.store

    /** Tips where to spend less, see [SavingTipsRepository.savingTips] */
    val savingTips: StateFlow<AnalysisState<SavingTipsOverview>> =
        repository.savingTips.stateWhileShown(viewModelScope, AnalysisState.Loading)

    /** Hides the tip until something new comes up, e.g. another price increase */
    fun dismissTip(tip: SavingTip) {
        viewModelScope.launch { store.dismiss(tip) }
    }

    fun restoreTip(tip: SavingTip) {
        viewModelScope.launch { store.restore(tip) }
    }

    fun markTipsSeen(tips: List<SavingTip>) {
        viewModelScope.launch { store.markSeen(tips) }
    }
}
