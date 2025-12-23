package org.example.candles.aggregation

import org.example.candles.domain.Timeframe
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregationDuplicatePolicyTest {
    @Test
    fun `rejects duplicate timestamps`() {
        val source = sequence {
            yield(simpleCandleAtMinute(0, 1.0))
            yield(simpleCandleAtMinute(0, 2.0))
        }
        assertThrows(DuplicateTimestampException::class.java) {
            aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m")).toList()
        }
    }
}
