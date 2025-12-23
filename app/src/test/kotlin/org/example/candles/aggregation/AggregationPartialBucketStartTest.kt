package org.example.candles.aggregation

import java.time.Instant
import org.example.candles.domain.Timeframe
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationPartialBucketStartTest {
    @Test
    fun `keeps bucket start even if first candle is later`() {
        val source = sequence {
            yield(simpleCandleAtMinute(2, 2.0))
            yield(simpleCandleAtMinute(3, 3.0))
            yield(simpleCandleAtMinute(4, 4.0))
            yield(simpleCandleAtMinute(5, 5.0))
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m")).toList()
        val first = aggregated[0]
        assertEquals(Instant.EPOCH, first.start)
        assertEquals(Instant.EPOCH.plusSeconds(300), first.endExclusive)
        assertEquals(2.0, first.open)
        assertEquals(4.0, first.close)
    }
}
