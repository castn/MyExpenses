package org.totschnig.myexpenses.next.balance

import java.time.LocalDate

sealed interface BalanceUiState {
    data object Loading : BalanceUiState

    /** Transactions may not be analysed yet, the user has not been asked */
    data object AskConsent : BalanceUiState

    data object Declined : BalanceUiState

    data class Ready(val balance: MonthlyBalance, val today: LocalDate) : BalanceUiState
}
