package org.example.candles.engine.backtest

import java.time.ZoneId
import org.example.candles.domain.Candle

object DateRangeFilter {
    fun filter(candles: Sequence<Candle>, dateRange: DateRange, timezone: ZoneId): Sequence<Candle> {
        return sequence {
            for (candle in candles) {
                val date = candle.start.atZone(timezone).toLocalDate()
                if (!date.isBefore(dateRange.start) && !date.isAfter(dateRange.endInclusive)) {
                    yield(candle)
                }
            }
        }
    }
}
