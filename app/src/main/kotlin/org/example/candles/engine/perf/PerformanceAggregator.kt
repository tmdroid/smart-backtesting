package org.example.candles.engine.perf

import org.example.candles.engine.trade.TradeOutcome
import org.example.candles.engine.trade.TradeResult

class PerformanceAggregator {
    fun summarize(results: Sequence<TradeResult>): List<PerformanceSummary> {
        val byStrategy = results.groupBy { it.strategyId }
        return byStrategy.map { (strategyId, trades) ->
            val wins = trades.count { it.outcome == TradeOutcome.TAKE_PROFIT }
            val losses = trades.count { it.outcome == TradeOutcome.STOP_LOSS }
            val breakevens = trades.count { it.outcome == TradeOutcome.BREAK_EVEN }
            val netPoints = trades.sumOf { it.pnlPoints }
            PerformanceSummary(
                strategyId = strategyId,
                trades = trades.size,
                wins = wins,
                losses = losses,
                breakevens = breakevens,
                netPoints = netPoints
            )
        }
    }
}

data class PerformanceSummary(
    val strategyId: String,
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val breakevens: Int,
    val netPoints: Double
)
