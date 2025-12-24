package org.example.candles.aggregation

import java.time.Instant
import org.example.candles.domain.Timeframe
import org.example.candles.policy.AggregationPolicy
import org.example.candles.policy.GapPolicy
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationCountMismatchTest {
    @Test
    fun `drops bucket when count mismatch without internal gap`() {
        val source = sequence {
            yield(simpleCandleAtMinute(0, 0.0))
            yield(simpleCandleAtMinute(1, 1.0))
            yield(simpleCandleAtMinute(2, 2.0))
            yield(simpleCandleAtMinute(3, 3.0))
            yield(simpleCandleAtMinute(5, 5.0))
        }
        val policy = AggregationPolicy(gapPolicy = GapPolicy.DROP_BUCKET_IF_INCOMPLETE)
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m"), policy).toList()
        assertEquals(1, aggregated.size)
        assertEquals(Instant.EPOCH.plusSeconds(300), aggregated[0].start)
    }
}
