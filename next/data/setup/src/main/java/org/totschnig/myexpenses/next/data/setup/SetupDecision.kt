package org.totschnig.myexpenses.next.data.setup

import java.io.Serializable
import org.totschnig.myexpenses.next.data.cash.CashAccount
import org.totschnig.myexpenses.next.data.categories.CashCategorization
import org.totschnig.myexpenses.next.data.categories.Categorization
import org.totschnig.myexpenses.next.data.categories.Counterpart

/**
 * A decision on a payee in the setup, kept so that it can be taken back, see
 * [SetupRepository.takeBack]. [Serializable] for the saved state.
 */
sealed interface SetupDecision : Serializable {
    /** [Counterpart.key] of the payee */
    val key: String

    /** The payee got a category */
    data class Categorized(val categorization: Categorization) : SetupDecision {
        override val key get() = categorization.counterpart.key

        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Some debits of the payee got a category, without a rule: they are for different things
     * ("Unterschiedlich")
     */
    data class Assigned(val categorization: Categorization) : SetupDecision {
        override val key get() = categorization.counterpart.key

        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    /** The user knows no category for the payee ("Weiß ich nicht") */
    data class Unclear(val counterpart: Counterpart) : SetupDecision {
        override val key get() = counterpart.key

        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * The cash withdrawals got a category, and either a rule for later ones or, with [account], a
     * cash account they go to from now on
     */
    data class Cash(val categorization: CashCategorization, val account: CashAccount? = null) :
        SetupDecision {
        override val key get() = SetupCash.KEY

        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    /** Put off for now ("Später"), nothing was written */
    data class Later(override val key: String) : SetupDecision {
        private companion object {
            private const val serialVersionUID = 1L
        }
    }
}
