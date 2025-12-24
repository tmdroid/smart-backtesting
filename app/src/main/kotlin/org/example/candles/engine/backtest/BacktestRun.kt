package org.example.candles.engine.backtest

import java.time.ZoneId

/**
 * Overlapping or duplicate ranges are allowed and will double-count in overall totals.
 * Backtest timezone should match strategy session timezone to avoid off-by-one date selection.
 */
data class BacktestRun(
    val periods: List<Period>,
    val strategyFactories: List<StrategyFactory>,
    val timezone: ZoneId = ZoneId.of("America/New_York")
) {
    fun resolveDateRanges(): List<DateRange> {
        val ranges = periods.flatMap { it.toDateRanges() }
        return ranges.sortedWith(compareBy<DateRange>({ it.start }, { it.endInclusive }))
    }
}
