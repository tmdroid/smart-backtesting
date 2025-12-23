package org.example.candles.engine.range

import java.time.Instant
import org.example.candles.domain.Timeframe

data class Range(
    val startTime: Instant,
    val endTime: Instant,
    val high: Double,
    val low: Double,
    val timeframe: Timeframe
)
