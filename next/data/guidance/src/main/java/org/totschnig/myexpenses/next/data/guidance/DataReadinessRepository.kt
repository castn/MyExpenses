package org.totschnig.myexpenses.next.data.guidance

import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.totschnig.myexpenses.next.core.model.ledger.Ledger
import org.totschnig.myexpenses.next.core.model.ledger.LedgerSource
import org.totschnig.myexpenses.next.core.common.ledger.Memo
import org.totschnig.myexpenses.next.core.common.ledger.mapMemo
import org.totschnig.myexpenses.next.core.common.logTimings
import org.totschnig.myexpenses.next.core.model.receipts.ReceiptFacts
import org.totschnig.myexpenses.next.core.common.shareWhileShown

/**
 * How well the transactions are described, see [DataReadiness], once for the process. The flows
 * run in [scope] while they are collected.
 */
class DataReadinessRepository(
    ledgerSource: LedgerSource,
    receiptFacts: Flow<ReceiptFacts>,
    scope: CoroutineScope,
) {
    private data class ReadinessInput(val ledger: Ledger, val receipts: ReceiptFacts, val today: LocalDate)

    private val memo = Memo<ReadinessInput, DataReadiness>("guidance: readiness") { input ->
        with(input) { dataReadiness(ledger, receipts, today) }
    }

    /** Null while loading or without consent to the analysis */
    val readiness: SharedFlow<DataReadiness?> =
        combine(ledgerSource.ledger, receiptFacts, ledgerSource.today) { ledger, receipts, today ->
            ledger?.let { ReadinessInput(it, receipts, today) }
        }
            .mapMemo(memo)
            .logTimings("guidance: readiness")
            .flowOn(Dispatchers.Default)
            .shareWhileShown(scope)
}
