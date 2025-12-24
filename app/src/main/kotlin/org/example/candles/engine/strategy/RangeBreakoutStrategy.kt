package org.example.candles.engine.strategy

import java.time.Instant
import java.time.LocalDate
import org.example.candles.domain.Candle
import org.example.candles.engine.range.IntrabarFillModel
import org.example.candles.engine.range.Range
import org.example.candles.engine.range.RangeCompletenessPolicy
import org.example.candles.engine.range.RangeDefinition
import org.example.candles.engine.trade.Trade
import org.example.candles.engine.trade.TradeOutcome
import org.example.candles.engine.trade.TradeResult
import java.time.Duration

class RangeBreakoutStrategy(
    override val id: String,
    private val rangeDefinition: RangeDefinition,
    private val tradeParameters: TradeParameters,
    private val rangeCompletenessPolicy: RangeCompletenessPolicy = RangeCompletenessPolicy.STRICT,
    private val intrabarFillModel: IntrabarFillModel = IntrabarFillModel.WORST_CASE
) : Strategy {

    private enum class State {
        IDLE,
        BUILDING_RANGE,
        RANGE_COMPLETE,
        IN_TRADE,
        IN_TRADE_BE_ARMED,
        DONE
    }

    private var state: State = State.IDLE
    private var activeSessionDate: LocalDate? = null

    private var rangeStart: Instant? = null
    private var rangeEnd: Instant? = null
    private var rangeHigh: Double? = null
    private var rangeLow: Double? = null
    private var expectedNextStart: Instant? = null
    private var missingDetected: Boolean = false
    private var seenAnyInRange: Boolean = false

    private var activeRange: Range? = null
    private var activeTrade: Trade? = null

    override fun onCandle(candle: Candle): List<StrategyEvent> {
        val events = mutableListOf<StrategyEvent>()
        val candleSessionDate = candle.start.atZone(rangeDefinition.sessionTime.timezone).toLocalDate()

        if (state == State.IN_TRADE || state == State.IN_TRADE_BE_ARMED) {
            evaluateTrade(candle, events)
            return events
        }

        if (activeSessionDate != null && activeSessionDate != candleSessionDate) {
            if (state == State.BUILDING_RANGE) {
                finalizeRange(events, candle.start)
            }
            if (state == State.RANGE_COMPLETE) {
                state = State.DONE
            }
            resetForNewSession(candleSessionDate)
        }

        if (state == State.DONE) {
            if (activeSessionDate != candleSessionDate) {
                resetForNewSession(candleSessionDate)
            } else {
                return events
            }
        }

        if (activeSessionDate == null) {
            activeSessionDate = candleSessionDate
        }

        if (state == State.IDLE) {
            if (isInRangeWindow(candle, activeSessionDate!!)) {
                startRange(candle, activeSessionDate!!)
            }
            return events
        }

        if (state == State.BUILDING_RANGE) {
            val sessionDate = activeSessionDate ?: candleSessionDate
            if (isInRangeWindow(candle, sessionDate)) {
                updateRange(candle)
                return events
            }

            val rangeEndInstant = rangeEnd
            if (rangeEndInstant != null && !candle.start.isBefore(rangeEndInstant)) {
                finalizeRange(events, candle.start)
                if (state == State.RANGE_COMPLETE) {
                    evaluateBreakout(candle, events)
                }
            }
            return events
        }

        if (state == State.RANGE_COMPLETE) {
            evaluateBreakout(candle, events)
        }

        return events
    }

    override fun flush(): List<StrategyEvent> {
        val events = mutableListOf<StrategyEvent>()
        if (state == State.IN_TRADE || state == State.IN_TRADE_BE_ARMED) {
            val trade = activeTrade
            if (trade != null) {
                val result = TradeResult(
                    strategyId = id,
                    trade = trade,
                    outcome = TradeOutcome.UNRESOLVED,
                    exitTime = null,
                    exitPrice = null,
                    pnlPoints = 0.0
                )
                events.add(TradeClosed(time = trade.entryTime, strategyId = id, result = result))
                state = State.DONE
            }
        }
        return events
    }

    private fun startRange(candle: Candle, sessionDate: LocalDate) {
        val rangeStartInstant = sessionDate.atTime(rangeDefinition.sessionTime.start)
            .atZone(rangeDefinition.sessionTime.timezone)
            .toInstant()
        val rangeEndInstant = sessionDate.atTime(rangeDefinition.sessionTime.end)
            .atZone(rangeDefinition.sessionTime.timezone)
            .toInstant()

        rangeStart = rangeStartInstant
        rangeEnd = rangeEndInstant
        rangeHigh = candle.high
        rangeLow = candle.low
        expectedNextStart = rangeStartInstant
        missingDetected = false
        seenAnyInRange = true

        if (candle.start != expectedNextStart) {
            missingDetected = true
        }
        expectedNextStart = candle.start.plusMillis(rangeDefinition.timeframe.millis)
        state = State.BUILDING_RANGE
    }

    private fun updateRange(candle: Candle) {
        val high = rangeHigh
        val low = rangeLow
        if (high == null || low == null) {
            rangeHigh = candle.high
            rangeLow = candle.low
        } else {
            rangeHigh = maxOf(high, candle.high)
            rangeLow = minOf(low, candle.low)
        }
        val expected = expectedNextStart
        if (expected != null && candle.start != expected) {
            missingDetected = true
        }
        expectedNextStart = candle.start.plusMillis(rangeDefinition.timeframe.millis)
        seenAnyInRange = true
    }

    private fun finalizeRange(events: MutableList<StrategyEvent>, eventTime: Instant) {
        val rangeStartInstant = rangeStart
        val rangeEndInstant = rangeEnd
        val high = rangeHigh
        val low = rangeLow

        if (rangeStartInstant == null || rangeEndInstant == null || high == null || low == null || !seenAnyInRange) {
            events.add(
                RangeInvalid(
                    time = eventTime,
                    strategyId = id,
                    reason = RangeInvalidReason.NO_CANDLES
                )
            )
            state = State.DONE
            return
        }

        val rangeDuration = Duration.between(rangeStartInstant, rangeEndInstant)
        val timeframeMillis = rangeDefinition.timeframe.millis
        val divisible = rangeDuration.toMillis() % timeframeMillis == 0L
        var missing = false

        if (divisible) {
            val expected = expectedNextStart
            if (expected == null || expected != rangeEndInstant) {
                missing = true
            }
            if (missingDetected) {
                missing = true
            }
        }

        if (rangeCompletenessPolicy == RangeCompletenessPolicy.STRICT && missing) {
            events.add(
                RangeInvalid(
                    time = rangeEndInstant,
                    strategyId = id,
                    reason = RangeInvalidReason.MISSING_CANDLES
                )
            )
            state = State.DONE
            return
        }

        val range = Range(
            startTime = rangeStartInstant,
            endTime = rangeEndInstant,
            high = high,
            low = low,
            timeframe = rangeDefinition.timeframe
        )
        activeRange = range
        events.add(RangeBuilt(time = rangeEndInstant, strategyId = id, range = range))
        state = State.RANGE_COMPLETE
    }

    private fun evaluateBreakout(candle: Candle, events: MutableList<StrategyEvent>) {
        val range = activeRange ?: return
        val close = candle.close
        val direction = when {
            close > range.high -> Direction.LONG
            close < range.low -> Direction.SHORT
            else -> null
        }
        if (direction == null) {
            return
        }

        val entryPrice = close
        val entryTime = candle.endExclusive
        val stopPrice = if (direction == Direction.LONG) {
            entryPrice - tradeParameters.stopLossPoints
        } else {
            entryPrice + tradeParameters.stopLossPoints
        }
        val takeProfitPrice = if (direction == Direction.LONG) {
            entryPrice + tradeParameters.takeProfitPoints
        } else {
            entryPrice - tradeParameters.takeProfitPoints
        }
        val breakEvenTriggerPrice = tradeParameters.breakEvenTriggerPoints?.let {
            if (direction == Direction.LONG) entryPrice + it else entryPrice - it
        }

        val trade = Trade(
            direction = direction,
            entryTime = entryTime,
            entryPrice = entryPrice,
            stopPrice = stopPrice,
            takeProfitPrice = takeProfitPrice,
            breakEvenTriggerPrice = breakEvenTriggerPrice
        )
        activeTrade = trade
        events.add(
            BreakoutSignal(
                time = entryTime,
                strategyId = id,
                direction = direction,
                signalCandle = candle,
                breakoutPrice = entryPrice,
                range = range
            )
        )
        events.add(TradeOpened(time = entryTime, strategyId = id, trade = trade))
        state = State.IN_TRADE
    }

    private fun evaluateTrade(candle: Candle, events: MutableList<StrategyEvent>) {
        val trade = activeTrade ?: return

        if (intrabarFillModel != IntrabarFillModel.WORST_CASE) {
            return
        }

        if (state == State.IN_TRADE) {
            val stopHit = isStopHit(trade, candle)
            if (stopHit) {
                closeTrade(candle, TradeOutcome.STOP_LOSS, trade.stopPrice, events)
                return
            }

            val tpHit = isTakeProfitHit(trade, candle)
            if (tpHit) {
                closeTrade(candle, TradeOutcome.TAKE_PROFIT, trade.takeProfitPrice, events)
                return
            }

            val beTrigger = isBreakEvenTriggerHit(trade, candle)
            if (beTrigger) {
                state = State.IN_TRADE_BE_ARMED
            }
            return
        }

        if (state == State.IN_TRADE_BE_ARMED) {
            val beStopHit = isBreakEvenStopHit(trade, candle)
            if (beStopHit) {
                closeTrade(candle, TradeOutcome.BREAK_EVEN, trade.entryPrice, events)
                return
            }

            val tpHit = isTakeProfitHit(trade, candle)
            if (tpHit) {
                closeTrade(candle, TradeOutcome.TAKE_PROFIT, trade.takeProfitPrice, events)
            }
        }
    }

    private fun closeTrade(
        candle: Candle,
        outcome: TradeOutcome,
        exitPrice: Double,
        events: MutableList<StrategyEvent>
    ) {
        val trade = activeTrade ?: return
        val exitTime = candle.endExclusive
        val pnlPoints = when (trade.direction) {
            Direction.LONG -> exitPrice - trade.entryPrice
            Direction.SHORT -> trade.entryPrice - exitPrice
        }
        val result = TradeResult(
            strategyId = id,
            trade = trade,
            outcome = outcome,
            exitTime = exitTime,
            exitPrice = exitPrice,
            pnlPoints = pnlPoints
        )
        events.add(TradeClosed(time = exitTime, strategyId = id, result = result))
        state = State.DONE
    }

    private fun isInRangeWindow(candle: Candle, sessionDate: LocalDate): Boolean {
        val sessionZone = rangeDefinition.sessionTime.timezone
        val candleTime = candle.start.atZone(sessionZone)
        val rangeStartTime = sessionDate.atTime(rangeDefinition.sessionTime.start)
        val rangeEndTime = sessionDate.atTime(rangeDefinition.sessionTime.end)
        val localTime = candleTime.toLocalTime()
        return !localTime.isBefore(rangeStartTime.toLocalTime()) && localTime.isBefore(rangeEndTime.toLocalTime())
    }

    private fun isStopHit(trade: Trade, candle: Candle): Boolean {
        return if (trade.direction == Direction.LONG) {
            candle.low <= trade.stopPrice
        } else {
            candle.high >= trade.stopPrice
        }
    }

    private fun isTakeProfitHit(trade: Trade, candle: Candle): Boolean {
        return if (trade.direction == Direction.LONG) {
            candle.high >= trade.takeProfitPrice
        } else {
            candle.low <= trade.takeProfitPrice
        }
    }

    private fun isBreakEvenTriggerHit(trade: Trade, candle: Candle): Boolean {
        val trigger = trade.breakEvenTriggerPrice ?: return false
        return if (trade.direction == Direction.LONG) {
            candle.high >= trigger
        } else {
            candle.low <= trigger
        }
    }

    private fun isBreakEvenStopHit(trade: Trade, candle: Candle): Boolean {
        return if (trade.direction == Direction.LONG) {
            candle.low <= trade.entryPrice
        } else {
            candle.high >= trade.entryPrice
        }
    }

    private fun resetForNewSession(newSessionDate: LocalDate) {
        state = State.IDLE
        activeSessionDate = newSessionDate
        rangeStart = null
        rangeEnd = null
        rangeHigh = null
        rangeLow = null
        expectedNextStart = null
        missingDetected = false
        seenAnyInRange = false
        activeRange = null
        activeTrade = null
    }
}

data class TradeParameters(
    val stopLossPoints: Double,
    val takeProfitPoints: Double,
    val breakEvenTriggerPoints: Double? = null
)
