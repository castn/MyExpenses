package org.totschnig.fixturecollector

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Names of new fixtures, so that the user does not have to make one up: the first word of the
 * merchant and the time of the photo, e.g. "aldi-20261003-1432". The time keeps names apart from
 * the fixtures already in the repository ("aldi") and from those of other collectors.
 */
object FixtureNames {

    private const val FALLBACK = "bon"
    private const val MAX_MERCHANT = 20
    private val time = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    private val umlauts = mapOf('ä' to "ae", 'ö' to "oe", 'ü' to "ue", 'ß' to "ss")

    /** "Aldi Süd" → "aldi", "Müller" → "mueller", null if no letters or digits are left */
    fun merchantPart(merchant: String?) = merchant
        ?.lowercase()
        ?.flatMap { umlauts[it]?.toList() ?: listOf(it) }
        ?.joinToString("")
        ?.split(Regex("[^a-z0-9]+"))
        ?.firstOrNull { it.isNotEmpty() }
        ?.take(MAX_MERCHANT)

    /** The first name not taken, with "-2", "-3" … if needed */
    fun suggest(merchant: String?, takenAt: LocalDateTime, exists: (String) -> Boolean): String {
        val base = "${merchantPart(merchant) ?: FALLBACK}-${takenAt.format(time)}"
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) base else "$base-$it" }
            .first { !exists(it) }
    }
}
