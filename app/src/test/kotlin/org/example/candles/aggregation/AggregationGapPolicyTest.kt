package org.example.candles.aggregation

import java.time.Instant
import org.example.candles.domain.Timeframe
import org.example.candles.policy.AggregationPolicy
import org.example.candles.policy.GapPolicy
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationGapPolicyTest {
    @Test
    fun `keeps partial bucket when gap policy is keep`() {
        val source = sequence {
            val minutes = listOf(0, 1, 3, 4, 5, 6, 7, 8, 9)
            for (m in minutes) {
                yield(simpleCandleAtMinute(m.toLong(), m.toDouble()))
            }
        }
        val policy = AggregationPolicy(gapPolicy = GapPolicy.KEEP_PARTIAL)
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m"), policy).toList()
        assertEquals(2, aggregated.size)
        val first = aggregated[0]
        assertEquals(Instant.EPOCH, first.start)
        assertEquals(Instant.EPOCH.plusSeconds(300), first.endExclusive)
        assertEquals(0.0, first.open)
        assertEquals(4.0, first.close)
    }

    @Test
    fun `drops incomplete bucket when gap policy is drop`() {
        val source = sequence {
            val minutes = listOf(0, 1, 3, 4, 5, 6, 7, 8, 9)
            for (m in minutes) {
                yield(simpleCandleAtMinute(m.toLong(), m.toDouble()))
            }
        }
        val policy = AggregationPolicy(gapPolicy = GapPolicy.DROP_BUCKET_IF_INCOMPLETE)
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m"), policy).toList()
        assertEquals(1, aggregated.size)
        val first = aggregated[0]
        assertEquals(Instant.EPOCH.plusSeconds(300), first.start)
        assertEquals(Instant.EPOCH.plusSeconds(600), first.endExclusive)
        assertEquals(5.0, first.open)
        assertEquals(9.0, first.close)
    }
}
