package org.totschnig.myexpenses.next.data.receipts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.common.measured
import org.totschnig.myexpenses.next.core.common.shareWhileShown
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.model.receipts.ScannedItem

/**
 * The scanned receipts of the user as other features know them, once for the process, read again
 * on each change of [database]. The flows run in [scope] while they are collected.
 */
class ReceiptRepository(
    val database: ReceiptDatabase,
    scope: CoroutineScope,
) {
    /** What other features know about scanned receipts */
    val facts: SharedFlow<ReceiptFacts> =
        database.changes()
            .map { measured("receipts: facts") { ReceiptFacts(database.loadAllItems().mapValues { (_, items) -> items.map { ScannedItem(it.name, it.amount, it.group) } }) } }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
            .logTimings("receipts: facts")
            .shareWhileShown(scope)
}
