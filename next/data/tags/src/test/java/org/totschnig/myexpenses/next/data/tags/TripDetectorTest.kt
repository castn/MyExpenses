package org.totschnig.myexpenses.next.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripDetectorTest {

    private val august: LocalDate = LocalDate.of(2026, 8, 1)
    private var nextId = 1L

    private fun expense(day: Long, payee: String?, comment: String? = null, foreign: Boolean = false) =
        TripInput(nextId++, august.plusDays(day), -2000, payee, comment, foreign)

    @Test
    fun foreignCurrencyAndHotelsMakeATrip() {
        val before = expense(0, "REWE")
        val hotel = expense(2, "Hotel Bella Vista")
        val restaurant = expense(3, "Trattoria Roma", foreign = true)
        val market = expense(4, "Conad")
        val gelato = expense(6, "Gelateria", foreign = true)
        val after = expense(12, "REWE")
        val trip = TripDetector.find(listOf(before, hotel, restaurant, market, gelato, after)).single()
        assertEquals(august.plusDays(2), trip.start)
        assertEquals(august.plusDays(6), trip.end)
        assertEquals(listOf(hotel, restaurant, market, gelato), trip.transactions)
        assertEquals(listOf(hotel, restaurant, gelato), trip.signals)
    }

    @Test
    fun aSingleBookingIsNoTrip() {
        assertTrue(TripDetector.find(listOf(expense(0, "Booking.com BV"), expense(1, "REWE"))).isEmpty())
    }

    @Test
    fun longGapsSplitTrips() {
        val trips = TripDetector.find(
            listOf(
                expense(0, "Lufthansa"), expense(1, null, "Kartenzahlung Fremdwährung"),
                expense(10, "Ryanair"), expense(13, "Airbnb")
            )
        )
        assertEquals(listOf(august.plusDays(10), august), trips.map { it.start })
    }

    @Test
    fun wordsMatchAtTheStartOfAWordOnly() {
        assertTrue(TripDetector.isSignal(expense(0, "Auslandseinsatzentgelt")))
        assertTrue(!TripDetector.isSignal(expense(0, "Shotel Bar")))
    }

    @Test
    fun taggedOrRejectedTripsAreNotSuggested() {
        val expenses = listOf(expense(0, "Hotel Adler"), expense(1, "Airbnb"), expense(1, "Bäckerei"))
        val trip = TripDetector.find(expenses).single()
        assertTrue(TripDetector.find(expenses, rejected = setOf(trip.key)).isEmpty())
        assertTrue(TripDetector.find(expenses, ignored = expenses.take(2).mapTo(HashSet()) { it.id }).isEmpty())
        assertEquals(1, TripDetector.find(expenses, ignored = setOf(expenses[0].id)).size)
    }
}
