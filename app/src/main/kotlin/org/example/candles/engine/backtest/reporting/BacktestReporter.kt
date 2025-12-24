package org.example.candles.engine.backtest.reporting

import de.vandermeer.asciitable.AsciiTable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import org.example.candles.engine.backtest.BacktestResult
import org.example.candles.engine.perf.PerformanceSummary
import org.example.candles.engine.trade.TradeResult

object BacktestReporter {
    fun printReport(result: BacktestResult) {
        val monthSharpes = ArrayList<Double>()
        val table = AsciiTable()
        table.addRule()
        table.addRow("month", "trades", "w/l/be", "win%", "netPts", "exp/trade", "avgRR", "sharpe")
        table.addRule()
        for (rangeResult in result.rangeResults) {
            val perf = rangeResult.performance
            val metrics = computeMetrics(rangeResult.tradeResults, perf)
            if (perf.trades > 1) {
                monthSharpes.add(metrics.sharpeRatio)
            }
            val month = rangeResult.dateRange.start.toString().substring(0, 7)
            val winRatePct = metrics.winRate * 100.0
            val avgRR = metrics.averageRiskReward?.let { String.format("%.2f", it) } ?: "N/A"
            table.addRow(
                month,
                perf.trades.toString(),
                "${perf.wins}/${perf.losses}/${perf.breakevens}",
                String.format("%.1f", winRatePct),
                String.format("%.1f", perf.netPoints),
                String.format("%.2f", metrics.expectancyPerTrade),
                avgRR,
                String.format("%.2f", metrics.sharpeRatio)
            )
            table.addRule()
        }

        val overall = result.overallPerformance
        val overallTrades = result.rangeResults.flatMap { it.tradeResults }
        val overallMetrics = computeMetrics(overallTrades, overall)
        val avgMonthlySharpe = if (monthSharpes.isNotEmpty()) monthSharpes.average() else 0.0

        val totalWinRatePct = overallMetrics.winRate * 100.0
        val totalAvgRR = overallMetrics.averageRiskReward?.let { String.format("%.2f", it) } ?: "N/A"
        table.addRow(
            "TOTAL",
            overall.trades.toString(),
            "${overall.wins}/${overall.losses}/${overall.breakevens}",
            String.format("%.1f", totalWinRatePct),
            String.format("%.1f", overall.netPoints),
            String.format("%.2f", overallMetrics.expectancyPerTrade),
            totalAvgRR,
            String.format("%.2f", overallMetrics.sharpeRatio)
        )
        table.addRule()

        println("Monthly Summary")
        println(table.render())

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
        println(
            String.format(
                "Expectancy/Trade: %.2f  Avg RR: %s  Avg Monthly Sharpe: %.2f",
                overallMetrics.expectancyPerTrade,
                totalAvgRR,
                avgMonthlySharpe
            )
        )
    }

    fun printJson(result: BacktestResult) {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"ranges\": [\n")
        result.rangeResults.forEachIndexed { index, rangeResult ->
            if (index > 0) sb.append(",\n")
            val perf = rangeResult.performance
            val metrics = computeMetrics(rangeResult.tradeResults, perf)
            sb.append("    {\n")
            sb.append("      \"start\": \"").append(rangeResult.dateRange.start).append("\",\n")
            sb.append("      \"end\": \"").append(rangeResult.dateRange.endInclusive).append("\",\n")
            sb.append("      \"trades\": ").append(perf.trades).append(",\n")
            sb.append("      \"wins\": ").append(perf.wins).append(",\n")
            sb.append("      \"losses\": ").append(perf.losses).append(",\n")
            sb.append("      \"breakevens\": ").append(perf.breakevens).append(",\n")
            sb.append("      \"netPoints\": ").append(format2(perf.netPoints)).append(",\n")
            sb.append("      \"winRate\": ").append(format2(metrics.winRate)).append(",\n")
            sb.append("      \"expectancyPerTrade\": ").append(format2(metrics.expectancyPerTrade)).append(",\n")
            sb.append("      \"averageRiskReward\": ")
            if (metrics.averageRiskReward == null) {
                sb.append("null,\n")
            } else {
                sb.append(format2(metrics.averageRiskReward)).append(",\n")
            }
            sb.append("      \"sharpeRatio\": ").append(format2(metrics.sharpeRatio)).append("\n")
            sb.append("    }")
        }
        sb.append("\n  ],\n")
        sb.append("  \"overall\": {\n")
        val overall = result.overallPerformance
        val overallMetrics = computeMetrics(result.rangeResults.flatMap { it.tradeResults }, overall)
        sb.append("    \"trades\": ").append(overall.trades).append(",\n")
        sb.append("    \"wins\": ").append(overall.wins).append(",\n")
        sb.append("    \"losses\": ").append(overall.losses).append(",\n")
        sb.append("    \"breakevens\": ").append(overall.breakevens).append(",\n")
        sb.append("    \"netPoints\": ").append(format2(overall.netPoints)).append(",\n")
        sb.append("    \"winRate\": ").append(format2(overallMetrics.winRate)).append(",\n")
        sb.append("    \"expectancyPerTrade\": ").append(format2(overallMetrics.expectancyPerTrade)).append(",\n")
        sb.append("    \"averageRiskReward\": ")
        if (overallMetrics.averageRiskReward == null) {
            sb.append("null,\n")
        } else {
            sb.append(format2(overallMetrics.averageRiskReward)).append(",\n")
        }
        sb.append("    \"sharpeRatio\": ").append(format2(overallMetrics.sharpeRatio)).append("\n")
        sb.append("  }\n")
        sb.append("}\n")
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

    private fun format2(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }
}
