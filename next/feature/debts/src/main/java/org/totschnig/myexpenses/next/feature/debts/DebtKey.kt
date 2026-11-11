package org.totschnig.myexpenses.next.feature.debts

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The pages of the debts, the overview at the bottom of the back stack */
@Serializable
sealed interface DebtKey : NavKey {
    @Serializable
    data object Overview : DebtKey

    /** A debt with its history */
    @Serializable
    data class Detail(val debtId: Long) : DebtKey

    /** The form of a new debt, or of the debt with [debtId] */
    @Serializable
    data class Form(val debtId: Long? = null) : DebtKey
}
