package org.totschnig.myexpenses.charts.scale

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A value axis with round ticks, e.g. 0, 50, 100, 150. The first and the last tick are the ends of
 * the axis, so that every value lies on or between two gridlines.
 */
class ValueAxis(val ticks: List<Double>) {
    init {
        require(ticks.size >= 2) { "An axis needs at least two ticks" }
    }

    val min: Double get() = ticks.first()
    val max: Double get() = ticks.last()

    /** Where [value] lies between [min] (0) and [max] (1), values outside are clamped */
    fun fraction(value: Double): Float = ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()

    companion object {
        /** Steps between ticks, each times a power of ten */
        private val NICE_STEPS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)

        /**
         * The axis with the smallest round step that covers [min] to [max] with at most [maxTicks]
         * ticks. Zero is always included, so that columns and bars grow from a true baseline. With
         * negative and positive values that takes at least three ticks.
         */
        fun of(min: Double, max: Double, maxTicks: Int = MAX_TICKS): ValueAxis {
            require(maxTicks >= 2) { "maxTicks must be at least 2" }
            val low = minOf(min, 0.0)
            val high = maxOf(max, 0.0)
            if (high == low) return ValueAxis(listOf(0.0, 1.0))
            val limit = if (low < 0 && high > 0) maxOf(maxTicks, 3) else maxTicks
            val rough = (high - low) / (limit - 1)
            var magnitude = 10.0.pow(floor(log10(rough)))
            while (true) {
                for (nice in NICE_STEPS) {
                    val step = nice * magnitude
                    val first = floor(low / step).roundToLong()
                    val last = ceil(high / step).roundToLong()
                    if (last - first + 1 <= limit) {
                        // Multiplying the index avoids the drift of adding up the step
                        return ValueAxis((first..last).map { it * step })
                    }
                }
                magnitude *= 10
            }
        }

        /** Enough gridlines to read off a value, few enough to stay in the background */
        const val MAX_TICKS = 4
    }
}
