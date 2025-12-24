package org.example.candles.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.example.candles.domain.Timeframe
import org.example.candles.engine.range.RangeDefinition
import org.example.candles.engine.range.TradingSessionTime
import org.example.candles.engine.strategy.BreakoutSignal
import org.example.candles.engine.strategy.RangeBreakoutStrategy
import org.example.candles.engine.strategy.TradeClosed
import org.example.candles.engine.strategy.TradeParameters
import org.example.candles.engine.trade.TradeOutcome
import org.example.candles.test.candleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RangeBreakoutStrategyShortIntrabarTest {
    private val timeframe = Timeframe.parse("1m")
    private val sessionZone = ZoneId.of("UTC")
    private val sessionStart = LocalTime.of(9, 0)
    private val sessionEnd = LocalTime.of(9, 2)

    @Test
    fun `short sl and tp crossed in same candle yields stop loss`() {
        val strategy = strategy(stopLoss = 40.0, takeProfit = 60.0, breakEven = null)
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 100.0, 105.0, 95.0, 100.0, 1, base)
        val candle1 = candleAtMinute(1, 101.0, 106.0, 96.0, 101.0, 1, base)
        val breakout = candleAtMinute(2, 95.0, 100.0, 80.0, 90.0, 1, base)
        val bothHit = candleAtMinute(3, 95.0, 150.0, 30.0, 90.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(bothHit))

        assertTrue(events.filterIsInstance<BreakoutSignal>().isNotEmpty())
        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.STOP_LOSS, tradeClosed.result.outcome)
    }

    @Test
    fun `short tp and be trigger crossed in same candle yields take profit`() {
        val strategy = strategy(stopLoss = 40.0, takeProfit = 60.0, breakEven = 25.0)
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 100.0, 105.0, 95.0, 100.0, 1, base)
        val candle1 = candleAtMinute(1, 101.0, 106.0, 96.0, 101.0, 1, base)
        val breakout = candleAtMinute(2, 95.0, 100.0, 80.0, 90.0, 1, base)
        val tpAndBe = candleAtMinute(3, 95.0, 120.0, 20.0, 90.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(tpAndBe))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.TAKE_PROFIT, tradeClosed.result.outcome)
    }

    @Test
    fun `short be trigger arms then entry stop hit yields break even`() {
        val strategy = strategy(stopLoss = 40.0, takeProfit = 60.0, breakEven = 25.0)
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 100.0, 105.0, 95.0, 100.0, 1, base)
        val candle1 = candleAtMinute(1, 101.0, 106.0, 96.0, 101.0, 1, base)
        val breakout = candleAtMinute(2, 95.0, 100.0, 80.0, 90.0, 1, base)
        val beArm = candleAtMinute(3, 95.0, 120.0, 60.0, 90.0, 1, base)
        val beStop = candleAtMinute(4, 95.0, 95.0, 70.0, 90.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(beArm))
        events.addAll(strategy.onCandle(beStop))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.BREAK_EVEN, tradeClosed.result.outcome)
        assertNotNull(tradeClosed.result.exitPrice)
        assertEquals(tradeClosed.result.trade.entryPrice, tradeClosed.result.exitPrice)
    }

    @Test
    fun `short sl and be trigger crossed in same candle yields stop loss`() {
        val strategy = strategy(stopLoss = 40.0, takeProfit = 60.0, breakEven = 25.0)
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 100.0, 105.0, 95.0, 100.0, 1, base)
        val candle1 = candleAtMinute(1, 101.0, 106.0, 96.0, 101.0, 1, base)
        val breakout = candleAtMinute(2, 95.0, 100.0, 80.0, 90.0, 1, base)
        val slAndBe = candleAtMinute(3, 95.0, 140.0, 60.0, 90.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(slAndBe))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.STOP_LOSS, tradeClosed.result.outcome)
    }

    private fun strategy(stopLoss: Double, takeProfit: Double, breakEven: Double?): RangeBreakoutStrategy {
        val rangeDefinition = RangeDefinition(
            timeframe = timeframe,
            sessionTime = TradingSessionTime(sessionZone, sessionStart, sessionEnd)
        )
        val tradeParameters = TradeParameters(
            stopLossPoints = stopLoss,
            takeProfitPoints = takeProfit,
            breakEvenTriggerPoints = breakEven
        )
        return RangeBreakoutStrategy(
            id = "rb-short",
            rangeDefinition = rangeDefinition,
            tradeParameters = tradeParameters
        )
    }
}
