package org.totschnig.myexpenses.next.feature.guidance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import org.totschnig.myexpenses.next.core.common.stateWhileShown
import org.totschnig.myexpenses.next.data.guidance.DataReadiness
import org.totschnig.myexpenses.next.data.guidance.DataReadinessRepository

/**
 * How well the transactions are described for the screens, see [DataReadinessRepository]
 */
class DataReadinessViewModel(repository: DataReadinessRepository) : ViewModel() {

    /** Null while loading or without consent to the analysis */
    val readiness: StateFlow<DataReadiness?> = repository.readiness.stateWhileShown(viewModelScope, null)
}
