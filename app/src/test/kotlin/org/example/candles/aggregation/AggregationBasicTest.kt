package org.example.candles.aggregation

import java.time.Instant
import org.example.candles.domain.Timeframe
import org.example.candles.test.simpleCandleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregationBasicTest {
    @Test
    fun `aggregates 1m to 5m`() {
        val source = sequence {
            for (i in 0 until 10) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("5m")).toList()
        assertEquals(2, aggregated.size)
        val first = aggregated[0]
        assertEquals(Instant.EPOCH, first.start)
        assertEquals(Instant.EPOCH.plusSeconds(300), first.endExclusive)
        assertEquals(0.0, first.open)
        assertEquals(4.0, first.high)
        assertEquals(0.0, first.low)
        assertEquals(4.0, first.close)
        assertEquals(5L, first.volume)
    }

    @Test
    fun `aggregates 1m to 3m`() {
        val source = sequence {
            for (i in 0 until 6) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("3m")).toList()
        assertEquals(2, aggregated.size)
        val first = aggregated[0]
        assertEquals(Instant.EPOCH, first.start)
        assertEquals(Instant.EPOCH.plusSeconds(180), first.endExclusive)
        assertEquals(0.0, first.open)
        assertEquals(2.0, first.close)
    }

    @Test
    fun `aggregates 1m to 15m`() {
        val source = sequence {
            for (i in 0 until 30) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("15m")).toList()
        assertEquals(2, aggregated.size)
        val first = aggregated[0]
        assertEquals(Instant.EPOCH, first.start)
        assertEquals(Instant.EPOCH.plusSeconds(900), first.endExclusive)
        assertEquals(0.0, first.open)
        assertEquals(14.0, first.high)
        assertEquals(0.0, first.low)
        assertEquals(14.0, first.close)
        assertEquals(15L, first.volume)
    }

    @Test
    fun `aggregates 1m to 1h`() {
        val source = sequence {
            for (i in 0 until 60) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("1h")).toList()
        assertEquals(1, aggregated.size)
        val candle = aggregated[0]
        assertEquals(Instant.EPOCH, candle.start)
        assertEquals(Instant.EPOCH.plusSeconds(3600), candle.endExclusive)
        assertEquals(0.0, candle.open)
        assertEquals(59.0, candle.high)
        assertEquals(0.0, candle.low)
        assertEquals(59.0, candle.close)
        assertEquals(60L, candle.volume)
    }

    @Test
    fun `aggregates 1m to 4h`() {
        val source = sequence {
            for (i in 0 until 240) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("4h")).toList()
        assertEquals(1, aggregated.size)
        val candle = aggregated[0]
        assertEquals(Instant.EPOCH, candle.start)
        assertEquals(Instant.EPOCH.plusSeconds(14_400), candle.endExclusive)
        assertEquals(0.0, candle.open)
        assertEquals(239.0, candle.high)
        assertEquals(0.0, candle.low)
        assertEquals(239.0, candle.close)
        assertEquals(240L, candle.volume)
    }

    @Test
    fun `aggregates 1m to 1d`() {
        val source = sequence {
            for (i in 0 until 1440) {
                yield(simpleCandleAtMinute(i.toLong(), i.toDouble()))
            }
        }
        val aggregated = aggregate(source, Timeframe.parse("1m"), Timeframe.parse("1d")).toList()
        assertEquals(1, aggregated.size)
        val candle = aggregated[0]
        assertEquals(Instant.EPOCH, candle.start)
        assertEquals(Instant.EPOCH.plusSeconds(86_400), candle.endExclusive)
        assertEquals(0.0, candle.open)
        assertEquals(1439.0, candle.high)
        assertEquals(0.0, candle.low)
        assertEquals(1439.0, candle.close)
        assertEquals(1440L, candle.volume)
    }
}
