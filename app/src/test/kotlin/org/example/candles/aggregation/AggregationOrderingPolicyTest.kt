package org.example.candles.aggregation

import org.example.candles.domain.Timeframe
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AggregationOrderingPolicyTest {
    @Test
    fun `rejects out of order timestamps`() {
        val source = sequence {
            yield(simpleCandleAtMinute(1, 1.0))
            yield(simpleCandleAtMinute(0, 0.0))
        }
        assertThrows(OrderingViolationException::class.java) {
            aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m")).toList()
        }
    }
}
