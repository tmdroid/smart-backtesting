package org.example.candles.engine.range

import java.time.LocalTime
import java.time.ZoneId

data class TradingSessionTime(
    val timezone: ZoneId,
    val start: LocalTime,
    val end: LocalTime
) {
    init {
        require(end.isAfter(start)) { "Session end must be after start" }
    }
}
