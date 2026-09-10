package org.totschnig.myexpenses.next.contracts

import org.totschnig.myexpenses.next.balance.SalaryChoice
import org.totschnig.myexpenses.next.balance.salary

/**
 * Decisions of the user about detected contracts.
 *
 * @param consent null as long as the user has not been asked whether transactions may be analysed
 * @param dismissed signatures of contracts the user removed as "not a contract"
 * @param names custom names by signature
 * @param areas keys of the categories chosen by the user (or [AREA_NONE]) by signature,
 * contracts without entry use the suggested category
 * @param customAreas categories created by the user, in the order they were created
 * @param salary which regular income is the salary
 */
data class ContractSettings(
    val consent: Boolean? = null,
    val dismissed: Set<String> = emptySet(),
    val names: Map<String, String> = emptyMap(),
    val areas: Map<String, String> = emptyMap(),
    val customAreas: List<CustomArea> = emptyList(),
    val salary: SalaryChoice = SalaryChoice.Automatic,
) {
    fun areaChoice(signature: String): AreaChoice {
        val key = areas[signature] ?: return AreaChoice.Automatic
        if (key == AREA_NONE) return AreaChoice.Fixed(null)
        // A deleted custom category falls back to the suggestion
        val area = BuiltInArea.fromKey(key) ?: customAreas.find { it.key == key } ?: return AreaChoice.Automatic
        return AreaChoice.Fixed(area)
    }

    companion object {
        /** Stored for contracts the user explicitly put into no category */
        const val AREA_NONE = "NONE"
    }
}

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
        val customAreas: List<CustomArea> = emptyList(),
        val salaryChoice: SalaryChoice = SalaryChoice.Automatic,
    ) : ContractsUiState {
        /** The regular income that is the salary, see [SalaryChoice] */
        val salary: Contract? get() = active.salary(salaryChoice)

        val isEmpty: Boolean get() = active.isEmpty() && ended.isEmpty() && dismissed.isEmpty()

        /**
         * Categories that get a tab: built-in ones only if they contain contracts,
         * custom ones always, so that a new category can be filled.
         */
        val areas: List<ContractArea>
            get() = BuiltInArea.entries.filter { area -> (active + ended).any { it.area == area } } + customAreas

        /** All categories a contract can be put into */
        val selectableAreas: List<ContractArea> get() = BuiltInArea.entries + customAreas

        /** Only contracts of [area] */
        fun forArea(area: ContractArea) = copy(
            active = active.filter { it.area == area },
            ended = ended.filter { it.area == area },
            dismissed = dismissed.filter { it.area == area }
        )
    }
}

/**
 * Applies the [settings] to the [contracts] found by [ContractDetector].
 */
fun buildContractsState(contracts: List<Contract>, settings: ContractSettings): ContractsUiState.Ready {
    val (dismissed, shown) = contracts
        .map { contract ->
            contract.copy(
                customName = settings.names[contract.signature],
                areaChoice = settings.areaChoice(contract.signature)
            )
        }
        .partition { it.signature in settings.dismissed }
    val (active, ended) = shown.partition { it.isActive }
    return ContractsUiState.Ready(
        active = active.sortedByDescending { it.monthlyAmount },
        ended = ended.sortedByDescending { it.lastDate },
        dismissed = dismissed.sortedBy { it.displayName.lowercase() },
        customAreas = settings.customAreas,
        salaryChoice = settings.salary
    )
}
