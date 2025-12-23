package org.example.candles.aggregation

import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationStreamingTest {
    @Test
    fun `aggregate consumes sequence only once`() {
        val candles = listOf(
            simpleCandleAtMinute(0, 0.0),
            simpleCandleAtMinute(1, 1.0),
            simpleCandleAtMinute(2, 2.0),
            simpleCandleAtMinute(3, 3.0),
            simpleCandleAtMinute(4, 4.0)
        )
        val singlePass = object : Sequence<Candle> {
            private var used = false
            override fun iterator(): Iterator<Candle> {
                if (used) {
                    throw IllegalStateException("Sequence iterated more than once")
                }
                used = true
                return candles.iterator()
            }
        }
        val aggregated = aggregate(singlePass, Timeframe.parse("1m"), Timeframe.parse("5m")).toList()
        assertEquals(1, aggregated.size)
    }
}
