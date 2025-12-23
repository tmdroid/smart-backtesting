package org.example.candles.engine.strategy

import java.time.Instant
import org.example.candles.domain.Candle
import org.example.candles.engine.range.Range
import org.example.candles.engine.trade.Trade
import org.example.candles.engine.trade.TradeResult

sealed interface StrategyEvent {
    val time: Instant
    val strategyId: String
}

data class RangeBuilt(
    override val time: Instant,
    override val strategyId: String,
    val range: Range
) : StrategyEvent

data class RangeInvalid(
    override val time: Instant,
    override val strategyId: String,
    val reason: RangeInvalidReason
) : StrategyEvent

data class BreakoutSignal(
    override val time: Instant,
    override val strategyId: String,
    val direction: Direction,
    val signalCandle: Candle,
    val breakoutPrice: Double,
    val range: Range
) : StrategyEvent

data class TradeOpened(
    override val time: Instant,
    override val strategyId: String,
    val trade: Trade
) : StrategyEvent

data class TradeClosed(
    override val time: Instant,
    override val strategyId: String,
    val result: TradeResult
) : StrategyEvent

data class Annotation(
    override val time: Instant,
    override val strategyId: String,
    val message: String
) : StrategyEvent

enum class Direction {
    LONG,
    SHORT
}

enum class RangeInvalidReason {
    MISSING_CANDLES,
    NO_CANDLES
}
