package org.example.candles.engine.range

import org.example.candles.domain.Timeframe

data class RangeDefinition(
    val timeframe: Timeframe,
    val sessionTime: TradingSessionTime,
    val priceMode: RangePriceMode = RangePriceMode.HIGH_LOW
)

enum class RangePriceMode {
    HIGH_LOW
}
