package org.totschnig.fixturecollector

import kotlin.math.abs

private val amount = Regex("""^(-)?\s*(\d{1,6})(?:\s?[,.]\s?(\d{1,2}))?\s*(-)?$""")

/** "1,79" → 179, "-0,30" and "0,30-" → -30, "12" → 1200; null if it is no amount */
fun parseCents(text: String): Long? {
    val match = amount.matchEntire(text.trim()) ?: return null
    val (minus, euros, cents, trailingMinus) = match.destructured
    val value = euros.toLong() * 100 + cents.padEnd(2, '0').ifEmpty { "0" }.toLong()
    return if (minus.isNotEmpty() || trailingMinus.isNotEmpty()) -value else value
}

/** 179 → "1,79", -30 → "-0,30" */
fun formatCents(cents: Long) = (if (cents < 0) "-" else "") +
    "${abs(cents) / 100},${(abs(cents) % 100).toString().padStart(2, '0')}"
