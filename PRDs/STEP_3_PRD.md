# STEP 3 PRD — Strategy Engine with Pluggable Architecture

## Purpose

Build a deterministic, backtest-only strategy engine with a pluggable strategy architecture.

Step 3 introduces:
- A generic Strategy abstraction
- A StrategyRunner capable of running multiple strategies over the same candle stream
- A concrete implementation of a Range Breakout Strategy

The system must support swapping strategies, running multiple strategies per day, and aggregating performance across strategies.

This step is engine-only (no UI, no execution).

---

## Scope & Non-Goals

### In Scope
- Generic strategy plugin architecture
- Streaming candle consumption
- Strategy event emission
- Trade lifecycle & outcome evaluation
- Portfolio-style performance aggregation
- One concrete strategy: Range Breakout

### Explicit Non-Goals
- Live trading or execution
- Order book simulation
- Partial fills
- Scaling in/out
- Trailing stops
- Indicators (MA, VWAP, RSI)
- Optimization / parameter search
- UI or visualization logic

---

## Dependencies

- Step 1 aggregation module (Candle, Timeframe, aggregation)
- Java/Kotlin standard library only

---

## Core Architecture

### Strategy Interface

```kotlin
interface Strategy {
    val id: String
    fun onCandle(candle: Candle): List<StrategyEvent>
    fun flush(): List<StrategyEvent> = emptyList()
}
```

---

### StrategyEvent

```kotlin
sealed interface StrategyEvent {
    val time: Instant
    val strategyId: String
}
```

Events may include:
- RangeBuilt
- BreakoutSignal
- TradeOpened
- TradeClosed
- Annotation

---

## Trade Model

### Trade

```kotlin
data class Trade(
    val direction: Direction,
    val entryTime: Instant,
    val entryPrice: Double,
    val stopPrice: Double,
    val takeProfitPrice: Double,
    val breakEvenTriggerPrice: Double? = null
)
```

---

### TradeResult

```kotlin
data class TradeResult(
    val strategyId: String,
    val trade: Trade,
    val outcome: TradeOutcome,
    val exitTime: Instant?,
    val exitPrice: Double?,
    val pnlPoints: Double
)
```

---

### TradeOutcome

- STOP_LOSS
- TAKE_PROFIT
- BREAK_EVEN
- UNRESOLVED

---

## StrategyRunner

```kotlin
class StrategyRunner(
    val strategies: List<Strategy>
) {
    fun run(candles: Sequence<Candle>): List<StrategyEvent>
}
```

Rules:
- Each candle is fed to all strategies
- Deterministic ordering
- flush() called at end of data

---

## Performance Aggregation

### PerformanceSummary

```kotlin
data class PerformanceSummary(
    val strategyId: String,
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val breakevens: Int,
    val netPoints: Double
)
```

---

## Concrete Strategy: Range Breakout

### Concept

1. Build a price range over a fixed session window on a timeframe
2. Range is holy: no trades until completed; no trades inside range
3. Breakout occurs on candle close outside range
4. Above range → LONG, below → SHORT
5. Trade outcome determined by SL / TP / BE rules

---

## Range Domain Concepts

### TradingSessionTime
- timezone (ZoneId)
- start (LocalTime)
- end (LocalTime)

---

### RangeDefinition
- timeframe: Timeframe
- sessionTime: TradingSessionTime
- priceMode: HIGH_LOW (default)

---

### Range
- startTime: Instant
- endTime: Instant
- high: Double
- low: Double
- timeframe: Timeframe

---

### BreakoutSignal
- direction
- signalCandle
- breakoutPrice
- range

---

## Trade Parameters

- stopLossPoints
- takeProfitPoints
- breakEvenTriggerPoints (optional)

---

## Strategy Rules

- Breakout candle must close strictly outside range
- First breakout only
- Entry at breakout candle close
- Outcome priority per candle:
  1. Stop Loss
  2. Take Profit
  3. Break Even
- End of data → UNRESOLVED

---

## Testing Requirements

- Multi-strategy StrategyRunner tests
- Range breakout correctness
- Deterministic fixtures

---

## Performance Requirements

- Streaming evaluation
- O(1) memory per strategy
- Multi-year MNQ data without OOM

---

## Acceptance Criteria

- Strategies are swappable
- Multiple strategies run on same candle stream
- Portfolio performance aggregated
- Range breakout behaves deterministically
