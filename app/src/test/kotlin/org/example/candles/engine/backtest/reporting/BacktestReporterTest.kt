package org.example.candles.engine.backtest.reporting

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Instant
import java.time.LocalDate
import org.example.candles.engine.backtest.BacktestResult
import org.example.candles.engine.backtest.DateRange
import org.example.candles.engine.backtest.RangeBacktestResult
import org.example.candles.engine.perf.PerformanceSummary
import org.example.candles.engine.strategy.Direction
import org.example.candles.engine.trade.Trade
import org.example.candles.engine.trade.TradeOutcome
import org.example.candles.engine.trade.TradeResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BacktestReporterTest {
    @Test
    fun `json report includes correct win rate net points and expectancy`() {
        val result = buildResult()
        val json = captureStdout { BacktestReporter.printJson(result) }

        assertTrue(json.contains("\"trades\": 4"))
        assertTrue(json.contains("\"wins\": 2"))
        assertTrue(json.contains("\"losses\": 1"))
        assertTrue(json.contains("\"breakevens\": 1"))
        assertTrue(json.contains("\"netPoints\": 80.00"))
        assertTrue(json.contains("\"winRate\": 0.67"))
        assertTrue(json.contains("\"expectancyPerTrade\": 20.00"))
    }

    @Test
    fun `json report includes average risk reward and sharpe ratio`() {
        val result = buildResult()
        val json = captureStdout { BacktestReporter.printJson(result) }

        assertTrue(json.contains("\"averageRiskReward\": 1.50"))
        assertTrue(json.contains("\"sharpeRatio\": 0.82"))
    }

    private fun buildResult(): BacktestResult {
        val trades = listOf(
            tradeResult(60.0, TradeOutcome.TAKE_PROFIT),
            tradeResult(60.0, TradeOutcome.TAKE_PROFIT),
            tradeResult(-40.0, TradeOutcome.STOP_LOSS),
            tradeResult(0.0, TradeOutcome.BREAK_EVEN)
        )
        val perf = PerformanceSummary(
            strategyId = "ALL",
            trades = 4,
            wins = 2,
            losses = 1,
            breakevens = 1,
            netPoints = 80.0
        )
        val range = DateRange(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30))
        val rangeResult = RangeBacktestResult(range, perf, trades)
        return BacktestResult(rangeResults = listOf(rangeResult), overallPerformance = perf)
    }

    private fun tradeResult(pnl: Double, outcome: TradeOutcome): TradeResult {
        val entryTime = Instant.parse("2025-06-01T00:00:00Z")
        val trade = Trade(
            direction = Direction.LONG,
            entryTime = entryTime,
            entryPrice = 100.0,
            stopPrice = 60.0,
            takeProfitPrice = 160.0,
            breakEvenTriggerPrice = null
        )
        return TradeResult(
            strategyId = "s1",
            trade = trade,
            outcome = outcome,
            exitTime = entryTime.plusSeconds(60),
            exitPrice = 100.0 + pnl,
            pnlPoints = pnl
        )
    }

    private fun captureStdout(block: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }
}
