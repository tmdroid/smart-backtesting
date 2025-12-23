package org.example.candles.aggregation

import java.time.Instant
import org.example.candles.domain.Timeframe
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationNonStandardTest {
    @Test
    fun `aggregates 1m to 7m`() {
        val source = sequence {
            for (i in 0 until 14) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("7m")).toList()
        assertEquals(2, aggregated.size)
        val first = aggregated[0]
        assertEquals(Instant.EPOCH, first.start)
        assertEquals(Instant.EPOCH.plusSeconds(420), first.endExclusive)
        assertEquals(0.0, first.open)
        assertEquals(6.0, first.high)
        assertEquals(0.0, first.low)
        assertEquals(6.0, first.close)
    }

    @Test
    fun `aggregates 1m to 90m`() {
        val source = sequence {
            for (i in 0 until 180) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("90m")).toList()
        assertEquals(2, aggregated.size)
        val first = aggregated[0]
        assertEquals(Instant.EPOCH, first.start)
        assertEquals(Instant.EPOCH.plusSeconds(5400), first.endExclusive)
        assertEquals(0.0, first.open)
        assertEquals(89.0, first.high)
        assertEquals(0.0, first.low)
        assertEquals(89.0, first.close)
    }
}
