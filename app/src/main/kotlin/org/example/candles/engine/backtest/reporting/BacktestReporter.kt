package org.example.candles.engine.backtest.reporting

import kotlin.math.abs
import kotlin.math.sqrt
import org.example.candles.engine.backtest.BacktestResult
import org.example.candles.engine.perf.PerformanceSummary
import org.example.candles.engine.trade.TradeResult

object BacktestReporter {
    fun printReport(result: BacktestResult) {
        println("Monthly Summary")
        println("month  trades  w/l/be  win%   netPts  exp/trade  avgRR  sharpe")
        val monthSharpes = ArrayList<Double>()
        for (rangeResult in result.rangeResults) {
            val perf = rangeResult.performance
            val metrics = computeMetrics(rangeResult.tradeResults, perf)
            if (perf.trades > 1) {
                monthSharpes.add(metrics.sharpeRatio)
            }
            val month = rangeResult.dateRange.start.toString().substring(0, 7)
            val winRatePct = metrics.winRate * 100.0
            val avgRR = metrics.averageRiskReward?.let { String.format("%.2f", it) } ?: "N/A"
            println(
                String.format(
                    "%s  %5d  %2d/%2d/%2d  %5.1f  %6.1f  %9.2f  %5s  %6.2f",
                    month,
                    perf.trades,
                    perf.wins,
                    perf.losses,
                    perf.breakevens,
                    winRatePct,
                    perf.netPoints,
                    metrics.expectancyPerTrade,
                    avgRR,
                    metrics.sharpeRatio
                )
            )
        }

        val overall = result.overallPerformance
        val overallMetrics = computeMetrics(result.rangeResults.flatMap { it.tradeResults }, overall)
        val avgMonthlySharpe = if (monthSharpes.isNotEmpty()) {
            monthSharpes.average()
        } else {
            0.0
        }
        println()
        println("Overall Summary")
        println(
            String.format(
                "Trades: %d  Wins/Losses/BE: %d/%d/%d  Win%%: %.1f  NetPts: %.1f",
                overall.trades,
                overall.wins,
                overall.losses,
                overall.breakevens,
                overallMetrics.winRate * 100.0,
                overall.netPoints
            )
        )
        val overallAvgRR = overallMetrics.averageRiskReward?.let { String.format("%.2f", it) } ?: "N/A"
        println(
            String.format(
                "Expectancy/Trade: %.2f  Avg RR: %s  Avg Monthly Sharpe: %.2f",
                overallMetrics.expectancyPerTrade,
                overallAvgRR,
                avgMonthlySharpe
            )
        )
    }

    fun printJson(result: BacktestResult) {
        val sb = StringBuilder()
        sb.append("{\"ranges\":[")
        result.rangeResults.forEachIndexed { index, rangeResult ->
            if (index > 0) sb.append(",")
            val perf = rangeResult.performance
            val metrics = computeMetrics(rangeResult.tradeResults, perf)
            sb.append("{")
            sb.append("\"start\":\"").append(rangeResult.dateRange.start).append("\",")
            sb.append("\"end\":\"").append(rangeResult.dateRange.endInclusive).append("\",")
            sb.append("\"trades\":").append(perf.trades).append(",")
            sb.append("\"wins\":").append(perf.wins).append(",")
            sb.append("\"losses\":").append(perf.losses).append(",")
            sb.append("\"breakevens\":").append(perf.breakevens).append(",")
            sb.append("\"netPoints\":").append(perf.netPoints).append(",")
            sb.append("\"winRate\":").append(metrics.winRate).append(",")
            sb.append("\"expectancyPerTrade\":").append(metrics.expectancyPerTrade).append(",")
            sb.append("\"averageRiskReward\":").append(metrics.averageRiskReward ?: "null").append(",")
            sb.append("\"sharpeRatio\":").append(metrics.sharpeRatio)
            sb.append("}")
        }
        sb.append("],\"overall\":{")
        val overall = result.overallPerformance
        val overallMetrics = computeMetrics(result.rangeResults.flatMap { it.tradeResults }, overall)
        sb.append("\"trades\":").append(overall.trades).append(",")
        sb.append("\"wins\":").append(overall.wins).append(",")
        sb.append("\"losses\":").append(overall.losses).append(",")
        sb.append("\"breakevens\":").append(overall.breakevens).append(",")
        sb.append("\"netPoints\":").append(overall.netPoints).append(",")
        sb.append("\"winRate\":").append(overallMetrics.winRate).append(",")
        sb.append("\"expectancyPerTrade\":").append(overallMetrics.expectancyPerTrade).append(",")
        sb.append("\"averageRiskReward\":").append(overallMetrics.averageRiskReward ?: "null").append(",")
        sb.append("\"sharpeRatio\":").append(overallMetrics.sharpeRatio)
        sb.append("}}")
        println(sb.toString())
    }

    private data class BacktestMetrics(
        val winRate: Double,
        val expectancyPerTrade: Double,
        val averageRiskReward: Double?,
        val sharpeRatio: Double
    )

    private fun computeMetrics(trades: List<TradeResult>, perf: PerformanceSummary): BacktestMetrics {
        val winLossTotal = perf.wins + perf.losses
        val winRate = if (winLossTotal > 0) perf.wins.toDouble() / winLossTotal else 0.0
        val expectancy = if (perf.trades > 0) perf.netPoints / perf.trades else 0.0

        val wins = trades.map { it.pnlPoints }.filter { it > 0.0 }
        val losses = trades.map { it.pnlPoints }.filter { it < 0.0 }.map { abs(it) }
        val avgWin = if (wins.isNotEmpty()) wins.average() else 0.0
        val avgLoss = if (losses.isNotEmpty()) losses.average() else 0.0
        val avgRR = if (avgLoss > 0.0 && avgWin > 0.0) avgWin / avgLoss else null

        val sharpe = computeSharpe(trades.map { it.pnlPoints })
        return BacktestMetrics(winRate, expectancy, avgRR, sharpe)
    }

    private fun computeSharpe(pnls: List<Double>): Double {
        if (pnls.size < 2) return 0.0
        val mean = pnls.average()
        var variance = 0.0
        for (pnl in pnls) {
            val diff = pnl - mean
            variance += diff * diff
        }
        variance /= (pnls.size - 1).toDouble()
        val stddev = sqrt(variance)
        if (stddev == 0.0) return 0.0
        return (mean / stddev) * sqrt(pnls.size.toDouble())
    }
}
