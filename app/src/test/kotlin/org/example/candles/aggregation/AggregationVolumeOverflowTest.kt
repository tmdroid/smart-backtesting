package org.example.candles.aggregation

import org.example.candles.domain.Timeframe
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregationVolumeOverflowTest {
    @Test
    fun `rejects volume overflow while aggregating`() {
        val source = sequence {
            yield(simpleCandleAtMinute(0, 1.0, volume = Long.MAX_VALUE))
            yield(simpleCandleAtMinute(1, 1.0, volume = 1L))
        }
        assertThrows(CandleValidationException::class.java) {
            aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m")).toList()
        }
    }
}
