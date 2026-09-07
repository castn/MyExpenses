package org.totschnig.myexpenses.next.contracts

/**
 * Decisions of the user about detected contracts.
 *
 * @param consent null as long as the user has not been asked whether transactions may be analysed
 * @param dismissed signatures of contracts the user removed as "not a contract"
 * @param names custom names by signature
 */
data class ContractSettings(
    val consent: Boolean? = null,
    val dismissed: Set<String> = emptySet(),
    val names: Map<String, String> = emptyMap(),
)

sealed interface ContractsUiState {
    data object Loading : ContractsUiState

    /** Shown on first opening: the user has not yet decided whether transactions may be analysed */
    data object AskConsent : ContractsUiState

    data object Declined : ContractsUiState

    data class Ready(
        /** Sorted by monthly amount, highest first */
        val active: List<Contract>,
        /** Last debited first */
        val ended: List<Contract>,
        /** Removed by the user, sorted by name */
        val dismissed: List<Contract>,
    ) : ContractsUiState {
        val isEmpty: Boolean get() = active.isEmpty() && ended.isEmpty() && dismissed.isEmpty()
    }
}

/**
 * Applies the [settings] to the [contracts] found by [ContractDetector].
 */
fun buildContractsState(contracts: List<Contract>, settings: ContractSettings): ContractsUiState.Ready {
    val (dismissed, shown) = contracts
        .map { contract -> settings.names[contract.signature]?.let { contract.copy(customName = it) } ?: contract }
        .partition { it.signature in settings.dismissed }
    val (active, ended) = shown.partition { it.isActive }
    return ContractsUiState.Ready(
        active = active.sortedByDescending { it.monthlyAmount },
        ended = ended.sortedByDescending { it.lastDate },
        dismissed = dismissed.sortedBy { it.displayName.lowercase() }
    )
}
