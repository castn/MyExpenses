package org.totschnig.myexpenses.next.data.tags

import org.totschnig.myexpenses.next.core.model.words.Words
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * An expense as seen by [TripDetector]
 *
 * @param isForeign paid in another currency than the one of the account
 */
data class TripInput(
    val id: Long,
    val date: LocalDate,
    val amount: Long,
    val payeeName: String?,
    val comment: String?,
    val isForeign: Boolean = false,
)

/**
 * Days that look like a trip, with all expenses in between
 *
 * @param signals the expenses the trip was found by, see [TripDetector.isSignal]
 */
data class Trip(
    val start: LocalDate,
    val end: LocalDate,
    val transactions: List<TripInput>,
    val signals: List<TripInput>,
) {
    /** Identifies the trip, e.g. to remember that the user said no */
    val key: String get() = "$start:$end"
}

/**
 * Finds trips by expenses in a foreign currency or with words of travelling (hotels, flights,
 * fees for paying abroad) on days close to each other.
 */
object TripDetector {
    /** Days without signal allowed within a trip */
    const val MAX_GAP_DAYS = 2L

    /** Different days with a signal needed, so that a single booking is no trip */
    const val MIN_SIGNAL_DAYS = 2

    /** Matched at the start of a word */
    private val WORDS = listOf(
        "hotel", "hostel", "booking com", "airbnb", "ferienwohnung", "ferienhaus", "camping", "jugendherberge",
        "lufthansa", "eurowings", "ryanair", "easyjet", "condor", "wizz air", "klm", "air france", "tuifly",
        "flughafen", "airport", "auslandseinsatz", "fremdwährung", "fremdwaehrung"
    )

    fun isSignal(transaction: TripInput): Boolean {
        if (transaction.isForeign) return true
        val text = Words.textOf(transaction.payeeName, transaction.comment)
        return WORDS.any { Words.startsWord(text, it) }
    }

    /**
     * @param expenses debits without transfers and contract payments
     * @param ignored expenses already tagged with a trip; trips mostly made of them are no suggestion
     * @param rejected [Trip.key]s the user said no to
     * @return the trips found, the latest first
     */
    fun find(
        expenses: List<TripInput>,
        ignored: Set<Long> = emptySet(),
        rejected: Set<String> = emptySet(),
    ): List<Trip> {
        val signalDays = expenses.filter(::isSignal).map { it.date }.distinct().sorted()
        val ranges = mutableListOf<MutableList<LocalDate>>()
        signalDays.forEach { day ->
            val last = ranges.lastOrNull()
            if (last != null && ChronoUnit.DAYS.between(last.last(), day) <= MAX_GAP_DAYS + 1) last += day
            else ranges += mutableListOf(day)
        }
        return ranges
            .filter { it.size >= MIN_SIGNAL_DAYS }
            .map { days ->
                val inRange = expenses.filter { it.date in days.first()..days.last() }
                Trip(days.first(), days.last(), inRange.sortedBy { it.date }, inRange.filter(::isSignal))
            }
            .filter { trip -> trip.key !in rejected && trip.transactions.count { it.id in ignored } * 2 < trip.transactions.size }
            .sortedByDescending { it.start }
    }
}
