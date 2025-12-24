package org.example.candles.engine.backtest

import org.example.candles.engine.perf.PerformanceSummary
import org.example.candles.engine.trade.TradeResult


data class RangeBacktestResult(
    val dateRange: DateRange,
    val performance: PerformanceSummary,
    val tradeResults: List<TradeResult>
)

data class BacktestResult(
    val rangeResults: List<RangeBacktestResult>,
    val overallPerformance: PerformanceSummary
)
