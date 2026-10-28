package org.totschnig.myexpenses.next.feature.receipts

import android.os.Bundle
import java.time.DateTimeException
import org.json.JSONException
import org.json.JSONObject
import org.totschnig.myexpenses.next.core.model.ledger.CurrencyInfo
import org.totschnig.myexpenses.next.data.receipts.ReceiptDraft
import org.totschnig.myexpenses.next.data.receipts.leftoverRowsOf
import org.totschnig.myexpenses.next.data.receipts.receiptDraftOf
import org.totschnig.myexpenses.next.data.receipts.receiptOf
import org.totschnig.myexpenses.next.data.receipts.toJson

/**
 * What [ReceiptViewModel] keeps in its saved state, so that a receipt being checked or assigned
 * survives when Android ends the process, e.g. while the document scanner is open. The candidates
 * of the assignment are looked up again, the transactions may have changed meanwhile.
 */
internal class ReceiptSavedState(
    val draft: ReceiptDraft?,
    val draftPayee: String?,
    val standaloneCurrency: CurrencyInfo?,
    val assignment: ReceiptAssignment?,
) {
    fun toBundle() = Bundle().apply {
        draft?.let { putString(KEY_DRAFT, it.toJson()) }
        draftPayee?.let { putString(KEY_DRAFT_PAYEE, it) }
        standaloneCurrency?.let {
            putString(KEY_CURRENCY_CODE, it.code)
            putString(KEY_CURRENCY_SYMBOL, it.symbol)
            putInt(KEY_CURRENCY_DIGITS, it.fractionDigits)
        }
        assignment?.let { putString(KEY_ASSIGNMENT, it.toJson()) }
    }

    companion object {
        private const val KEY_DRAFT = "draft"
        private const val KEY_DRAFT_PAYEE = "draftPayee"
        private const val KEY_CURRENCY_CODE = "currencyCode"
        private const val KEY_CURRENCY_SYMBOL = "currencySymbol"
        private const val KEY_CURRENCY_DIGITS = "currencyDigits"
        private const val KEY_ASSIGNMENT = "assignment"

        fun of(bundle: Bundle) = ReceiptSavedState(
            draft = bundle.getString(KEY_DRAFT)?.let(::receiptDraftOf),
            draftPayee = bundle.getString(KEY_DRAFT_PAYEE),
            standaloneCurrency = bundle.getString(KEY_CURRENCY_CODE)?.let { code ->
                CurrencyInfo(code, bundle.getString(KEY_CURRENCY_SYMBOL) ?: code, bundle.getInt(KEY_CURRENCY_DIGITS))
            },
            assignment = bundle.getString(KEY_ASSIGNMENT)?.let(::receiptAssignmentOf)
        )
    }
}

private fun ReceiptAssignment.toJson() = JSONObject().apply {
    put("receipt", receipt.toJson())
    itemCount?.let { put("itemCount", it) }
    waitingId?.let { put("waitingId", it) }
    put("leftoverRows", leftoverRows.toJson())
}.toString()

private fun receiptAssignmentOf(json: String) = try {
    with(JSONObject(json)) {
        ReceiptAssignment(
            receipt = receiptOf(getJSONObject("receipt")),
            itemCount = if (has("itemCount")) getInt("itemCount") else null,
            waitingId = if (has("waitingId")) getLong("waitingId") else null,
            candidates = emptyList(),
            leftoverRows = leftoverRowsOf(getJSONArray("leftoverRows"))
        )
    }
} catch (_: JSONException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: DateTimeException) {
    null
}
