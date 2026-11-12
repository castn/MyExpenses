package org.totschnig.myexpenses.next.feature.transactions

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The pages of the templates, the list at the bottom of the back stack */
@Serializable
sealed interface TemplateKey : NavKey {
    @Serializable
    data object List : TemplateKey

    /**
     * The form of a template
     *
     * @param templateId null for a new template
     * @param fromTransaction a new template starts with the values of this transaction
     */
    @Serializable
    data class Editor(val templateId: Long? = null, val fromTransaction: Long? = null) : TemplateKey
}
