package org.totschnig.myexpenses.next.onboarding

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.listSaver
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The first account as entered on the first start, created only once the user starts the app,
 * so that going back from the last page does not create it twice
 *
 * @param amount the opening balance as typed
 */
@Immutable
data class AccountDraft(val label: String, val currency: String, val amount: String) {

    /** The opening balance, null if it is no number */
    val openingBalance: BigDecimal? get() = parseAmount(amount)

    companion object {
        val Saver = listSaver<AccountDraft, String>(
            save = { listOf(it.label, it.currency, it.amount) },
            restore = { AccountDraft(it[0], it[1], it[2]) }
        )
    }
}

/**
 * An amount as typed: comma or point before the decimals, no separators of thousands, so that
 * "12,50" and "12.50" mean the same everywhere. Empty is zero, null if it is no number.
 */
internal fun parseAmount(text: String): BigDecimal? {
    val normalized = text.trim().replace(" ", "").replace('−', '-').replace(',', '.')
    if (normalized.isEmpty()) return BigDecimal.ZERO
    if (normalized.count { it == '.' } > 1) return null
    return normalized.toBigDecimalOrNull()
}

/** [amount] in the minor unit of a currency with [fractionDigits], null if it does not fit */
internal fun toMinorUnits(amount: BigDecimal, fractionDigits: Int): Long? = try {
    amount.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP).longValueExact()
} catch (_: ArithmeticException) {
    null
}
