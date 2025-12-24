package org.example.candles.aggregation

import java.time.Instant
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregationValidationTest {
    @Test
    fun `rejects candle with high below open close`() {
        val candle = Candle(
            start = Instant.EPOCH,
            endExclusive = Instant.EPOCH.plusSeconds(60),
            open = 10.0,
            high = 9.0,
            low = 9.0,
            close = 10.0,
            volume = 1L
        )
        assertThrows(CandleValidationException::class.java) {
            aggregate(sequenceOf(candle), Timeframe.parse("1m"), Timeframe.parse("5m")).toList()
        }
    }
}
