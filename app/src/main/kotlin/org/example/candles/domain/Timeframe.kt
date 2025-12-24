package org.example.candles.domain

import java.time.Duration

class Timeframe private constructor(private val duration: Duration) {
    val millis: Long = duration.toMillis()

    @Suppress("unused")
    fun isMultipleOf(other: Timeframe): Boolean = millis % other.millis == 0L

    internal fun toDuration(): Duration = duration

    override fun toString(): String {
        val dayMillis = Duration.ofDays(1).toMillis()
        val hourMillis = Duration.ofHours(1).toMillis()
        val minuteMillis = Duration.ofMinutes(1).toMillis()
        return when {
            millis % dayMillis == 0L -> "${millis / dayMillis}d"
            millis % hourMillis == 0L -> "${millis / hourMillis}h"
            millis % minuteMillis == 0L -> "${millis / minuteMillis}m"
            else -> "${millis}ms"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Timeframe) return false
        return millis == other.millis
    }

    override fun hashCode(): Int = millis.hashCode()

    companion object {
        private val pattern = Regex("^(\\d+)([mhd])$")

        fun parse(value: String): Timeframe {
            val match = pattern.matchEntire(value.trim())
                ?: throw IllegalArgumentException("Invalid timeframe: $value")
            val amount = match.groupValues[1].toLong()
            if (amount <= 0L) {
                throw IllegalArgumentException("Timeframe must be positive: $value")
            }
            val unit = match.groupValues[2]
            val duration = when (unit) {
                "m" -> Duration.ofMinutes(amount)
                "h" -> Duration.ofHours(amount)
                "d" -> Duration.ofDays(amount)
                else -> throw IllegalArgumentException("Unsupported timeframe unit: $unit")
            }
            return Timeframe(duration)
        }

        internal fun fromDuration(duration: Duration): Timeframe {
            if (duration.isZero || duration.isNegative) {
                throw IllegalArgumentException("Timeframe duration must be positive: $duration")
            }
            val minuteMillis = Duration.ofMinutes(1).toMillis()
            if (duration.toMillis() % minuteMillis != 0L) {
                throw IllegalArgumentException("Timeframe duration must be whole minutes: $duration")
            }
            return Timeframe(duration)
        }
    }
}
