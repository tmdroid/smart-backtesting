package org.example.candles.aggregation

import java.time.Instant
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregationInferredTimeframeTest {
    @Test
    fun `rejects invalid inferred source timeframe`() {
        val candle = Candle(
            start = Instant.EPOCH,
            endExclusive = Instant.EPOCH.plusSeconds(30),
            open = 1.0,
            high = 1.0,
            low = 1.0,
            close = 1.0,
            volume = 1L
        )
        assertThrows(CandleValidationException::class.java) {
            aggregate(sequenceOf(candle), Timeframe.parse("5m")).toList()
        }
    }
}
