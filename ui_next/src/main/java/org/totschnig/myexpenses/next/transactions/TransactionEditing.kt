package org.totschnig.myexpenses.next.transactions

import org.totschnig.myexpenses.next.feature.transactions.TagField
import org.totschnig.myexpenses.next.feature.transactions.TransactionEditActions
import org.totschnig.myexpenses.next.feature.transactions.TransactionEditState

/**
 * What changes transactions from their details: the state and actions of the form, see
 * [TransactionEditActions], and the tags in it
 *
 * @param tagField the tags in the form, null without the area of the tags
 */
class TransactionEditing(
    val state: TransactionEditState,
    val actions: TransactionEditActions,
    val tagField: TagField?
)
