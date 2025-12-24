package org.example.candles.engine.backtest

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import org.example.candles.domain.Candle
import org.example.candles.engine.strategy.Annotation
import org.example.candles.engine.strategy.Direction
import org.example.candles.engine.strategy.Strategy
import org.example.candles.engine.strategy.StrategyEvent
import org.example.candles.engine.strategy.TradeClosed
import org.example.candles.engine.trade.Trade
import org.example.candles.engine.trade.TradeOutcome
import org.example.candles.engine.trade.TradeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BacktestExecutorTest {
    @Test
    fun `strategy factories create fresh instances per range`() {
        val created = AtomicInteger(0)
        val factory = StrategyFactory {
            val id = created.incrementAndGet()
            CountingStrategy("s$id")
        }
        val run = BacktestRun(
            periods = listOf(
                CustomDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)),
                CustomDateRange(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 2))
            ),
            strategyFactories = listOf(factory),
            timezone = ZoneId.of("UTC")
        )
        val executor = BacktestExecutor { sequenceOf(candleAt("2025-01-01T00:00:00Z")) }
        executor.run(run)
        assertEquals(2, created.get())
    }

    @Test
    fun `aggregation sums per range results`() {
        val factory = StrategyFactory { FixedTradeStrategy("s1") }
        val run = BacktestRun(
            periods = listOf(
                CustomDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)),
                CustomDateRange(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 2))
            ),
            strategyFactories = listOf(factory),
            timezone = ZoneId.of("UTC")
        )
        val executor = BacktestExecutor { range ->
            val time = range.start.atStartOfDay(ZoneId.of("UTC")).toInstant()
            sequenceOf(candleAt(time.toString()))
        }
        val result = executor.run(run)

        assertEquals(2, result.rangeResults.size)
        assertEquals(2, result.overallPerformance.trades)
        assertEquals(2.0, result.overallPerformance.netPoints)
    }

    @Test
    fun `executor consumes candle sequence only once`() {
        val range = CustomDateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))
        val run = BacktestRun(
            periods = listOf(range),
            strategyFactories = listOf(StrategyFactory { NoOpStrategy("s1") }),
            timezone = ZoneId.of("UTC")
        )
        val executor = BacktestExecutor { singleUseSequence(candleAt("2025-01-01T00:00:00Z")) }
        val result = executor.run(run)
        assertEquals(0, result.overallPerformance.trades)
    }

    private fun candleAt(iso: String): Candle {
        val time = Instant.parse(iso)
        return Candle(
            start = time,
            endExclusive = time.plusSeconds(60),
            open = 1.0,
            high = 1.0,
            low = 1.0,
            close = 1.0,
            volume = 1L
        )
    }

    private fun singleUseSequence(candle: Candle): Sequence<Candle> {
        var consumed = false
        return Sequence {
            if (consumed) {
                throw IllegalStateException("Sequence consumed more than once")
            }
            consumed = true
            listOf(candle).iterator()
        }
    }

    private class CountingStrategy(override val id: String) : Strategy {
        private var count = 0
        override fun onCandle(candle: Candle): List<StrategyEvent> {
            count += 1
            return listOf(Annotation(time = candle.start, strategyId = id, message = "count=$count"))
        }
    }

    private class NoOpStrategy(override val id: String) : Strategy {
        override fun onCandle(candle: Candle): List<StrategyEvent> = emptyList()
    }

    private class FixedTradeStrategy(override val id: String) : Strategy {
        override fun onCandle(candle: Candle): List<StrategyEvent> {
            val trade = Trade(
                direction = Direction.LONG,
                entryTime = candle.start,
                entryPrice = 1.0,
                stopPrice = 0.5,
                takeProfitPrice = 2.0,
                breakEvenTriggerPrice = null
            )
            val result = TradeResult(
                strategyId = id,
                trade = trade,
                outcome = TradeOutcome.TAKE_PROFIT,
                exitTime = candle.endExclusive,
                exitPrice = 2.0,
                pnlPoints = 1.0
            )
            return listOf(TradeClosed(time = candle.endExclusive, strategyId = id, result = result))
        }
    }
}
