package org.example.candles.aggregation

import java.time.Instant
import org.example.candles.domain.Timeframe
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationLargeDatasetTest {
    @Test
    fun `aggregates 100k candles lazily`() {
        val total = 100_000
        val source = sequence {
            for (i in 0 until total) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val target = Timeframe.parse("5m")
        val aggregated = aggregate(source, Timeframe.parse("1m"), target).toList()
        val expectedCount = total / 5
        assertEquals(expectedCount, aggregated.size)
        val first = aggregated.first()
        assertEquals(Instant.EPOCH, first.start)
        assertEquals(Instant.EPOCH.plusSeconds(300), first.endExclusive)
        assertEquals(0.0, first.open)
        assertEquals(4.0, first.close)

        val last = aggregated.last()
        val lastStart = Instant.EPOCH.plusSeconds((expectedCount.toLong() - 1L) * 300L)
        assertEquals(lastStart, last.start)
        assertEquals(lastStart.plusSeconds(300), last.endExclusive)
        assertEquals(99_995.0, last.open)
        assertEquals(99_999.0, last.close)
    }
}
