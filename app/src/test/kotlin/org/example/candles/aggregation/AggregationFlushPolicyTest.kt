package org.example.candles.aggregation

import org.example.candles.domain.Timeframe
import org.example.candles.policy.AggregationPolicy
import org.example.candles.policy.FlushPolicy
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationFlushPolicyTest {
    @Test
    fun `emits partial bucket on flush when policy is emit`() {
        val source = sequence {
            for (i in 0 until 3) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val policy = AggregationPolicy(flushPolicy = FlushPolicy.EMIT_PARTIAL)
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m"), policy).toList()
        assertEquals(1, aggregated.size)
    }

    @Test
    fun `drops partial bucket on flush when policy is drop`() {
        val source = sequence {
            for (i in 0 until 3) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val policy = AggregationPolicy(flushPolicy = FlushPolicy.DROP_PARTIAL)
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m"), policy).toList()
        assertEquals(0, aggregated.size)
    }
}
