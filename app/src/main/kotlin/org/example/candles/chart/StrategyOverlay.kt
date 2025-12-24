package org.example.candles.chart

import org.example.candles.engine.strategy.Direction


data class StrategyOverlay(
    val rangeBox: RangeBox?,
    val riskRewardBox: RiskRewardBox?
)

data class RangeBox(
    val startIndexInclusive: Int,
    val endIndexInclusive: Int,
    val high: Double,
    val low: Double
)

data class RiskRewardBox(
    val startIndexInclusive: Int,
    val endIndexInclusive: Int,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val direction: Direction
)
