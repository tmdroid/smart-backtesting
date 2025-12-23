package org.example.candles.engine.trade

import java.time.Instant


data class TradeResult(
    val strategyId: String,
    val trade: Trade,
    val outcome: TradeOutcome,
    val exitTime: Instant?,
    val exitPrice: Double?,
    val pnlPoints: Double
)
