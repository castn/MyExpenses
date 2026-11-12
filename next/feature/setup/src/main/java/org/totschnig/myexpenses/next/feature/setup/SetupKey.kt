package org.totschnig.myexpenses.next.feature.setup

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The pages of the setup, the overview of the steps at the bottom of the back stack */
@Serializable
sealed interface SetupKey : NavKey {
    @Serializable
    data object Start : SetupKey

    @Serializable
    data object Categories : SetupKey

    /** What the categories step brought: the share categorized without asking from now on */
    @Serializable
    data object Automatic : SetupKey

    @Serializable
    data object Contracts : SetupKey

    @Serializable
    data object Done : SetupKey
}
