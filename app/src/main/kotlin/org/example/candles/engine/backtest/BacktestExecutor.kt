package org.example.candles.engine.backtest

import org.example.candles.domain.Candle
import org.example.candles.engine.perf.PerformanceAggregator
import org.example.candles.engine.perf.PerformanceSummary
import org.example.candles.engine.runner.StrategyRunner
import org.example.candles.engine.strategy.TradeClosed
import org.example.candles.engine.trade.TradeResult

class BacktestExecutor(
    private val candleSourceFactory: (DateRange) -> Sequence<Candle>
) {
    fun run(backtestRun: BacktestRun): BacktestResult {
        val ranges = backtestRun.resolveDateRanges()
        val rangeResults = mutableListOf<RangeBacktestResult>()
        val allSummaries = mutableListOf<PerformanceSummary>()

        for (range in ranges) {
            val strategies = backtestRun.strategyFactories.map { it.create() }
            val runner = StrategyRunner(strategies)
            val rawCandles = candleSourceFactory(range)
            val filtered = DateRangeFilter.filter(rawCandles, range, backtestRun.timezone)
            val events = runner.run(filtered)
            val tradeResults = events.filterIsInstance<TradeClosed>().map { it.result }.toList()
            val performance = aggregateAllStrategies(tradeResults.asSequence())
            rangeResults.add(RangeBacktestResult(range, performance, tradeResults))
            allSummaries.add(performance)
        }

        val overall = sumSummaries(allSummaries)
        return BacktestResult(rangeResults, overall)
    }

    private fun aggregateAllStrategies(results: Sequence<TradeResult>): PerformanceSummary {
        val aggregator = PerformanceAggregator()
        val normalized = results.map { it.copy(strategyId = ALL_STRATEGIES_ID) }
        return aggregator.summarize(normalized).firstOrNull()
            ?: PerformanceSummary(ALL_STRATEGIES_ID, 0, 0, 0, 0, 0.0)
    }

    private fun sumSummaries(summaries: List<PerformanceSummary>): PerformanceSummary {
        if (summaries.isEmpty()) {
            return PerformanceSummary(ALL_STRATEGIES_ID, 0, 0, 0, 0, 0.0)
        }
        val trades = summaries.sumOf { it.trades }
        val wins = summaries.sumOf { it.wins }
        val losses = summaries.sumOf { it.losses }
        val breakevens = summaries.sumOf { it.breakevens }
        val netPoints = summaries.sumOf { it.netPoints }
        return PerformanceSummary(ALL_STRATEGIES_ID, trades, wins, losses, breakevens, netPoints)
    }

    private companion object {
        const val ALL_STRATEGIES_ID = "ALL"
    }
}
