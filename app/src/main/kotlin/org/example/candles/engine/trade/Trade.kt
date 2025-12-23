package org.example.candles.engine.trade

import java.time.Instant
import org.example.candles.engine.strategy.Direction

data class Trade(
    val direction: Direction,
    val entryTime: Instant,
    val entryPrice: Double,
    val stopPrice: Double,
    val takeProfitPrice: Double,
    val breakEvenTriggerPrice: Double? = null
)
