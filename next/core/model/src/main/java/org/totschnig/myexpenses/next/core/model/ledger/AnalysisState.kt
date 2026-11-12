package org.totschnig.myexpenses.next.core.model.ledger

/**
 * What a screen of an analysis shows: its result, unless the user turned off that transactions are
 * analysed, see [LedgerSource.consent]
 */
sealed interface AnalysisState<out T> {
    data object Loading : AnalysisState<Nothing>

    /** The user turned off that transactions are analysed */
    data object Declined : AnalysisState<Nothing>

    data class Ready<out T>(val value: T) : AnalysisState<T>
}

val <T> AnalysisState<T>.valueOrNull: T?
    get() = (this as? AnalysisState.Ready)?.value

/**
 * @param value null while loading
 */
fun <T> analysisState(consent: Boolean, value: T?): AnalysisState<T> = when {
    !consent -> AnalysisState.Declined
    value == null -> AnalysisState.Loading
    else -> AnalysisState.Ready(value)
}
