package org.example.candles.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.example.candles.domain.Timeframe
import org.example.candles.engine.range.RangeCompletenessPolicy
import org.example.candles.engine.range.RangeDefinition
import org.example.candles.engine.range.TradingSessionTime
import org.example.candles.engine.strategy.BreakoutSignal
import org.example.candles.engine.strategy.RangeBreakoutStrategy
import org.example.candles.engine.strategy.RangeBuilt
import org.example.candles.engine.strategy.RangeInvalid
import org.example.candles.engine.strategy.RangeInvalidReason
import org.example.candles.engine.strategy.TradeClosed
import org.example.candles.engine.strategy.TradeOpened
import org.example.candles.engine.strategy.TradeParameters
import org.example.candles.engine.trade.TradeOutcome
import org.example.candles.test.candleAtMinute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RangeBreakoutStrategyTest {
    private val timeframe = Timeframe.parse("1m")
    private val sessionZone = ZoneId.of("UTC")
    private val sessionStart = LocalTime.of(9, 0)
    private val sessionEnd = LocalTime.of(9, 3)

    @Test
    fun `range window inclusion is start inclusive end exclusive`() {
        val strategy = strategy(rangePolicy = RangeCompletenessPolicy.LENIENT, sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 11.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 11.0, 12.0, 10.0, 11.0, 1, base)
        val candle2 = candleAtMinute(2, 20.0, 20.0, 20.0, 20.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(candle2))

        val rangeBuilt = events.filterIsInstance<RangeBuilt>().single()
        assertEquals(12.0, rangeBuilt.range.high)
        assertEquals(9.0, rangeBuilt.range.low)
    }

    @Test
    fun `strict completeness invalidates missing candles`() {
        val strategy = strategy(rangePolicy = RangeCompletenessPolicy.STRICT)
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 10.0, 10.0, 1, base)
        val candle2 = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)
        val candle3 = candleAtMinute(3, 12.0, 12.0, 12.0, 12.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle2))
        events.addAll(strategy.onCandle(candle3))

        val invalid = events.filterIsInstance<RangeInvalid>().single()
        assertEquals(RangeInvalidReason.MISSING_CANDLES, invalid.reason)
    }

    @Test
    fun `lenient completeness builds range with missing candles`() {
        val strategy = strategy(rangePolicy = RangeCompletenessPolicy.LENIENT)
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 10.0, 10.0, 1, base)
        val candle2 = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)
        val candle3 = candleAtMinute(3, 12.0, 12.0, 12.0, 12.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle2))
        events.addAll(strategy.onCandle(candle3))

        val rangeBuilt = events.filterIsInstance<RangeBuilt>().single()
        assertEquals(11.0, rangeBuilt.range.high)
        assertEquals(10.0, rangeBuilt.range.low)
    }

    @Test
    fun `breakout requires close strictly outside range`() {
        val strategy = strategy(rangePolicy = RangeCompletenessPolicy.LENIENT, sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 9.0, 9.0, 5.0, 9.0, 1, base)
        val candle2 = candleAtMinute(2, 10.0, 10.0, 10.0, 10.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(candle2))

        assertTrue(events.filterIsInstance<BreakoutSignal>().isEmpty())
        assertTrue(events.filterIsInstance<TradeOpened>().isEmpty())
    }

    @Test
    fun `wick based stop loss triggers even when close does not`() {
        val strategy = strategy(sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 11.0, 11.0, 10.5, 11.0, 1, base)
        val breakout = candleAtMinute(2, 12.0, 12.0, 12.0, 12.0, 1, base)
        val stopHit = candleAtMinute(3, 12.0, 13.0, 10.0, 12.5, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(stopHit))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.STOP_LOSS, tradeClosed.result.outcome)
    }

    @Test
    fun `worst case intrabar order prioritizes stop loss`() {
        val strategy = strategy(sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 10.0, 10.0, 9.0, 10.0, 1, base)
        val breakout = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)
        val bothHit = candleAtMinute(3, 11.0, 13.0, 9.0, 12.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(bothHit))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.STOP_LOSS, tradeClosed.result.outcome)
    }

    @Test
    fun `break even trigger arms stop then resolves when entry hit`() {
        val strategy = strategy(breakEvenPoints = 1.0, takeProfitPoints = 2.0, sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 10.0, 10.0, 9.0, 10.0, 1, base)
        val breakout = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)
        val arm = candleAtMinute(3, 11.0, 12.0, 11.0, 11.5, 1, base)
        val beHit = candleAtMinute(4, 11.0, 11.2, 11.0, 11.1, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(arm))
        assertTrue(events.filterIsInstance<TradeClosed>().isEmpty())
        events.addAll(strategy.onCandle(beHit))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.BREAK_EVEN, tradeClosed.result.outcome)
    }

    @Test
    fun `stop loss wins when stop and break even trigger hit in same candle`() {
        val strategy = strategy(breakEvenPoints = 1.0, takeProfitPoints = 5.0, sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 10.0, 10.0, 9.0, 10.0, 1, base)
        val breakout = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)
        val stopAndBe = candleAtMinute(3, 11.0, 12.0, 9.5, 11.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(stopAndBe))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.STOP_LOSS, tradeClosed.result.outcome)
    }

    @Test
    fun `take profit wins when tp and break even trigger hit in same candle`() {
        val strategy = strategy(breakEvenPoints = 1.0, takeProfitPoints = 2.0, sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 10.0, 10.0, 9.0, 10.0, 1, base)
        val breakout = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)
        val tpAndBe = candleAtMinute(3, 11.0, 13.0, 10.5, 12.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(tpAndBe))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.TAKE_PROFIT, tradeClosed.result.outcome)
    }

    @Test
    fun `break even stop is checked before take profit once armed`() {
        val strategy = strategy(breakEvenPoints = 1.0, takeProfitPoints = 2.0, sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 10.0, 10.0, 9.0, 10.0, 1, base)
        val breakout = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)
        val arm = candleAtMinute(3, 11.0, 12.0, 11.0, 11.5, 1, base)
        val bothHit = candleAtMinute(4, 11.0, 13.0, 11.0, 12.5, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(arm))
        events.addAll(strategy.onCandle(bothHit))

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.BREAK_EVEN, tradeClosed.result.outcome)
    }

    @Test
    fun `session date rollover keeps open trade but blocks new range`() {
        val strategy = strategy(sessionEndOverride = LocalTime.of(9, 2))
        val baseDay1 = Instant.parse("2020-01-01T09:00:00Z")
        val baseDay2 = Instant.parse("2020-01-02T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, baseDay1)
        val candle1 = candleAtMinute(1, 10.0, 10.0, 9.0, 10.0, 1, baseDay1)
        val breakout = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, baseDay1)
        val tpHitNextDay = candleAtMinute(0, 11.0, 13.0, 11.0, 12.0, 1, baseDay2)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.onCandle(tpHitNextDay))

        val rangesBuilt = events.filterIsInstance<RangeBuilt>()
        assertEquals(1, rangesBuilt.size)
        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.TAKE_PROFIT, tradeClosed.result.outcome)
    }

    @Test
    fun `pnl is computed correctly for long and short trades`() {
        val longStrategy = strategy(sessionEndOverride = LocalTime.of(9, 2))
        val shortStrategy = strategy(sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 10.0, 10.0, 9.0, 10.0, 1, base)

        val longBreakout = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)
        val longTp = candleAtMinute(3, 11.0, 13.0, 11.0, 12.0, 1, base)

        val shortBreakout = candleAtMinute(2, 8.0, 8.0, 8.0, 8.0, 1, base)
        val shortTp = candleAtMinute(3, 8.0, 8.0, 6.0, 7.0, 1, base)

        val longEvents = mutableListOf<Any>()
        longEvents.addAll(longStrategy.onCandle(candle0))
        longEvents.addAll(longStrategy.onCandle(candle1))
        longEvents.addAll(longStrategy.onCandle(longBreakout))
        longEvents.addAll(longStrategy.onCandle(longTp))

        val longClosed = longEvents.filterIsInstance<TradeClosed>().single()
        assertEquals(1.0, longClosed.result.pnlPoints)

        val shortEvents = mutableListOf<Any>()
        shortEvents.addAll(shortStrategy.onCandle(candle0))
        shortEvents.addAll(shortStrategy.onCandle(candle1))
        shortEvents.addAll(shortStrategy.onCandle(shortBreakout))
        shortEvents.addAll(shortStrategy.onCandle(shortTp))

        val shortClosed = shortEvents.filterIsInstance<TradeClosed>().single()
        assertEquals(1.0, shortClosed.result.pnlPoints)
    }

    @Test
    fun `unresolved trade is emitted on flush`() {
        val strategy = strategy(sessionEndOverride = LocalTime.of(9, 2))
        val base = Instant.parse("2020-01-01T09:00:00Z")

        val candle0 = candleAtMinute(0, 10.0, 10.0, 9.0, 10.0, 1, base)
        val candle1 = candleAtMinute(1, 10.0, 10.0, 9.0, 10.0, 1, base)
        val breakout = candleAtMinute(2, 11.0, 11.0, 11.0, 11.0, 1, base)

        val events = mutableListOf<Any>()
        events.addAll(strategy.onCandle(candle0))
        events.addAll(strategy.onCandle(candle1))
        events.addAll(strategy.onCandle(breakout))
        events.addAll(strategy.flush())

        val tradeClosed = events.filterIsInstance<TradeClosed>().single()
        assertEquals(TradeOutcome.UNRESOLVED, tradeClosed.result.outcome)
        assertEquals(0.0, tradeClosed.result.pnlPoints)
        assertNull(tradeClosed.result.exitTime)
        assertNull(tradeClosed.result.exitPrice)
    }

    private fun strategy(
        rangePolicy: RangeCompletenessPolicy = RangeCompletenessPolicy.STRICT,
        breakEvenPoints: Double? = null,
        takeProfitPoints: Double = 1.0,
        sessionEndOverride: LocalTime? = null
    ): RangeBreakoutStrategy {
        val rangeDefinition = RangeDefinition(
            timeframe = timeframe,
            sessionTime = TradingSessionTime(sessionZone, sessionStart, sessionEndOverride ?: sessionEnd)
        )
        val tradeParameters = TradeParameters(
            stopLossPoints = 1.0,
            takeProfitPoints = takeProfitPoints,
            breakEvenTriggerPoints = breakEvenPoints
        )
        return RangeBreakoutStrategy(
            id = "rb",
            rangeDefinition = rangeDefinition,
            tradeParameters = tradeParameters,
            rangeCompletenessPolicy = rangePolicy
        )
    }
}
