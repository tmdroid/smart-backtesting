package org.example.candles.test

import java.time.Instant
import org.example.candles.domain.Candle
import org.example.candles.domain.Timeframe

fun candleAtMinute(
    minuteOffset: Long,
    open: Double,
    high: Double,
    low: Double,
    close: Double,
    volume: Long,
    base: Instant = Instant.EPOCH,
    sourceTimeframe: Timeframe = Timeframe.parse("1m")
): Candle {
    val start = base.plusSeconds(minuteOffset * 60)
    val endExclusive = start.plusMillis(sourceTimeframe.millis)
    return Candle(
        start = start,
        endExclusive = endExclusive,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume
    )
}

fun simpleCandleAtMinute(
    minuteOffset: Long,
    value: Double,
    volume: Long = 1L,
    base: Instant = Instant.EPOCH,
    sourceTimeframe: Timeframe = Timeframe.parse("1m")
): Candle {
    return candleAtMinute(
        minuteOffset = minuteOffset,
        open = value,
        high = value,
        low = value,
        close = value,
        volume = volume,
        base = base,
        sourceTimeframe = sourceTimeframe
    )
}
