package org.example.candles.engine.backtest

import org.example.candles.engine.perf.PerformanceSummary


data class RangeBacktestResult(
    val dateRange: DateRange,
    val performance: PerformanceSummary
)

data class BacktestResult(
    val rangeResults: List<RangeBacktestResult>,
    val overallPerformance: PerformanceSummary
)
