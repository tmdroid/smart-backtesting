package org.example.candles.domain

import java.time.Instant

data class Candle(
    val start: Instant,
    val endExclusive: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)
