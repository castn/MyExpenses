package org.totschnig.myexpenses.next.feature.setup

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The pages of the setup, the overview of the steps at the bottom of the back stack */
@Serializable
sealed interface SetupKey : NavKey {
    @Serializable
    data object Start : SetupKey

    /** Confirming the regular incomes and choosing the salary */
    @Serializable
    data object Income : SetupKey

    @Serializable
    data object Categories : SetupKey

    /** The debits without category of the payee of [payeeKey], named [title] */
    @Serializable
    data class Transactions(val payeeKey: String, val title: String) : SetupKey

    /**
     * The debits without category of the payee of [payeeKey], named [title], to give them
     * different categories ("Unterschiedlich")
     */
    @Serializable
    data class Differs(val payeeKey: String, val title: String) : SetupKey

    /** What the categories step brought: the share categorized without asking from now on */
    @Serializable
    data object Automatic : SetupKey

    @Serializable
    data object Contracts : SetupKey

    @Serializable
    data object Done : SetupKey
}
