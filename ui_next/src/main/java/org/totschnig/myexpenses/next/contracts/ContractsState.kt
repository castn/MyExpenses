package org.totschnig.myexpenses.next.contracts

sealed interface ContractsUiState {
    data object Loading : ContractsUiState

    /** Shown on first opening: the user has not yet decided whether transactions may be analysed */
    data object AskConsent : ContractsUiState

    data object Declined : ContractsUiState

    data class Ready(val contracts: List<Contract>) : ContractsUiState
}
