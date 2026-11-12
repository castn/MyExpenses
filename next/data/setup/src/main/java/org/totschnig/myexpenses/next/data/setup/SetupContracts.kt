package org.totschnig.myexpenses.next.data.setup

/**
 * What the setup knows about contracts, from the contracts area, which may be left out
 * (`feat-optional-area`)
 */
sealed interface SetupContracts {

    /** Without the contracts area or without the analysis: there is no contracts step */
    data object None : SetupContracts

    /** @param suggestions contracts detected and not confirmed yet, one decision each */
    data class Available(val suggestions: Int) : SetupContracts
}
