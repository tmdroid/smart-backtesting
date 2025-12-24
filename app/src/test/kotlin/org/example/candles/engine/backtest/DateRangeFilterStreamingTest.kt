package org.example.candles.engine.backtest

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.example.candles.domain.Candle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DateRangeFilterStreamingTest {
    @Test
    fun `filter consumes sequence only once`() {
        val candle = candleAt(Instant.parse("2025-01-01T00:00:00Z"))
        val singleUse = singleUseSequence(candle)
        val range = DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))

        val filtered = DateRangeFilter.filter(singleUse, range, ZoneId.of("UTC")).toList()
        assertEquals(1, filtered.size)
    }

    private fun candleAt(time: Instant): Candle {
        return Candle(
            start = time,
            endExclusive = time.plusSeconds(60),
            open = 1.0,
            high = 1.0,
            low = 1.0,
            close = 1.0,
            volume = 1L
        )
    }

    private fun singleUseSequence(candle: Candle): Sequence<Candle> {
        var used = false
        return Sequence {
            if (used) {
                throw IllegalStateException("Sequence iterated more than once")
            }
            used = true
            listOf(candle).iterator()
        }
    }
}
