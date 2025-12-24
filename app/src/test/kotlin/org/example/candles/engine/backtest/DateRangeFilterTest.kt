package org.example.candles.engine.backtest

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.example.candles.domain.Candle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DateRangeFilterTest {
    @Test
    fun `filter includes start and end inclusive`() {
        val timezone = ZoneId.of("UTC")
        val start = LocalDate.of(2025, 11, 1)
        val end = LocalDate.of(2025, 11, 2)
        val range = DateRange(start, end)

        val candle1 = candleAt(Instant.parse("2025-11-01T00:00:00Z"))
        val candle2 = candleAt(Instant.parse("2025-11-02T23:59:00Z"))
        val candle3 = candleAt(Instant.parse("2025-11-03T00:00:00Z"))

        val filtered = DateRangeFilter.filter(sequenceOf(candle1, candle2, candle3), range, timezone).toList()
        assertEquals(listOf(candle1, candle2), filtered)
    }

    @Test
    fun `timezone boundary uses ny local date`() {
        val timezone = ZoneId.of("America/New_York")
        val range = DateRange(LocalDate.of(2025, 10, 31), LocalDate.of(2025, 10, 31))

        val candle = candleAt(Instant.parse("2025-11-01T00:30:00Z"))
        val filtered = DateRangeFilter.filter(sequenceOf(candle), range, timezone).toList()
        assertEquals(1, filtered.size)

        val excluded = DateRangeFilter.filter(
            sequenceOf(candle),
            DateRange(LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 1)),
            timezone
        ).toList()
        assertEquals(0, excluded.size)
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
}
